package external

import (
	"bytes"
	"context"
	"encoding/json"
	"net/http"
	"testing"
	"time"

	"github.com/framework/golang-sdk/protocol/external/jsonrpc"
	"github.com/framework/golang-sdk/protocol/external/rest"
	"github.com/leanovate/gopter"
	"github.com/leanovate/gopter/gen"
	"github.com/leanovate/gopter/prop"
)

// TestExternalProtocolHandlingCompleteness 属性测试：外部协议处理完整性
// Feature: multi-language-communication-framework, Property 3: 外部协议处理完整性
// 验证需求: 2.1, 2.2, 2.3, 2.4
func TestExternalProtocolHandlingCompleteness(t *testing.T) {
	properties := gopter.NewProperties(nil)

	properties.Property("REST protocol handles requests correctly", prop.ForAll(
		func(path string) bool {
			config := &rest.RestConfig{
				Host: "127.0.0.1",
				Port: 9001,
				Path: "/api",
			}
			
			handler := rest.NewRestProtocolHandler(config)
			if handler == nil {
				return false
			}
			
			err := handler.Start()
			if err != nil {
				return false
			}
			defer handler.Stop(context.Background())
			
			time.Sleep(300 * time.Millisecond)
			
			// 测试 REST 请求
			resp, err := http.Get("http://127.0.0.1:9001/api/" + path)
			if err != nil {
				return false
			}
			defer resp.Body.Close()
			
			// 应该返回有效的响应
			return resp.StatusCode == http.StatusOK
		},
		gen.Identifier(),
	))

	properties.Property("JSON-RPC protocol handles valid requests", prop.ForAll(
		func(method string, id int) bool {
			config := &jsonrpc.JsonRpcConfig{
				Host: "127.0.0.1",
				Port: 9002,
				Path: "/jsonrpc",
			}
			
			handler := jsonrpc.NewJsonRpcProtocolHandler(config)
			if handler == nil {
				return false
			}
			
			err := handler.Start()
			if err != nil {
				return false
			}
			defer handler.Stop(context.Background())
			
			time.Sleep(300 * time.Millisecond)
			
			// 构造 JSON-RPC 请求
			request := map[string]interface{}{
				"jsonrpc": "2.0",
				"method":  method,
				"id":      id,
			}
			
			requestBody, _ := json.Marshal(request)
			resp, err := http.Post("http://127.0.0.1:9002/jsonrpc", "application/json", bytes.NewBuffer(requestBody))
			if err != nil {
				return false
			}
			defer resp.Body.Close()
			
			// 解析响应
			var response map[string]interface{}
			if err := json.NewDecoder(resp.Body).Decode(&response); err != nil {
				return false
			}
			
			// 验证响应格式
			if response["jsonrpc"] != "2.0" {
				return false
			}
			
			// 应该有 result 或 error
			return response["result"] != nil || response["error"] != nil
		},
		gen.Identifier(),
		gen.Int(),
	))

	properties.TestingRun(t, gopter.ConsoleReporter(false))
}

// TestHTTPMethodSupportCompleteness 属性测试：HTTP 方法支持完整性
// Feature: multi-language-communication-framework, Property 4: HTTP 方法支持完整性
// 验证需求: 2.5
func TestHTTPMethodSupportCompleteness(t *testing.T) {
	// 启动 REST 服务器
	config := &rest.RestConfig{
		Host: "127.0.0.1",
		Port: 9003,
		Path: "/api",
	}
	
	handler := rest.NewRestProtocolHandler(config)
	err := handler.Start()
	if err != nil {
		t.Fatalf("Failed to start REST handler: %v", err)
	}
	defer handler.Stop(context.Background())
	
	time.Sleep(500 * time.Millisecond)
	
	properties := gopter.NewProperties(nil)

	properties.Property("All standard HTTP methods are supported", prop.ForAll(
		func(path string) bool {
			methods := []string{
				http.MethodGet,
				http.MethodPost,
				http.MethodPut,
				http.MethodDelete,
				http.MethodPatch,
			}
			
			client := &http.Client{}
			
			for _, method := range methods {
				req, err := http.NewRequest(method, "http://127.0.0.1:9003/api/"+path, nil)
				if err != nil {
					return false
				}
				
				resp, err := client.Do(req)
				if err != nil {
					return false
				}
				resp.Body.Close()
				
				// 所有方法都应该被处理（返回 200）
				if resp.StatusCode != http.StatusOK {
					return false
				}
			}
			
			return true
		},
		gen.Identifier(),
	))

	properties.TestingRun(t, gopter.ConsoleReporter(false))
}

// TestProtocolErrorHandling 测试协议错误处理
func TestProtocolErrorHandling(t *testing.T) {
	properties := gopter.NewProperties(nil)

	properties.Property("JSON-RPC returns error for invalid version", prop.ForAll(
		func(version string, method string) bool {
			// 跳过有效版本
			if version == "2.0" {
				return true
			}
			
			config := &jsonrpc.JsonRpcConfig{
				Host: "127.0.0.1",
				Port: 9004,
				Path: "/jsonrpc",
			}
			
			handler := jsonrpc.NewJsonRpcProtocolHandler(config)
			err := handler.Start()
			if err != nil {
				return false
			}
			defer handler.Stop(context.Background())
			
			time.Sleep(300 * time.Millisecond)
			
			// 构造无效版本的请求
			request := map[string]interface{}{
				"jsonrpc": version,
				"method":  method,
				"id":      1,
			}
			
			requestBody, _ := json.Marshal(request)
			resp, err := http.Post("http://127.0.0.1:9004/jsonrpc", "application/json", bytes.NewBuffer(requestBody))
			if err != nil {
				return false
			}
			defer resp.Body.Close()
			
			// 解析响应
			var response map[string]interface{}
			if err := json.NewDecoder(resp.Body).Decode(&response); err != nil {
				return false
			}
			
			// 应该返回错误
			return response["error"] != nil
		},
		gen.AlphaString(),
		gen.Identifier(),
	))

	properties.TestingRun(t, gopter.ConsoleReporter(false))
}
