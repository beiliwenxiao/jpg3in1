package client

import (
	"context"
)

// Response 异步调用响应
type Response struct {
	Data  interface{}
	Error error
}

// ServiceHandler 服务处理器
type ServiceHandler interface{}

// FrameworkClient 框架客户端接口
type FrameworkClient interface {
	// Call 同步调用服务
	Call(ctx context.Context, service, method string, request interface{}, response interface{}) error

	// CallAsync 异步调用服务
	CallAsync(ctx context.Context, service, method string, request interface{}) (<-chan Response, error)

	// Stream 流式调用服务
	Stream(ctx context.Context, service, method string, request interface{}) (<-chan interface{}, error)

	// RegisterService 注册服务
	RegisterService(name string, handler ServiceHandler) error

	// Start 启动客户端
	Start() error

	// Shutdown 关闭客户端
	Shutdown(ctx context.Context) error
}

// Config 客户端配置
type Config struct {
	ServiceRegistry string
	// 其他配置项...
}
