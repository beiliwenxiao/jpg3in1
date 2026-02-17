package errors

import (
	"fmt"
	"time"
)

// FrameworkError 框架统一错误类型
type FrameworkError struct {
	Code       ErrorCode
	Message    string
	Details    string
	ServiceID  string
	Timestamp  int64
	ErrorChain []string
	Cause      error
}

// NewFrameworkError 创建新的框架错误
func NewFrameworkError(code ErrorCode, message string) *FrameworkError {
	return &FrameworkError{
		Code:       code,
		Message:    message,
		Timestamp:  time.Now().UnixMilli(),
		ErrorChain: []string{formatChainEntry(code, message, "")},
	}
}

// NewFrameworkErrorWithDetails 创建带详情的框架错误
func NewFrameworkErrorWithDetails(code ErrorCode, message, details string) *FrameworkError {
	return &FrameworkError{
		Code:       code,
		Message:    message,
		Details:    details,
		Timestamp:  time.Now().UnixMilli(),
		ErrorChain: []string{formatChainEntry(code, message, "")},
	}
}

// NewFrameworkErrorWithCause 创建带原因的框架错误
func NewFrameworkErrorWithCause(code ErrorCode, message string, cause error) *FrameworkError {
	return &FrameworkError{
		Code:       code,
		Message:    message,
		Timestamp:  time.Now().UnixMilli(),
		ErrorChain: buildErrorChain(code, message, "", cause),
		Cause:      cause,
	}
}

// NewFrameworkErrorFull 创建完整的框架错误
func NewFrameworkErrorFull(code ErrorCode, message, details, serviceID string, cause error) *FrameworkError {
	return &FrameworkError{
		Code:       code,
		Message:    message,
		Details:    details,
		ServiceID:  serviceID,
		Timestamp:  time.Now().UnixMilli(),
		ErrorChain: buildErrorChain(code, message, serviceID, cause),
		Cause:      cause,
	}
}

// Error 实现 error 接口
func (e *FrameworkError) Error() string {
	if e.Details != "" {
		return fmt.Sprintf("[%d %s] %s: %s", e.Code.Code(), e.Code.String(), e.Message, e.Details)
	}
	return fmt.Sprintf("[%d %s] %s", e.Code.Code(), e.Code.String(), e.Message)
}

// Unwrap 实现 errors.Unwrap 接口
func (e *FrameworkError) Unwrap() error {
	return e.Cause
}

// WithServiceID 添加服务 ID 上下文
func (e *FrameworkError) WithServiceID(serviceID string) *FrameworkError {
	return &FrameworkError{
		Code:       e.Code,
		Message:    e.Message,
		Details:    e.Details,
		ServiceID:  serviceID,
		Timestamp:  e.Timestamp,
		ErrorChain: e.ErrorChain,
		Cause:      e.Cause,
	}
}

// ToErrorResponse 转换为标准化的错误响应
func (e *FrameworkError) ToErrorResponse() map[string]interface{} {
	response := map[string]interface{}{
		"code":      e.Code.Code(),
		"error":     e.Code.String(),
		"message":   e.Message,
		"timestamp": e.Timestamp,
	}

	if e.Details != "" {
		response["details"] = e.Details
	}
	if e.ServiceID != "" {
		response["serviceId"] = e.ServiceID
	}
	if len(e.ErrorChain) > 0 {
		response["errorChain"] = e.ErrorChain
	}

	return response
}

// NewFrameworkErrorFromHTTPStatus 从 HTTP 状态码创建 FrameworkError
func NewFrameworkErrorFromHTTPStatus(httpStatus int, message string) *FrameworkError {
	return NewFrameworkError(FromHTTPStatus(httpStatus), message)
}

// NewFrameworkErrorFromGRPCStatus 从 gRPC 状态码创建 FrameworkError
func NewFrameworkErrorFromGRPCStatus(grpcStatus int, message string) *FrameworkError {
	return NewFrameworkError(FromGRPCStatus(grpcStatus), message)
}

// NewFrameworkErrorFromJSONRPCCode 从 JSON-RPC 错误码创建 FrameworkError
func NewFrameworkErrorFromJSONRPCCode(jsonRpcCode int, message string) *FrameworkError {
	return NewFrameworkError(FromJSONRPCCode(jsonRpcCode), message)
}

// buildErrorChain 构建错误链
func buildErrorChain(code ErrorCode, message, serviceID string, cause error) []string {
	chain := []string{formatChainEntry(code, message, serviceID)}

	current := cause
	depth := 0
	for current != nil && depth < 10 {
		if fe, ok := current.(*FrameworkError); ok {
			chain = append(chain, formatChainEntry(fe.Code, fe.Message, fe.ServiceID))
			current = fe.Cause
		} else {
			chain = append(chain, fmt.Sprintf("%T: %s", current, current.Error()))
			break
		}
		depth++
	}

	return chain
}

// formatChainEntry 格式化错误链条目
func formatChainEntry(code ErrorCode, message, serviceID string) string {
	entry := fmt.Sprintf("[%d %s] %s", code.Code(), code.String(), message)
	if serviceID != "" {
		entry += fmt.Sprintf(" (service: %s)", serviceID)
	}
	return entry
}
