package websocket

import (
	"context"
	"encoding/json"
	"fmt"

	"github.com/gogf/gf/v2/frame/g"
	"github.com/gogf/gf/v2/net/ghttp"
	"github.com/gogf/gf/v2/os/glog"
)

// WebSocketProtocolHandler WebSocket 协议处理器
type WebSocketProtocolHandler struct {
	server *ghttp.Server
	config *WebSocketConfig
}

// WebSocketConfig WebSocket 配置
type WebSocketConfig struct {
	Host string
	Port int
	Path string
}

// NewWebSocketProtocolHandler 创建 WebSocket 协议处理器
func NewWebSocketProtocolHandler(config *WebSocketConfig) *WebSocketProtocolHandler {
	// 为每个handler创建独立的命名服务器实例
	serverName := fmt.Sprintf("websocket-%s-%d", config.Host, config.Port)
	server := g.Server(serverName)
	return &WebSocketProtocolHandler{
		server: server,
		config: config,
	}
}

// Start 启动 WebSocket 服务器
func (h *WebSocketProtocolHandler) Start() error {
	// 配置服务器
	h.server.SetAddr(fmt.Sprintf("%s:%d", h.config.Host, h.config.Port))
	
	// 注册 WebSocket 路由
	h.server.BindHandler(h.config.Path, h.handleWebSocket)
	
	// 启动服务器
	go h.server.Run()
	
	return nil
}

// Stop 停止 WebSocket 服务器
func (h *WebSocketProtocolHandler) Stop(ctx context.Context) error {
	return h.server.Shutdown()
}

// handleWebSocket 处理 WebSocket 连接
func (h *WebSocketProtocolHandler) handleWebSocket(r *ghttp.Request) {
	ws, err := r.WebSocket()
	if err != nil {
		glog.Error(r.Context(), "WebSocket upgrade failed:", err)
		r.Response.WriteStatus(500)
		return
	}
	defer ws.Close()
	
	glog.Info(r.Context(), "WebSocket connection established")
	
	// 持续读取消息
	for {
		// 读取消息（支持文本和二进制）
		msgType, message, err := ws.ReadMessage()
		if err != nil {
			glog.Error(r.Context(), "WebSocket read error:", err)
			break
		}
		
		// 处理消息
		response := h.handleMessage(msgType, message)
		
		// 发送响应
		if err := ws.WriteMessage(msgType, response); err != nil {
			glog.Error(r.Context(), "WebSocket write error:", err)
			break
		}
	}
	
	glog.Info(r.Context(), "WebSocket connection closed")
}

// handleMessage 处理 WebSocket 消息
func (h *WebSocketProtocolHandler) handleMessage(msgType int, message []byte) []byte {
	// 创建请求对象
	_ = &WebSocketMessage{
		Type: msgType,
		Data: message,
	}
	
	// TODO: 调用协议适配器转换请求
	// TODO: 调用消息路由器路由到目标服务
	// TODO: 获取响应并转换回 WebSocket 格式
	
	// 临时响应 - 回显消息
	response := &WebSocketMessage{
		Type: msgType,
		Data: message,
	}
	
	// 如果是文本消息，尝试解析 JSON 并添加响应信息
	if msgType == 1 { // TextMessage
		var data map[string]interface{}
		if err := json.Unmarshal(message, &data); err == nil {
			data["echo"] = true
			data["handler"] = "websocket"
			responseData, _ := json.Marshal(data)
			response.Data = responseData
		}
	}
	
	return response.Data
}

// WebSocketMessage WebSocket 消息
type WebSocketMessage struct {
	Type int    // 1: 文本消息, 2: 二进制消息
	Data []byte
}
