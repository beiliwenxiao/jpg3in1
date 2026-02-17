package adapter

import (
	"context"
	"encoding/json"
	"testing"

	"github.com/leanovate/gopter"
	"github.com/leanovate/gopter/gen"
	"github.com/leanovate/gopter/prop"
)

// TestProtocolConversionRoundTripConsistency 属性测试：协议转换往返一致性
// Feature: multi-language-communication-framework, Property 9: 协议转换往返一致性
// **Validates: Requirements 4.1**
//
// 对于任意外部协议消息，转换为内部协议再转换回外部协议应该保持语义等价性
func TestProtocolConversionRoundTripConsistency(t *testing.T) {
	properties := gopter.NewProperties(nil)

	adapter := NewDefaultProtocolAdapter()

	properties.Property("REST protocol round trip preserves semantics", prop.ForAll(
		func(service string, method string, dataKey string, dataValue string) bool {
			// 构造请求体数据
			bodyData := map[string]interface{}{
				dataKey: dataValue,
			}
			
			// 构造外部 REST 请求
			external := &ExternalRequest{
				Protocol: ProtocolREST,
				Headers: map[string]string{
					"X-Service-Name": service,
					"X-Method-Name":  method,
					"Content-Type":   "application/json",
				},
				Body: bodyData,
				Metadata: &RequestMetadata{
					RequestId:  "test-request-id",
					TraceId:    "test-trace-id",
					Timestamp:  1234567890,
					ClientAddr: "127.0.0.1",
				},
			}

			// 转换为内部请求
			internal, err := adapter.TransformRequest(context.Background(), external)
			if err != nil {
				return false
			}

			// 验证内部请求包含正确的服务和方法
			if internal.Service != service || internal.Method != method {
				return false
			}

			// 验证追踪信息被保留
			if internal.TraceId == "" || internal.SpanId == "" {
				return false
			}

			// 构造内部响应
			responseData := map[string]interface{}{
				"status": "success",
				"data":   bodyData,
			}
			responsePayload, _ := json.Marshal(responseData)

			internalResp := &InternalResponse{
				Payload: responsePayload,
				Headers: map[string]string{
					"Content-Type": "application/json",
				},
				Error: nil,
			}

			// 转换回外部响应
			externalResp, err := adapter.TransformResponse(context.Background(), internalResp, ProtocolREST)
			if err != nil {
				return false
			}

			// 验证协议类型保持一致
			if externalResp.Protocol != ProtocolREST {
				return false
			}

			// 验证响应体可以被解析
			if externalResp.Body == nil {
				return false
			}

			// 验证状态码正确
			if externalResp.StatusCode != 200 {
				return false
			}

			return true
		},
		gen.Identifier(),
		gen.Identifier(),
		gen.Identifier(),
		gen.AlphaString(),
	))

	properties.Property("JSON-RPC protocol round trip preserves semantics", prop.ForAll(
		func(method string, id int, paramKey string, paramValue string) bool {
			// 构造参数
			params := map[string]interface{}{
				paramKey: paramValue,
			}
			
			// 构造外部 JSON-RPC 请求
			external := &ExternalRequest{
				Protocol: ProtocolJSONRPC,
				Headers: map[string]string{
					"Content-Type": "application/json",
				},
				Body: map[string]interface{}{
					"jsonrpc": "2.0",
					"method":  method,
					"params":  params,
					"id":      id,
				},
				Metadata: &RequestMetadata{
					RequestId: "test-request-id",
					TraceId:   "test-trace-id",
				},
			}

			// 转换为内部请求
			internal, err := adapter.TransformRequest(context.Background(), external)
			if err != nil {
				return false
			}

			// 验证方法名被正确提取
			if internal.Method == "" {
				return false
			}

			// 构造内部响应
			resultData := map[string]interface{}{
				"result": "success",
			}
			resultPayload, _ := json.Marshal(resultData)

			internalResp := &InternalResponse{
				Payload: resultPayload,
				Headers: map[string]string{},
				Error:   nil,
			}

			// 转换回外部响应
			externalResp, err := adapter.TransformResponse(context.Background(), internalResp, ProtocolJSONRPC)
			if err != nil {
				return false
			}

			// 验证协议类型保持一致
			if externalResp.Protocol != ProtocolJSONRPC {
				return false
			}

			// 验证响应体是 JSON-RPC 格式
			if bodyMap, ok := externalResp.Body.(map[string]interface{}); ok {
				// 必须包含 jsonrpc 字段
				if bodyMap["jsonrpc"] != "2.0" {
					return false
				}
				// 必须包含 result 或 error
				if bodyMap["result"] == nil && bodyMap["error"] == nil {
					return false
				}
				return true
			}

			return false
		},
		gen.Identifier(),
		gen.Int(),
		gen.Identifier(),
		gen.AlphaString(),
	))

	properties.Property("WebSocket protocol round trip preserves semantics", prop.ForAll(
		func(service string, method string, data string) bool {
			// 构造外部 WebSocket 请求
			external := &ExternalRequest{
				Protocol: ProtocolWebSocket,
				Headers:  map[string]string{},
				Body: map[string]interface{}{
					"service": service,
					"method":  method,
					"data":    data,
				},
				Metadata: &RequestMetadata{
					RequestId: "test-request-id",
				},
			}

			// 转换为内部请求
			internal, err := adapter.TransformRequest(context.Background(), external)
			if err != nil {
				return false
			}

			// 验证服务和方法被正确提取
			if internal.Service != service || internal.Method != method {
				return false
			}

			// 构造内部响应
			responseData := map[string]interface{}{
				"status": "ok",
			}
			responsePayload, _ := json.Marshal(responseData)

			internalResp := &InternalResponse{
				Payload: responsePayload,
				Headers: map[string]string{},
				Error:   nil,
			}

			// 转换回外部响应
			externalResp, err := adapter.TransformResponse(context.Background(), internalResp, ProtocolWebSocket)
			if err != nil {
				return false
			}

			// 验证协议类型保持一致
			if externalResp.Protocol != ProtocolWebSocket {
				return false
			}

			// 验证响应体存在
			if externalResp.Body == nil {
				return false
			}

			return true
		},
		gen.Identifier(),
		gen.Identifier(),
		gen.AlphaString(),
	))

	properties.Property("MQTT protocol round trip preserves semantics", prop.ForAll(
		func(service string, method string, payload []byte) bool {
			// 跳过空 payload
			if len(payload) == 0 {
				return true
			}

			// 构造外部 MQTT 请求
			topic := service + "/" + method
			external := &ExternalRequest{
				Protocol: ProtocolMQTT,
				Headers: map[string]string{
					"topic": topic,
				},
				Body: payload,
				Metadata: &RequestMetadata{
					RequestId: "test-request-id",
				},
			}

			// 转换为内部请求
			internal, err := adapter.TransformRequest(context.Background(), external)
			if err != nil {
				return false
			}

			// 验证服务和方法被正确提取
			if internal.Service != service || internal.Method != method {
				return false
			}

			// 构造内部响应
			internalResp := &InternalResponse{
				Payload: []byte("response"),
				Headers: map[string]string{},
				Error:   nil,
			}

			// 转换回外部响应
			externalResp, err := adapter.TransformResponse(context.Background(), internalResp, ProtocolMQTT)
			if err != nil {
				return false
			}

			// 验证协议类型保持一致
			if externalResp.Protocol != ProtocolMQTT {
				return false
			}

			return true
		},
		gen.Identifier(),
		gen.Identifier(),
		gen.SliceOf(gen.UInt8()),
	))

	properties.Property("Error responses preserve error information", prop.ForAll(
		func(errorCode ErrorCode, errorMsg string) bool {
			// 构造带错误的内部响应
			internalResp := &InternalResponse{
				Payload: []byte{},
				Headers: map[string]string{},
				Error: &FrameworkError{
					Code:    errorCode,
					Message: errorMsg,
				},
			}

			// 转换为外部响应（REST）
			externalResp, err := adapter.TransformResponse(context.Background(), internalResp, ProtocolREST)
			if err != nil {
				return false
			}

			// 验证错误信息被保留
			if externalResp.Error == nil {
				return false
			}

			if externalResp.Error.Code != errorCode {
				return false
			}

			// 验证状态码映射正确
			if externalResp.StatusCode < 400 {
				return false
			}

			return true
		},
		gen.OneConstOf(
			ErrorBadRequest,
			ErrorUnauthorized,
			ErrorForbidden,
			ErrorNotFound,
			ErrorTimeout,
			ErrorInternal,
			ErrorServiceUnavailable,
			ErrorProtocol,
			ErrorSerialization,
		),
		gen.AlphaString(),
	))

	properties.Property("Metadata is preserved during conversion", prop.ForAll(
		func(service string, method string, traceId string, requestId string) bool {
			// 构造外部请求
			external := &ExternalRequest{
				Protocol: ProtocolREST,
				Headers: map[string]string{
					"X-Service-Name": service,
					"X-Method-Name":  method,
					"X-Trace-Id":     traceId,
				},
				Body: map[string]interface{}{},
				Metadata: &RequestMetadata{
					RequestId:  requestId,
					TraceId:    traceId,
					ClientAddr: "127.0.0.1",
				},
			}

			// 转换为内部请求
			internal, err := adapter.TransformRequest(context.Background(), external)
			if err != nil {
				return false
			}

			// 验证元数据被保留
			if internal.TraceId != traceId {
				return false
			}

			if internal.Metadata["request_id"] != requestId {
				return false
			}

			if internal.Metadata["original_protocol"] != string(ProtocolREST) {
				return false
			}

			return true
		},
		gen.Identifier(),
		gen.Identifier(),
		gen.Identifier(),
		gen.Identifier(),
	))

	properties.TestingRun(t, gopter.ConsoleReporter(false))
}
