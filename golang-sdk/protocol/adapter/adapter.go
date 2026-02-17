package adapter

import (
	"context"
	"fmt"
	"time"
)

// ProtocolType 协议类型
type ProtocolType string

const (
	// 外部协议
	ProtocolREST      ProtocolType = "REST"
	ProtocolWebSocket ProtocolType = "WebSocket"
	ProtocolJSONRPC   ProtocolType = "JSON-RPC"
	ProtocolMQTT      ProtocolType = "MQTT"

	// 内部协议
	ProtocolGRPC         ProtocolType = "gRPC"
	ProtocolInternalRPC  ProtocolType = "InternalRPC"
	ProtocolCustomBinary ProtocolType = "CustomBinary"
)

// ExternalRequest 外部协议请求
type ExternalRequest struct {
	Protocol  ProtocolType       // 协议类型
	Headers   map[string]string  // 请求头
	Body      interface{}        // 请求体
	Metadata  *RequestMetadata   // 元数据
	RawData   []byte             // 原始数据（可选）
}

// InternalRequest 内部协议请求
type InternalRequest struct {
	Service   string             // 服务名称
	Method    string             // 方法名称
	Payload   []byte             // 负载数据
	Headers   map[string]string  // 请求头
	TraceId   string             // 追踪 ID
	SpanId    string             // 跨度 ID
	Timeout   time.Duration      // 超时时间
	Metadata  map[string]string  // 元数据
}

// ExternalResponse 外部协议响应
type ExternalResponse struct {
	Protocol   ProtocolType       // 协议类型
	StatusCode int                // 状态码
	Headers    map[string]string  // 响应头
	Body       interface{}        // 响应体
	Error      *FrameworkError    // 错误信息
}

// InternalResponse 内部协议响应
type InternalResponse struct {
	Payload   []byte             // 负载数据
	Headers   map[string]string  // 响应头
	Error     *FrameworkError    // 错误信息
	Metadata  map[string]string  // 元数据
}

// RequestMetadata 请求元数据
type RequestMetadata struct {
	RequestId  string            // 请求 ID
	TraceId    string            // 追踪 ID
	SpanId     string            // 跨度 ID
	Timestamp  int64             // 时间戳
	ClientAddr string            // 客户端地址
	Extra      map[string]string // 额外信息
}

// FrameworkError 框架错误
type FrameworkError struct {
	Code       ErrorCode         // 错误码
	Message    string            // 错误消息
	Details    interface{}       // 详细信息
	Cause      error             // 原因错误
	StackTrace []string          // 堆栈追踪
	Timestamp  int64             // 发生时间
	ServiceId  string            // 发生服务
}

// ErrorCode 错误码
type ErrorCode int

const (
	// 客户端错误 (4xx)
	ErrorBadRequest    ErrorCode = 400
	ErrorUnauthorized  ErrorCode = 401
	ErrorForbidden     ErrorCode = 403
	ErrorNotFound      ErrorCode = 404
	ErrorTimeout       ErrorCode = 408

	// 服务端错误 (5xx)
	ErrorInternal         ErrorCode = 500
	ErrorNotImplemented   ErrorCode = 501
	ErrorServiceUnavailable ErrorCode = 503

	// 框架错误 (6xx)
	ErrorProtocol       ErrorCode = 600
	ErrorSerialization  ErrorCode = 601
	ErrorRouting        ErrorCode = 602
	ErrorConnection     ErrorCode = 603
)

// Error 实现 error 接口
func (e *FrameworkError) Error() string {
	if e.Cause != nil {
		return fmt.Sprintf("[%d] %s: %v", e.Code, e.Message, e.Cause)
	}
	return fmt.Sprintf("[%d] %s", e.Code, e.Message)
}

// ProtocolAdapter 协议适配器接口
type ProtocolAdapter interface {
	// TransformRequest 将外部协议请求转换为内部协议请求
	TransformRequest(ctx context.Context, external *ExternalRequest) (*InternalRequest, error)

	// TransformResponse 将内部协议响应转换为外部协议响应
	TransformResponse(ctx context.Context, internal *InternalResponse, originalProtocol ProtocolType) (*ExternalResponse, error)

	// GetSupportedProtocols 获取支持的协议类型
	GetSupportedProtocols() []ProtocolType
}
