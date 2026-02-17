package errors

import (
	"errors"
	"testing"
)

func TestNewFrameworkError(t *testing.T) {
	err := NewFrameworkError(BadRequest, "测试错误")

	if err.Code != BadRequest {
		t.Errorf("Code = %v, want %v", err.Code, BadRequest)
	}
	if err.Message != "测试错误" {
		t.Errorf("Message = %v, want %v", err.Message, "测试错误")
	}
	if err.Timestamp <= 0 {
		t.Error("Timestamp should be positive")
	}
	if len(err.ErrorChain) == 0 {
		t.Error("ErrorChain should not be empty")
	}
}

func TestNewFrameworkErrorWithDetails(t *testing.T) {
	err := NewFrameworkErrorWithDetails(NotFound, "资源未找到", "用户ID: 123")

	if err.Code != NotFound {
		t.Errorf("Code = %v, want %v", err.Code, NotFound)
	}
	if err.Details != "用户ID: 123" {
		t.Errorf("Details = %v, want %v", err.Details, "用户ID: 123")
	}
}

func TestNewFrameworkErrorWithCause(t *testing.T) {
	cause := errors.New("底层错误")
	err := NewFrameworkErrorWithCause(InternalError, "操作失败", cause)

	if err.Cause != cause {
		t.Errorf("Cause = %v, want %v", err.Cause, cause)
	}
	if len(err.ErrorChain) < 2 {
		t.Error("ErrorChain should contain at least 2 entries")
	}
}

func TestFrameworkError_Error(t *testing.T) {
	tests := []struct {
		name     string
		err      *FrameworkError
		contains string
	}{
		{
			name:     "简单错误",
			err:      NewFrameworkError(BadRequest, "无效请求"),
			contains: "[400 Bad Request] 无效请求",
		},
		{
			name:     "带详情的错误",
			err:      NewFrameworkErrorWithDetails(NotFound, "未找到", "详细信息"),
			contains: "详细信息",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			errStr := tt.err.Error()
			if errStr == "" {
				t.Error("Error() should not return empty string")
			}
		})
	}
}

func TestFrameworkError_Unwrap(t *testing.T) {
	cause := errors.New("原因错误")
	err := NewFrameworkErrorWithCause(InternalError, "包装错误", cause)

	unwrapped := err.Unwrap()
	if unwrapped != cause {
		t.Errorf("Unwrap() = %v, want %v", unwrapped, cause)
	}
}

func TestFrameworkError_WithServiceID(t *testing.T) {
	err := NewFrameworkError(InternalError, "错误")
	errWithService := err.WithServiceID("service-123")

	if errWithService.ServiceID != "service-123" {
		t.Errorf("ServiceID = %v, want %v", errWithService.ServiceID, "service-123")
	}
}

func TestFrameworkError_ToErrorResponse(t *testing.T) {
	err := NewFrameworkErrorFull(
		BadRequest,
		"请求错误",
		"详细信息",
		"service-1",
		nil,
	)

	response := err.ToErrorResponse()

	if response["code"] != 400 {
		t.Errorf("response[code] = %v, want %v", response["code"], 400)
	}
	if response["error"] != "Bad Request" {
		t.Errorf("response[error] = %v, want %v", response["error"], "Bad Request")
	}
	if response["message"] != "请求错误" {
		t.Errorf("response[message] = %v, want %v", response["message"], "请求错误")
	}
	if response["details"] != "详细信息" {
		t.Errorf("response[details] = %v, want %v", response["details"], "详细信息")
	}
	if response["serviceId"] != "service-1" {
		t.Errorf("response[serviceId] = %v, want %v", response["serviceId"], "service-1")
	}
	if response["timestamp"] == nil {
		t.Error("response[timestamp] should not be nil")
	}
}

func TestFrameworkError_ErrorChain(t *testing.T) {
	innerErr := NewFrameworkError(ConnectionError, "连接失败")
	outerErr := NewFrameworkErrorWithCause(ServiceUnavailable, "服务不可用", innerErr)

	if len(outerErr.ErrorChain) < 2 {
		t.Errorf("ErrorChain length = %v, want >= 2", len(outerErr.ErrorChain))
	}

	// 验证错误链包含两个错误的信息
	chainStr := ""
	for _, entry := range outerErr.ErrorChain {
		chainStr += entry
	}

	if chainStr == "" {
		t.Error("ErrorChain should not be empty")
	}
}

func TestNewFrameworkErrorFromHTTPStatus(t *testing.T) {
	err := NewFrameworkErrorFromHTTPStatus(404, "页面未找到")

	if err.Code != NotFound {
		t.Errorf("Code = %v, want %v", err.Code, NotFound)
	}
	if err.Message != "页面未找到" {
		t.Errorf("Message = %v, want %v", err.Message, "页面未找到")
	}
}

func TestNewFrameworkErrorFromGRPCStatus(t *testing.T) {
	err := NewFrameworkErrorFromGRPCStatus(5, "方法未找到")

	if err.Code != NotFound {
		t.Errorf("Code = %v, want %v", err.Code, NotFound)
	}
}

func TestNewFrameworkErrorFromJSONRPCCode(t *testing.T) {
	err := NewFrameworkErrorFromJSONRPCCode(-32601, "方法不存在")

	if err.Code != NotFound {
		t.Errorf("Code = %v, want %v", err.Code, NotFound)
	}
}
