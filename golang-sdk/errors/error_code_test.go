package errors

import (
	"testing"
)

func TestErrorCode_String(t *testing.T) {
	tests := []struct {
		code     ErrorCode
		expected string
	}{
		{BadRequest, "Bad Request"},
		{Unauthorized, "Unauthorized"},
		{Forbidden, "Forbidden"},
		{NotFound, "Not Found"},
		{Timeout, "Timeout"},
		{InternalError, "Internal Error"},
		{NotImplemented, "Not Implemented"},
		{ServiceUnavailable, "Service Unavailable"},
		{ProtocolError, "Protocol Error"},
		{SerializationError, "Serialization Error"},
		{RoutingError, "Routing Error"},
		{ConnectionError, "Connection Error"},
	}

	for _, tt := range tests {
		t.Run(tt.expected, func(t *testing.T) {
			if got := tt.code.String(); got != tt.expected {
				t.Errorf("ErrorCode.String() = %v, want %v", got, tt.expected)
			}
		})
	}
}

func TestErrorCode_IsClientError(t *testing.T) {
	tests := []struct {
		code     ErrorCode
		expected bool
	}{
		{BadRequest, true},
		{Unauthorized, true},
		{Forbidden, true},
		{NotFound, true},
		{Timeout, true},
		{InternalError, false},
		{ServiceUnavailable, false},
		{ProtocolError, false},
	}

	for _, tt := range tests {
		t.Run(tt.code.String(), func(t *testing.T) {
			if got := tt.code.IsClientError(); got != tt.expected {
				t.Errorf("ErrorCode.IsClientError() = %v, want %v", got, tt.expected)
			}
		})
	}
}

func TestErrorCode_IsServerError(t *testing.T) {
	tests := []struct {
		code     ErrorCode
		expected bool
	}{
		{BadRequest, false},
		{InternalError, true},
		{NotImplemented, true},
		{ServiceUnavailable, true},
		{ProtocolError, false},
	}

	for _, tt := range tests {
		t.Run(tt.code.String(), func(t *testing.T) {
			if got := tt.code.IsServerError(); got != tt.expected {
				t.Errorf("ErrorCode.IsServerError() = %v, want %v", got, tt.expected)
			}
		})
	}
}

func TestErrorCode_IsFrameworkError(t *testing.T) {
	tests := []struct {
		code     ErrorCode
		expected bool
	}{
		{BadRequest, false},
		{InternalError, false},
		{ProtocolError, true},
		{SerializationError, true},
		{RoutingError, true},
		{ConnectionError, true},
	}

	for _, tt := range tests {
		t.Run(tt.code.String(), func(t *testing.T) {
			if got := tt.code.IsFrameworkError(); got != tt.expected {
				t.Errorf("ErrorCode.IsFrameworkError() = %v, want %v", got, tt.expected)
			}
		})
	}
}

func TestErrorCode_IsRetryable(t *testing.T) {
	tests := []struct {
		code     ErrorCode
		expected bool
	}{
		{Timeout, true},
		{ServiceUnavailable, true},
		{ConnectionError, true},
		{BadRequest, false},
		{InternalError, false},
		{NotFound, false},
	}

	for _, tt := range tests {
		t.Run(tt.code.String(), func(t *testing.T) {
			if got := tt.code.IsRetryable(); got != tt.expected {
				t.Errorf("ErrorCode.IsRetryable() = %v, want %v", got, tt.expected)
			}
		})
	}
}

func TestFromHTTPStatus(t *testing.T) {
	tests := []struct {
		httpStatus int
		expected   ErrorCode
	}{
		{400, BadRequest},
		{401, Unauthorized},
		{403, Forbidden},
		{404, NotFound},
		{408, Timeout},
		{500, InternalError},
		{501, NotImplemented},
		{503, ServiceUnavailable},
		{450, BadRequest}, // 未映射的 4xx
		{550, InternalError}, // 未映射的 5xx
	}

	for _, tt := range tests {
		t.Run(tt.expected.String(), func(t *testing.T) {
			if got := FromHTTPStatus(tt.httpStatus); got != tt.expected {
				t.Errorf("FromHTTPStatus(%d) = %v, want %v", tt.httpStatus, got, tt.expected)
			}
		})
	}
}

func TestFromGRPCStatus(t *testing.T) {
	tests := []struct {
		grpcStatus int
		expected   ErrorCode
	}{
		{3, BadRequest},
		{4, Timeout},
		{5, NotFound},
		{7, Forbidden},
		{12, NotImplemented},
		{13, InternalError},
		{14, ServiceUnavailable},
		{16, Unauthorized},
	}

	for _, tt := range tests {
		t.Run(tt.expected.String(), func(t *testing.T) {
			if got := FromGRPCStatus(tt.grpcStatus); got != tt.expected {
				t.Errorf("FromGRPCStatus(%d) = %v, want %v", tt.grpcStatus, got, tt.expected)
			}
		})
	}
}

func TestFromJSONRPCCode(t *testing.T) {
	tests := []struct {
		jsonRpcCode int
		expected    ErrorCode
	}{
		{-32700, BadRequest},
		{-32600, BadRequest},
		{-32601, NotFound},
		{-32602, BadRequest},
		{-32603, InternalError},
	}

	for _, tt := range tests {
		t.Run(tt.expected.String(), func(t *testing.T) {
			if got := FromJSONRPCCode(tt.jsonRpcCode); got != tt.expected {
				t.Errorf("FromJSONRPCCode(%d) = %v, want %v", tt.jsonRpcCode, got, tt.expected)
			}
		})
	}
}

func TestErrorCode_ToHTTPStatus(t *testing.T) {
	tests := []struct {
		code     ErrorCode
		expected int
	}{
		{BadRequest, 400},
		{Unauthorized, 401},
		{Forbidden, 403},
		{NotFound, 404},
		{Timeout, 408},
		{InternalError, 500},
		{NotImplemented, 501},
		{ServiceUnavailable, 503},
		{ProtocolError, 502},
		{SerializationError, 400},
		{RoutingError, 502},
		{ConnectionError, 503},
	}

	for _, tt := range tests {
		t.Run(tt.code.String(), func(t *testing.T) {
			if got := tt.code.ToHTTPStatus(); got != tt.expected {
				t.Errorf("ErrorCode.ToHTTPStatus() = %v, want %v", got, tt.expected)
			}
		})
	}
}

func TestErrorCode_ToJSONRPCCode(t *testing.T) {
	tests := []struct {
		code     ErrorCode
		expected int
	}{
		{BadRequest, -32600},
		{NotFound, -32601},
		{InternalError, -32603},
		{Timeout, -32603},
		{ServiceUnavailable, -32603},
		{SerializationError, -32700},
	}

	for _, tt := range tests {
		t.Run(tt.code.String(), func(t *testing.T) {
			if got := tt.code.ToJSONRPCCode(); got != tt.expected {
				t.Errorf("ErrorCode.ToJSONRPCCode() = %v, want %v", got, tt.expected)
			}
		})
	}
}
