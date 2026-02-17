package adapter

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/gogf/gf/v2/util/guid"
)

// DefaultProtocolAdapter 默认协议适配器实现
type DefaultProtocolAdapter struct {
	defaultTimeout time.Duration
}

// NewDefaultProtocolAdapter 创建默认协议适配器
func NewDefaultProtocolAdapter() *DefaultProtocolAdapter {
	return &DefaultProtocolAdapter{
		defaultTimeout: 30 * time.Second,
	}
}

// TransformRequest 将外部协议请求转换为内部协议请求
func (a *DefaultProtocolAdapter) TransformRequest(ctx context.Context, external *ExternalRequest) (*InternalRequest, error) {
	if external == nil {
		return nil, &FrameworkError{
			Code:    ErrorBadRequest,
			Message: "external request is nil",
		}
	}

	// 生成追踪 ID
	traceId := a.getOrGenerateTraceId(external)
	spanId := guid.S()

	// 提取服务名称和方法名称
	service, method, err := a.extractServiceAndMethod(external)
	if err != nil {
		return nil, err
	}

	// 序列化请求体
	payload, err := a.serializePayload(external.Body)
	if err != nil {
		return nil, &FrameworkError{
			Code:    ErrorSerialization,
			Message: "failed to serialize request body",
			Cause:   err,
		}
	}

	// 构造内部请求
	internal := &InternalRequest{
		Service:  service,
		Method:   method,
		Payload:  payload,
		Headers:  a.copyHeaders(external.Headers),
		TraceId:  traceId,
		SpanId:   spanId,
		Timeout:  a.defaultTimeout,
		Metadata: make(map[string]string),
	}

	// 添加协议类型到元数据
	internal.Metadata["original_protocol"] = string(external.Protocol)

	// 复制元数据
	if external.Metadata != nil {
		internal.Metadata["request_id"] = external.Metadata.RequestId
		internal.Metadata["client_addr"] = external.Metadata.ClientAddr
		for k, v := range external.Metadata.Extra {
			internal.Metadata[k] = v
		}
	}

	return internal, nil
}

// TransformResponse 将内部协议响应转换为外部协议响应
func (a *DefaultProtocolAdapter) TransformResponse(ctx context.Context, internal *InternalResponse, originalProtocol ProtocolType) (*ExternalResponse, error) {
	if internal == nil {
		return nil, &FrameworkError{
			Code:    ErrorInternal,
			Message: "internal response is nil",
		}
	}

	// 反序列化响应体
	var body interface{}
	if len(internal.Payload) > 0 {
		if err := json.Unmarshal(internal.Payload, &body); err != nil {
			// 如果无法解析为 JSON，返回原始字节
			body = internal.Payload
		}
	}

	// 确定状态码
	statusCode := 200
	if internal.Error != nil {
		statusCode = a.mapErrorCodeToHttpStatus(internal.Error.Code)
	}

	// 构造外部响应
	external := &ExternalResponse{
		Protocol:   originalProtocol,
		StatusCode: statusCode,
		Headers:    a.copyHeaders(internal.Headers),
		Body:       body,
		Error:      internal.Error,
	}

	// 根据协议类型调整响应格式
	switch originalProtocol {
	case ProtocolJSONRPC:
		external.Body = a.formatJsonRpcResponse(body, internal.Error)
	case ProtocolREST:
		if internal.Error != nil {
			external.Body = map[string]interface{}{
				"error":   internal.Error.Message,
				"code":    internal.Error.Code,
				"details": internal.Error.Details,
			}
		}
	}

	return external, nil
}

// GetSupportedProtocols 获取支持的协议类型
func (a *DefaultProtocolAdapter) GetSupportedProtocols() []ProtocolType {
	return []ProtocolType{
		ProtocolREST,
		ProtocolWebSocket,
		ProtocolJSONRPC,
		ProtocolMQTT,
		ProtocolGRPC,
		ProtocolInternalRPC,
		ProtocolCustomBinary,
	}
}

// extractServiceAndMethod 从外部请求中提取服务名称和方法名称
func (a *DefaultProtocolAdapter) extractServiceAndMethod(external *ExternalRequest) (string, string, error) {
	switch external.Protocol {
	case ProtocolREST:
		return a.extractFromREST(external)
	case ProtocolJSONRPC:
		return a.extractFromJSONRPC(external)
	case ProtocolWebSocket:
		return a.extractFromWebSocket(external)
	case ProtocolMQTT:
		return a.extractFromMQTT(external)
	default:
		return "", "", &FrameworkError{
			Code:    ErrorProtocol,
			Message: fmt.Sprintf("unsupported protocol: %s", external.Protocol),
		}
	}
}

// extractFromREST 从 REST 请求中提取服务和方法
func (a *DefaultProtocolAdapter) extractFromREST(external *ExternalRequest) (string, string, error) {
	// 从请求头或元数据中提取
	service := external.Headers["X-Service-Name"]
	method := external.Headers["X-Method-Name"]

	if service == "" || method == "" {
		// 尝试从 body 中提取
		if bodyMap, ok := external.Body.(map[string]interface{}); ok {
			if s, ok := bodyMap["service"].(string); ok {
				service = s
			}
			if m, ok := bodyMap["method"].(string); ok {
				method = m
			}
		}
	}

	if service == "" || method == "" {
		return "", "", &FrameworkError{
			Code:    ErrorBadRequest,
			Message: "service or method not specified in REST request",
		}
	}

	return service, method, nil
}

// extractFromJSONRPC 从 JSON-RPC 请求中提取服务和方法
func (a *DefaultProtocolAdapter) extractFromJSONRPC(external *ExternalRequest) (string, string, error) {
	bodyMap, ok := external.Body.(map[string]interface{})
	if !ok {
		return "", "", &FrameworkError{
			Code:    ErrorBadRequest,
			Message: "invalid JSON-RPC request body",
		}
	}

	method, ok := bodyMap["method"].(string)
	if !ok || method == "" {
		return "", "", &FrameworkError{
			Code:    ErrorBadRequest,
			Message: "method not specified in JSON-RPC request",
		}
	}

	// JSON-RPC 方法格式: "ServiceName.MethodName"
	// 如果没有点号，使用默认服务名
	service := "default"
	if len(method) > 0 {
		// 简单解析，可以根据需要扩展
		for i, c := range method {
			if c == '.' {
				service = method[:i]
				method = method[i+1:]
				break
			}
		}
	}

	return service, method, nil
}

// extractFromWebSocket 从 WebSocket 请求中提取服务和方法
func (a *DefaultProtocolAdapter) extractFromWebSocket(external *ExternalRequest) (string, string, error) {
	// WebSocket 消息格式类似 JSON-RPC
	bodyMap, ok := external.Body.(map[string]interface{})
	if !ok {
		return "", "", &FrameworkError{
			Code:    ErrorBadRequest,
			Message: "invalid WebSocket message body",
		}
	}

	service, _ := bodyMap["service"].(string)
	method, _ := bodyMap["method"].(string)

	if service == "" || method == "" {
		return "", "", &FrameworkError{
			Code:    ErrorBadRequest,
			Message: "service or method not specified in WebSocket message",
		}
	}

	return service, method, nil
}

// extractFromMQTT 从 MQTT 请求中提取服务和方法
func (a *DefaultProtocolAdapter) extractFromMQTT(external *ExternalRequest) (string, string, error) {
	// MQTT topic 格式: "service/method"
	topic := external.Headers["topic"]
	if topic == "" {
		return "", "", &FrameworkError{
			Code:    ErrorBadRequest,
			Message: "topic not specified in MQTT message",
		}
	}

	// 解析 topic
	service := "default"
	method := topic
	for i, c := range topic {
		if c == '/' {
			service = topic[:i]
			method = topic[i+1:]
			break
		}
	}

	return service, method, nil
}

// serializePayload 序列化负载
func (a *DefaultProtocolAdapter) serializePayload(body interface{}) ([]byte, error) {
	if body == nil {
		return []byte{}, nil
	}

	// 如果已经是字节数组，直接返回
	if bytes, ok := body.([]byte); ok {
		return bytes, nil
	}

	// 序列化为 JSON
	return json.Marshal(body)
}

// copyHeaders 复制请求头
func (a *DefaultProtocolAdapter) copyHeaders(headers map[string]string) map[string]string {
	if headers == nil {
		return make(map[string]string)
	}

	copied := make(map[string]string, len(headers))
	for k, v := range headers {
		copied[k] = v
	}
	return copied
}

// getOrGenerateTraceId 获取或生成追踪 ID
func (a *DefaultProtocolAdapter) getOrGenerateTraceId(external *ExternalRequest) string {
	// 尝试从请求头获取
	if traceId := external.Headers["X-Trace-Id"]; traceId != "" {
		return traceId
	}

	// 尝试从元数据获取
	if external.Metadata != nil && external.Metadata.TraceId != "" {
		return external.Metadata.TraceId
	}

	// 生成新的追踪 ID
	return guid.S()
}

// mapErrorCodeToHttpStatus 将错误码映射到 HTTP 状态码
func (a *DefaultProtocolAdapter) mapErrorCodeToHttpStatus(code ErrorCode) int {
	switch code {
	case ErrorBadRequest:
		return 400
	case ErrorUnauthorized:
		return 401
	case ErrorForbidden:
		return 403
	case ErrorNotFound:
		return 404
	case ErrorTimeout:
		return 408
	case ErrorInternal:
		return 500
	case ErrorNotImplemented:
		return 501
	case ErrorServiceUnavailable:
		return 503
	case ErrorProtocol, ErrorSerialization, ErrorRouting, ErrorConnection:
		return 500
	default:
		return 500
	}
}

// formatJsonRpcResponse 格式化 JSON-RPC 响应
func (a *DefaultProtocolAdapter) formatJsonRpcResponse(body interface{}, err *FrameworkError) interface{} {
	response := map[string]interface{}{
		"jsonrpc": "2.0",
	}

	if err != nil {
		response["error"] = map[string]interface{}{
			"code":    err.Code,
			"message": err.Message,
			"data":    err.Details,
		}
	} else {
		response["result"] = body
	}

	return response
}
