package rest

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"

	"github.com/gogf/gf/v2/frame/g"
	"github.com/gogf/gf/v2/net/ghttp"
)

// RestProtocolHandler REST 协议处理器
type RestProtocolHandler struct {
	server *ghttp.Server
	config *RestConfig
}

// RestConfig REST 配置
type RestConfig struct {
	Host string
	Port int
	Path string
}

// NewRestProtocolHandler 创建 REST 协议处理器
func NewRestProtocolHandler(config *RestConfig) *RestProtocolHandler {
	// 为每个handler创建独立的命名服务器实例
	serverName := fmt.Sprintf("rest-%s-%d", config.Host, config.Port)
	server := g.Server(serverName)
	return &RestProtocolHandler{
		server: server,
		config: config,
	}
}

// Start 启动 REST 服务器
func (h *RestProtocolHandler) Start() error {
	// 配置服务器
	h.server.SetAddr(h.config.Host + ":" + strconv.Itoa(h.config.Port))
	
	// 注册路由
	h.registerRoutes()
	
	// 启动服务器
	go h.server.Run()
	
	return nil
}

// Stop 停止 REST 服务器
func (h *RestProtocolHandler) Stop(ctx context.Context) error {
	return h.server.Shutdown()
}

// registerRoutes 注册路由
func (h *RestProtocolHandler) registerRoutes() {
	group := h.server.Group(h.config.Path)
	
	// 支持所有标准 HTTP 方法
	group.GET("/*", h.handleRequest)
	group.POST("/*", h.handleRequest)
	group.PUT("/*", h.handleRequest)
	group.DELETE("/*", h.handleRequest)
	group.PATCH("/*", h.handleRequest)
}

// handleRequest 处理 HTTP 请求
func (h *RestProtocolHandler) handleRequest(r *ghttp.Request) {
	// 解析请求
	request := &RestRequest{
		Method:  r.Method,
		Path:    r.URL.Path,
		Headers: make(map[string]string),
		Query:   make(map[string]string),
	}
	
	// 提取请求头
	for key, values := range r.Header {
		if len(values) > 0 {
			request.Headers[key] = values[0]
		}
	}
	
	// 提取查询参数
	for key, values := range r.URL.Query() {
		if len(values) > 0 {
			request.Query[key] = values[0]
		}
	}
	
	// 读取请求体
	if r.Method == http.MethodPost || r.Method == http.MethodPut || r.Method == http.MethodPatch {
		body := r.GetBody()
		if len(body) > 0 {
			var bodyData interface{}
			if err := json.Unmarshal(body, &bodyData); err == nil {
				request.Body = bodyData
			} else {
				request.Body = string(body)
			}
		}
	}
	
	// TODO: 调用协议适配器转换请求
	// TODO: 调用消息路由器路由到目标服务
	// TODO: 获取响应并转换回 REST 格式
	
	// 临时响应
	response := &RestResponse{
		StatusCode: http.StatusOK,
		Headers:    make(map[string]string),
		Body: map[string]interface{}{
			"message": "REST API handler is working",
			"method":  request.Method,
			"path":    request.Path,
		},
	}
	
	h.sendResponse(r, response)
}

// sendResponse 发送响应
func (h *RestProtocolHandler) sendResponse(r *ghttp.Request, response *RestResponse) {
	// 设置响应头
	for key, value := range response.Headers {
		r.Response.Header().Set(key, value)
	}
	
	// 设置状态码
	r.Response.WriteStatus(response.StatusCode)
	
	// 发送响应体
	if response.Body != nil {
		r.Response.WriteJson(response.Body)
	}
}

// RestRequest REST 请求
type RestRequest struct {
	Method  string
	Path    string
	Headers map[string]string
	Query   map[string]string
	Body    interface{}
}

// RestResponse REST 响应
type RestResponse struct {
	StatusCode int
	Headers    map[string]string
	Body       interface{}
}
