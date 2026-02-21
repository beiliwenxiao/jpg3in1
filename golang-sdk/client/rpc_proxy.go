package client

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"time"

	"gopkg.in/yaml.v3"
)

// RpcProxy RPC 服务代理
//
// 通过配置文件定义远程服务，调用时极简：
//
//	rpc := client.NewRpcProxyFromConfig("config.yaml")
//	msg := rpc.Call("php-service", "hello.sayHello", nil)
type RpcProxy struct {
	services map[string]serviceEndpoint
	client   *http.Client
}

type serviceEndpoint struct {
	Host string `yaml:"host"`
	Port int    `yaml:"port"`
}

// 配置文件结构
type proxyConfig struct {
	Framework struct {
		Services map[string]serviceEndpoint `yaml:"services"`
	} `yaml:"framework"`
}

// jsonRpcReq JSON-RPC 2.0 请求
type jsonRpcReq struct {
	Jsonrpc string      `json:"jsonrpc"`
	Method  string      `json:"method"`
	Params  interface{} `json:"params,omitempty"`
	ID      int         `json:"id"`
}

// jsonRpcResp JSON-RPC 2.0 响应
type jsonRpcResp struct {
	Jsonrpc string      `json:"jsonrpc"`
	Result  interface{} `json:"result,omitempty"`
	Error   interface{} `json:"error,omitempty"`
	ID      int         `json:"id"`
}

// NewRpcProxy 创建空的 RpcProxy
func NewRpcProxy() *RpcProxy {
	return &RpcProxy{
		services: make(map[string]serviceEndpoint),
		client:   &http.Client{Timeout: 5 * time.Second},
	}
}

// NewRpcProxyFromConfig 从配置文件创建 RpcProxy
func NewRpcProxyFromConfig(configPath string) (*RpcProxy, error) {
	data, err := os.ReadFile(configPath)
	if err != nil {
		return nil, fmt.Errorf("读取配置文件失败: %w", err)
	}

	var cfg proxyConfig
	if err := yaml.Unmarshal(data, &cfg); err != nil {
		return nil, fmt.Errorf("解析配置文件失败: %w", err)
	}

	proxy := NewRpcProxy()
	for name, ep := range cfg.Framework.Services {
		proxy.AddService(name, ep.Host, ep.Port)
	}
	return proxy, nil
}

// AddService 手动添加远程服务
func (p *RpcProxy) AddService(name, host string, port int) *RpcProxy {
	p.services[name] = serviceEndpoint{Host: host, Port: port}
	return p
}

// Call 调用远程服务，返回字符串结果
func (p *RpcProxy) Call(service, method string, params interface{}) (string, error) {
	ep, ok := p.services[service]
	if !ok {
		return "", fmt.Errorf("未知服务: %s，请在配置文件 framework.services 中定义", service)
	}

	url := fmt.Sprintf("http://%s:%d/jsonrpc", ep.Host, ep.Port)
	reqBody, _ := json.Marshal(jsonRpcReq{
		Jsonrpc: "2.0",
		Method:  method,
		Params:  params,
		ID:      1,
	})

	resp, err := p.client.Post(url, "application/json", bytes.NewBuffer(reqBody))
	if err != nil {
		return "", fmt.Errorf("RPC 调用失败: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", fmt.Errorf("读取响应失败: %w", err)
	}

	var rpcResp jsonRpcResp
	if err := json.Unmarshal(body, &rpcResp); err != nil {
		return "", fmt.Errorf("解析响应失败: %w", err)
	}

	if rpcResp.Error != nil {
		return "", fmt.Errorf("RPC 错误: %v", rpcResp.Error)
	}

	return fmt.Sprintf("%v", rpcResp.Result), nil
}

// CallResult 调用远程服务，返回原始 interface{} 结果
func (p *RpcProxy) CallResult(service, method string, params interface{}) (interface{}, error) {
	ep, ok := p.services[service]
	if !ok {
		return nil, fmt.Errorf("未知服务: %s，请在配置文件 framework.services 中定义", service)
	}

	url := fmt.Sprintf("http://%s:%d/jsonrpc", ep.Host, ep.Port)
	reqBody, _ := json.Marshal(jsonRpcReq{
		Jsonrpc: "2.0",
		Method:  method,
		Params:  params,
		ID:      1,
	})

	resp, err := p.client.Post(url, "application/json", bytes.NewBuffer(reqBody))
	if err != nil {
		return nil, fmt.Errorf("RPC 调用失败: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("读取响应失败: %w", err)
	}

	var rpcResp jsonRpcResp
	if err := json.Unmarshal(body, &rpcResp); err != nil {
		return nil, fmt.Errorf("解析响应失败: %w", err)
	}

	if rpcResp.Error != nil {
		return nil, fmt.Errorf("RPC 错误: %v", rpcResp.Error)
	}

	return rpcResp.Result, nil
}
