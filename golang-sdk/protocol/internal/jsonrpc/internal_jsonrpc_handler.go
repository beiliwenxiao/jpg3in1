package jsonrpc

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"sync"

	"github.com/gogf/gf/v2/os/glog"
)

// InternalJsonRpcHandler 内部 JSON-RPC 协议处理器
type InternalJsonRpcHandler struct {
	listener net.Listener
	config   *InternalJsonRpcConfig
	handlers map[string]MethodHandler
	mu       sync.RWMutex
	stopChan chan struct{}
}

// InternalJsonRpcConfig 内部 JSON-RPC 配置
type InternalJsonRpcConfig struct {
	Host string
	Port int
}

// MethodHandler 方法处理器
type MethodHandler func(ctx context.Context, params interface{}) (interface{}, error)

// NewInternalJsonRpcHandler 创建内部 JSON-RPC 处理器
func NewInternalJsonRpcHandler(config *InternalJsonRpcConfig) *InternalJsonRpcHandler {
	return &InternalJsonRpcHandler{
		config:   config,
		handlers: make(map[string]MethodHandler),
		stopChan: make(chan struct{}),
	}
}

// Start 启动内部 JSON-RPC 服务器
func (h *InternalJsonRpcHandler) Start() error {
	address := fmt.Sprintf("%s:%d", h.config.Host, h.config.Port)
	
	listener, err := net.Listen("tcp", address)
	if err != nil {
		return fmt.Errorf("failed to listen on %s: %v", address, err)
	}
	
	h.listener = listener
	glog.Infof(context.Background(), "Internal JSON-RPC server listening on %s", address)
	
	// 接受连接
	go h.acceptConnections()
	
	return nil
}

// Stop 停止内部 JSON-RPC 服务器
func (h *InternalJsonRpcHandler) Stop(ctx context.Context) error {
	close(h.stopChan)
	
	if h.listener != nil {
		h.listener.Close()
	}
	
	glog.Info(ctx, "Internal JSON-RPC server stopped")
	return nil
}

// RegisterMethod 注册方法处理器
func (h *InternalJsonRpcHandler) RegisterMethod(method string, handler MethodHandler) {
	h.mu.Lock()
	defer h.mu.Unlock()
	
	h.handlers[method] = handler
}

// acceptConnections 接受连接
func (h *InternalJsonRpcHandler) acceptConnections() {
	for {
		select {
		case <-h.stopChan:
			return
		default:
			conn, err := h.listener.Accept()
			if err != nil {
				select {
				case <-h.stopChan:
					return
				default:
					glog.Errorf(context.Background(), "Failed to accept connection: %v", err)
					continue
				}
			}
			
			// 处理连接
			go h.handleConnection(conn)
		}
	}
}

// handleConnection 处理连接
func (h *InternalJsonRpcHandler) handleConnection(conn net.Conn) {
	defer conn.Close()
	
	ctx := context.Background()
	
	// 读取请求
	buffer := make([]byte, 4096)
	n, err := conn.Read(buffer)
	if err != nil {
		if err != io.EOF {
			glog.Errorf(ctx, "Failed to read request: %v", err)
		}
		return
	}
	
	// 解析 JSON-RPC 请求
	var request JsonRpcRequest
	if err := json.Unmarshal(buffer[:n], &request); err != nil {
		h.sendError(conn, nil, -32700, "Parse error", err.Error())
		return
	}
	
	// 验证请求
	if request.Jsonrpc != "2.0" {
		h.sendError(conn, request.Id, -32600, "Invalid Request", "jsonrpc must be 2.0")
		return
	}
	
	if request.Method == "" {
		h.sendError(conn, request.Id, -32600, "Invalid Request", "method is required")
		return
	}
	
	// 查找处理器
	h.mu.RLock()
	handler, exists := h.handlers[request.Method]
	h.mu.RUnlock()
	
	if !exists {
		h.sendError(conn, request.Id, -32601, "Method not found", fmt.Sprintf("method %s not found", request.Method))
		return
	}
	
	// 调用处理器
	result, err := handler(ctx, request.Params)
	if err != nil {
		h.sendError(conn, request.Id, -32603, "Internal error", err.Error())
		return
	}
	
	// 发送响应
	h.sendResponse(conn, request.Id, result)
}

// sendResponse 发送响应
func (h *InternalJsonRpcHandler) sendResponse(conn net.Conn, id interface{}, result interface{}) {
	response := JsonRpcResponse{
		Jsonrpc: "2.0",
		Id:      id,
		Result:  result,
	}
	
	data, _ := json.Marshal(response)
	conn.Write(data)
}

// sendError 发送错误响应
func (h *InternalJsonRpcHandler) sendError(conn net.Conn, id interface{}, code int, message string, data interface{}) {
	response := JsonRpcResponse{
		Jsonrpc: "2.0",
		Id:      id,
		Error: &JsonRpcError{
			Code:    code,
			Message: message,
			Data:    data,
		},
	}
	
	responseData, _ := json.Marshal(response)
	conn.Write(responseData)
}

// JsonRpcRequest JSON-RPC 请求
type JsonRpcRequest struct {
	Jsonrpc string      `json:"jsonrpc"`
	Method  string      `json:"method"`
	Params  interface{} `json:"params,omitempty"`
	Id      interface{} `json:"id"`
}

// JsonRpcResponse JSON-RPC 响应
type JsonRpcResponse struct {
	Jsonrpc string        `json:"jsonrpc"`
	Result  interface{}   `json:"result,omitempty"`
	Error   *JsonRpcError `json:"error,omitempty"`
	Id      interface{}   `json:"id"`
}

// JsonRpcError JSON-RPC 错误
type JsonRpcError struct {
	Code    int         `json:"code"`
	Message string      `json:"message"`
	Data    interface{} `json:"data,omitempty"`
}

// InternalJsonRpcClient 内部 JSON-RPC 客户端
type InternalJsonRpcClient struct {
	conn   net.Conn
	config *InternalJsonRpcConfig
}

// NewInternalJsonRpcClient 创建内部 JSON-RPC 客户端
func NewInternalJsonRpcClient(config *InternalJsonRpcConfig) *InternalJsonRpcClient {
	return &InternalJsonRpcClient{
		config: config,
	}
}

// Connect 连接到服务器
func (c *InternalJsonRpcClient) Connect() error {
	address := fmt.Sprintf("%s:%d", c.config.Host, c.config.Port)
	
	conn, err := net.Dial("tcp", address)
	if err != nil {
		return fmt.Errorf("failed to connect to %s: %v", address, err)
	}
	
	c.conn = conn
	return nil
}

// Close 关闭连接
func (c *InternalJsonRpcClient) Close() error {
	if c.conn != nil {
		return c.conn.Close()
	}
	return nil
}

// Call 调用远程方法
func (c *InternalJsonRpcClient) Call(ctx context.Context, method string, params interface{}, id interface{}) (interface{}, error) {
	if c.conn == nil {
		return nil, fmt.Errorf("client not connected")
	}
	
	// 构造请求
	request := JsonRpcRequest{
		Jsonrpc: "2.0",
		Method:  method,
		Params:  params,
		Id:      id,
	}
	
	// 序列化请求
	requestData, err := json.Marshal(request)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal request: %v", err)
	}
	
	// 发送请求
	if _, err := c.conn.Write(requestData); err != nil {
		return nil, fmt.Errorf("failed to send request: %v", err)
	}
	
	// 读取响应
	buffer := make([]byte, 4096)
	n, err := c.conn.Read(buffer)
	if err != nil {
		return nil, fmt.Errorf("failed to read response: %v", err)
	}
	
	// 解析响应
	var response JsonRpcResponse
	if err := json.NewDecoder(bytes.NewReader(buffer[:n])).Decode(&response); err != nil {
		return nil, fmt.Errorf("failed to decode response: %v", err)
	}
	
	// 检查错误
	if response.Error != nil {
		return nil, fmt.Errorf("JSON-RPC error %d: %s", response.Error.Code, response.Error.Message)
	}
	
	return response.Result, nil
}
