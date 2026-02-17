package errors

// ErrorCode 统一错误码类型
type ErrorCode int

const (
	// 客户端错误 (4xx)
	BadRequest ErrorCode = 400
	Unauthorized ErrorCode = 401
	Forbidden ErrorCode = 403
	NotFound ErrorCode = 404
	Timeout ErrorCode = 408

	// 服务端错误 (5xx)
	InternalError ErrorCode = 500
	NotImplemented ErrorCode = 501
	ServiceUnavailable ErrorCode = 503

	// 框架错误 (6xx)
	ProtocolError ErrorCode = 600
	SerializationError ErrorCode = 601
	RoutingError ErrorCode = 602
	ConnectionError ErrorCode = 603
)

// String 返回错误码的字符串表示
func (e ErrorCode) String() string {
	switch e {
	case BadRequest:
		return "Bad Request"
	case Unauthorized:
		return "Unauthorized"
	case Forbidden:
		return "Forbidden"
	case NotFound:
		return "Not Found"
	case Timeout:
		return "Timeout"
	case InternalError:
		return "Internal Error"
	case NotImplemented:
		return "Not Implemented"
	case ServiceUnavailable:
		return "Service Unavailable"
	case ProtocolError:
		return "Protocol Error"
	case SerializationError:
		return "Serialization Error"
	case RoutingError:
		return "Routing Error"
	case ConnectionError:
		return "Connection Error"
	default:
		return "Unknown Error"
	}
}

// Code 返回错误码的整数值
func (e ErrorCode) Code() int {
	return int(e)
}

// IsClientError 判断是否为客户端错误
func (e ErrorCode) IsClientError() bool {
	return e >= 400 && e < 500
}

// IsServerError 判断是否为服务端错误
func (e ErrorCode) IsServerError() bool {
	return e >= 500 && e < 600
}

// IsFrameworkError 判断是否为框架错误
func (e ErrorCode) IsFrameworkError() bool {
	return e >= 600
}

// IsRetryable 判断是否为可重试的错误
func (e ErrorCode) IsRetryable() bool {
	return e == Timeout || e == ServiceUnavailable || e == ConnectionError
}

// FromCode 根据错误码整数值获取 ErrorCode
func FromCode(code int) ErrorCode {
	switch code {
	case 400:
		return BadRequest
	case 401:
		return Unauthorized
	case 403:
		return Forbidden
	case 404:
		return NotFound
	case 408:
		return Timeout
	case 500:
		return InternalError
	case 501:
		return NotImplemented
	case 503:
		return ServiceUnavailable
	case 600:
		return ProtocolError
	case 601:
		return SerializationError
	case 602:
		return RoutingError
	case 603:
		return ConnectionError
	default:
		return InternalError
	}
}

// FromHTTPStatus 从 HTTP 状态码映射到 ErrorCode
func FromHTTPStatus(httpStatus int) ErrorCode {
	switch httpStatus {
	case 400:
		return BadRequest
	case 401:
		return Unauthorized
	case 403:
		return Forbidden
	case 404:
		return NotFound
	case 408:
		return Timeout
	case 500:
		return InternalError
	case 501:
		return NotImplemented
	case 503:
		return ServiceUnavailable
	default:
		if httpStatus >= 400 && httpStatus < 500 {
			return BadRequest
		}
		if httpStatus >= 500 {
			return InternalError
		}
		return InternalError
	}
}

// FromGRPCStatus 从 gRPC 状态码映射到 ErrorCode
func FromGRPCStatus(grpcStatus int) ErrorCode {
	switch grpcStatus {
	case 0: // OK
		return 0
	case 1: // CANCELLED
		return InternalError
	case 2: // UNKNOWN
		return InternalError
	case 3: // INVALID_ARGUMENT
		return BadRequest
	case 4: // DEADLINE_EXCEEDED
		return Timeout
	case 5: // NOT_FOUND
		return NotFound
	case 7: // PERMISSION_DENIED
		return Forbidden
	case 12: // UNIMPLEMENTED
		return NotImplemented
	case 13: // INTERNAL
		return InternalError
	case 14: // UNAVAILABLE
		return ServiceUnavailable
	case 16: // UNAUTHENTICATED
		return Unauthorized
	default:
		return InternalError
	}
}

// FromJSONRPCCode 从 JSON-RPC 错误码映射到 ErrorCode
func FromJSONRPCCode(jsonRpcCode int) ErrorCode {
	switch jsonRpcCode {
	case -32700: // Parse error
		return BadRequest
	case -32600: // Invalid Request
		return BadRequest
	case -32601: // Method not found
		return NotFound
	case -32602: // Invalid params
		return BadRequest
	case -32603: // Internal error
		return InternalError
	default:
		return InternalError
	}
}

// ToHTTPStatus 将 ErrorCode 映射到 HTTP 状态码
func (e ErrorCode) ToHTTPStatus() int {
	switch e {
	case BadRequest:
		return 400
	case Unauthorized:
		return 401
	case Forbidden:
		return 403
	case NotFound:
		return 404
	case Timeout:
		return 408
	case InternalError:
		return 500
	case NotImplemented:
		return 501
	case ServiceUnavailable:
		return 503
	case ProtocolError:
		return 502
	case SerializationError:
		return 400
	case RoutingError:
		return 502
	case ConnectionError:
		return 503
	default:
		return 500
	}
}

// ToJSONRPCCode 将 ErrorCode 映射到 JSON-RPC 错误码
func (e ErrorCode) ToJSONRPCCode() int {
	switch e {
	case BadRequest:
		return -32600
	case NotFound:
		return -32601
	case InternalError, Timeout, ServiceUnavailable:
		return -32603
	case SerializationError:
		return -32700
	default:
		return -32603
	}
}
