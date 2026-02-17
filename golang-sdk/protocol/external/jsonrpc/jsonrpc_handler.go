package jsonrpc

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"

	"github.com/gogf/gf/v2/frame/g"
	"github.com/gogf/gf/v2/net/ghttp"
)

// JsonRpcProtocolHandler JSON-RPC 2.0 协议处理器
type JsonRpcProtocolHandler struct {
	server *ghttp.Server
	config *JsonRpcConfig
}

// JsonRpcConfig JSON-RPC 配置
type JsonRpcConfig struct {
	Host string
	Port int
	Path string
}

// NewJsonRpcProtocolHandler 创建 JSON-RPC 协议处理器
func NewJsonRpcProtocolHandler(config *JsonRpcConfig) *JsonRpcProtocolHandler {
	// 为每个handler创建独立的命名服务器实例
	serverName := fmt.Sprintf("jsonrpc-%s-%d", config.Host, config.Port)
	server := g.Server(serverName)
	return &JsonRpcProtocolHandler{
		server: server,
		config: config,
	}
}

// Start 启动 JSON-RPC 服务器
func (h *JsonRpcProtocolHandler) Start() error {
	// 配置服务器
	h.server.SetAddr(fmt.Sprintf("%s:%d", h.config.Host, h.config.Port))
	
	// 注册 JSON-RPC 路由
	h.server.BindHandler(h.config.Path, h.handleJsonRpc)
	
	// 启动服务器
	go h.server.Run()
	
	return nil
}

// Stop 停止 JSON-RPC 服务器
func (h *JsonRpcProtocolHandler) Stop(ctx context.Context) error {
	return h.server.Shutdown()
}

// handleJsonRpc 处理 JSON-RPC 请求
func (h *JsonRpcProtocolHandler) handleJsonRpc(r *ghttp.Request) {
	// 只接受 POST 请求
	if r.Method != http.MethodPost {
		h.sendError(r, nil, -32600, "Invalid Request", "Only POST method is allowed")
		return
	}
	
	// 解析请求
	body := r.GetBody()
	var request JsonRpcRequest
	if err := json.Unmarshal(body, &request); err != nil {
		h.sendError(r, nil, -32700, "Parse error", err.Error())
		return
	}
	
	// 验证 JSON-RPC 版本
	if request.Jsonrpc != "2.0" {
		h.sendError(r, request.Id, -32600, "Invalid Request", "jsonrpc must be 2.0")
		return
	}
	
	// 验证方法名
	if request.Method == "" {
		h.sendError(r, request.Id, -32600, "Invalid Request", "method is required")
		return
	}
	
	// 处理请求
	result := h.handleMethod(request.Method, request.Params)
	
	// 发送响应
	h.sendResponse(r, request.Id, result)
}

// handleMethod 处理 JSON-RPC 方法调用
func (h *JsonRpcProtocolHandler) handleMethod(method string, params interface{}) interface{} {
	// TODO: 调用协议适配器转换请求
	// TODO: 调用消息路由器路由到目标服务
	// TODO: 获取响应并返回
	
	// 临时响应
	return map[string]interface{}{
		"message": "JSON-RPC handler is working",
		"method":  method,
		"params":  params,
	}
}

// sendResponse 发送 JSON-RPC 响应
func (h *JsonRpcProtocolHandler) sendResponse(r *ghttp.Request, id interface{}, result interface{}) {
	response := JsonRpcResponse{
		Jsonrpc: "2.0",
		Id:      id,
		Result:  result,
	}
	
	r.Response.Header().Set("Content-Type", "application/json")
	r.Response.WriteJson(response)
}

// sendError 发送 JSON-RPC 错误响应
func (h *JsonRpcProtocolHandler) sendError(r *ghttp.Request, id interface{}, code int, message string, data interface{}) {
	response := JsonRpcResponse{
		Jsonrpc: "2.0",
		Id:      id,
		Error: &JsonRpcError{
			Code:    code,
			Message: message,
			Data:    data,
		},
	}
	
	r.Response.Header().Set("Content-Type", "application/json")
	r.Response.WriteJson(response)
}

// JsonRpcRequest JSON-RPC 2.0 请求
type JsonRpcRequest struct {
	Jsonrpc string      `json:"jsonrpc"`
	Method  string      `json:"method"`
	Params  interface{} `json:"params,omitempty"`
	Id      interface{} `json:"id"`
}

// JsonRpcResponse JSON-RPC 2.0 响应
type JsonRpcResponse struct {
	Jsonrpc string        `json:"jsonrpc"`
	Result  interface{}   `json:"result,omitempty"`
	Error   *JsonRpcError `json:"error,omitempty"`
	Id      interface{}   `json:"id"`
}

// JsonRpcError JSON-RPC 2.0 错误
type JsonRpcError struct {
	Code    int         `json:"code"`
	Message string      `json:"message"`
	Data    interface{} `json:"data,omitempty"`
}
