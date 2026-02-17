package adapter

import (
	"context"
	"encoding/json"
	"testing"
)

func TestDefaultProtocolAdapter_TransformRequest_REST(t *testing.T) {
	adapter := NewDefaultProtocolAdapter()
	ctx := context.Background()

	// 测试 REST 请求转换
	external := &ExternalRequest{
		Protocol: ProtocolREST,
		Headers: map[string]string{
			"X-Service-Name": "user-service",
			"X-Method-Name":  "getUser",
		},
		Body: map[string]interface{}{
			"userId": "123",
		},
	}

	internal, err := adapter.TransformRequest(ctx, external)
	if err != nil {
		t.Fatalf("TransformRequest failed: %v", err)
	}

	if internal.Service != "user-service" {
		t.Errorf("Expected service 'user-service', got '%s'", internal.Service)
	}

	if internal.Method != "getUser" {
		t.Errorf("Expected method 'getUser', got '%s'", internal.Method)
	}

	if internal.TraceId == "" {
		t.Error("TraceId should not be empty")
	}

	if len(internal.Payload) == 0 {
		t.Error("Payload should not be empty")
	}
}

func TestDefaultProtocolAdapter_TransformRequest_JSONRPC(t *testing.T) {
	adapter := NewDefaultProtocolAdapter()
	ctx := context.Background()

	// 测试 JSON-RPC 请求转换
	external := &ExternalRequest{
		Protocol: ProtocolJSONRPC,
		Body: map[string]interface{}{
			"jsonrpc": "2.0",
			"method":  "UserService.getUser",
			"params": map[string]interface{}{
				"userId": "123",
			},
			"id": 1,
		},
	}

	internal, err := adapter.TransformRequest(ctx, external)
	if err != nil {
		t.Fatalf("TransformRequest failed: %v", err)
	}

	if internal.Service != "UserService" {
		t.Errorf("Expected service 'UserService', got '%s'", internal.Service)
	}

	if internal.Method != "getUser" {
		t.Errorf("Expected method 'getUser', got '%s'", internal.Method)
	}
}

func TestDefaultProtocolAdapter_TransformRequest_WebSocket(t *testing.T) {
	adapter := NewDefaultProtocolAdapter()
	ctx := context.Background()

	// 测试 WebSocket 请求转换
	external := &ExternalRequest{
		Protocol: ProtocolWebSocket,
		Body: map[string]interface{}{
			"service": "chat-service",
			"method":  "sendMessage",
			"data": map[string]interface{}{
				"message": "Hello",
			},
		},
	}

	internal, err := adapter.TransformRequest(ctx, external)
	if err != nil {
		t.Fatalf("TransformRequest failed: %v", err)
	}

	if internal.Service != "chat-service" {
		t.Errorf("Expected service 'chat-service', got '%s'", internal.Service)
	}

	if internal.Method != "sendMessage" {
		t.Errorf("Expected method 'sendMessage', got '%s'", internal.Method)
	}
}

func TestDefaultProtocolAdapter_TransformRequest_MQTT(t *testing.T) {
	adapter := NewDefaultProtocolAdapter()
	ctx := context.Background()

	// 测试 MQTT 请求转换
	external := &ExternalRequest{
		Protocol: ProtocolMQTT,
		Headers: map[string]string{
			"topic": "device/temperature",
		},
		Body: map[string]interface{}{
			"value": 25.5,
		},
	}

	internal, err := adapter.TransformRequest(ctx, external)
	if err != nil {
		t.Fatalf("TransformRequest failed: %v", err)
	}

	if internal.Service != "device" {
		t.Errorf("Expected service 'device', got '%s'", internal.Service)
	}

	if internal.Method != "temperature" {
		t.Errorf("Expected method 'temperature', got '%s'", internal.Method)
	}
}

func TestDefaultProtocolAdapter_TransformResponse_Success(t *testing.T) {
	adapter := NewDefaultProtocolAdapter()
	ctx := context.Background()

	// 测试成功响应转换
	responseData := map[string]interface{}{
		"userId": "123",
		"name":   "John Doe",
	}
	payload, _ := json.Marshal(responseData)

	internal := &InternalResponse{
		Payload: payload,
		Headers: map[string]string{
			"Content-Type": "application/json",
		},
	}

	external, err := adapter.TransformResponse(ctx, internal, ProtocolREST)
	if err != nil {
		t.Fatalf("TransformResponse failed: %v", err)
	}

	if external.StatusCode != 200 {
		t.Errorf("Expected status code 200, got %d", external.StatusCode)
	}

	if external.Protocol != ProtocolREST {
		t.Errorf("Expected protocol REST, got %s", external.Protocol)
	}

	if external.Body == nil {
		t.Error("Body should not be nil")
	}
}

func TestDefaultProtocolAdapter_TransformResponse_Error(t *testing.T) {
	adapter := NewDefaultProtocolAdapter()
	ctx := context.Background()

	// 测试错误响应转换
	internal := &InternalResponse{
		Error: &FrameworkError{
			Code:    ErrorNotFound,
			Message: "User not found",
		},
	}

	external, err := adapter.TransformResponse(ctx, internal, ProtocolREST)
	if err != nil {
		t.Fatalf("TransformResponse failed: %v", err)
	}

	if external.StatusCode != 404 {
		t.Errorf("Expected status code 404, got %d", external.StatusCode)
	}

	if external.Error == nil {
		t.Error("Error should not be nil")
	}
}

func TestDefaultProtocolAdapter_TransformResponse_JSONRPC(t *testing.T) {
	adapter := NewDefaultProtocolAdapter()
	ctx := context.Background()

	// 测试 JSON-RPC 响应格式
	responseData := map[string]interface{}{
		"result": "success",
	}
	payload, _ := json.Marshal(responseData)

	internal := &InternalResponse{
		Payload: payload,
	}

	external, err := adapter.TransformResponse(ctx, internal, ProtocolJSONRPC)
	if err != nil {
		t.Fatalf("TransformResponse failed: %v", err)
	}

	// 验证 JSON-RPC 格式
	bodyMap, ok := external.Body.(map[string]interface{})
	if !ok {
		t.Fatal("Body should be a map")
	}

	if bodyMap["jsonrpc"] != "2.0" {
		t.Error("JSON-RPC version should be 2.0")
	}

	if bodyMap["result"] == nil {
		t.Error("Result should not be nil")
	}
}

func TestDefaultProtocolAdapter_GetSupportedProtocols(t *testing.T) {
	adapter := NewDefaultProtocolAdapter()

	protocols := adapter.GetSupportedProtocols()
	if len(protocols) == 0 {
		t.Error("Should support at least one protocol")
	}

	// 验证支持的协议
	expectedProtocols := []ProtocolType{
		ProtocolREST,
		ProtocolWebSocket,
		ProtocolJSONRPC,
		ProtocolMQTT,
		ProtocolGRPC,
		ProtocolInternalRPC,
		ProtocolCustomBinary,
	}

	for _, expected := range expectedProtocols {
		found := false
		for _, protocol := range protocols {
			if protocol == expected {
				found = true
				break
			}
		}
		if !found {
			t.Errorf("Protocol %s should be supported", expected)
		}
	}
}

func TestDefaultProtocolAdapter_TransformRequest_NilRequest(t *testing.T) {
	adapter := NewDefaultProtocolAdapter()
	ctx := context.Background()

	// 测试 nil 请求
	_, err := adapter.TransformRequest(ctx, nil)
	if err == nil {
		t.Error("Should return error for nil request")
	}
}

func TestDefaultProtocolAdapter_TransformResponse_NilResponse(t *testing.T) {
	adapter := NewDefaultProtocolAdapter()
	ctx := context.Background()

	// 测试 nil 响应
	_, err := adapter.TransformResponse(ctx, nil, ProtocolREST)
	if err == nil {
		t.Error("Should return error for nil response")
	}
}
