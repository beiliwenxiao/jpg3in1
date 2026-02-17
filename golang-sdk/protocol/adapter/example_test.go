package adapter_test

import (
	"context"
	"fmt"

	"github.com/framework/golang-sdk/protocol/adapter"
)

// 示例：REST 请求转换为内部请求
func ExampleDefaultProtocolAdapter_TransformRequest_rest() {
	adapterInstance := adapter.NewDefaultProtocolAdapter()
	ctx := context.Background()

	// 创建 REST 外部请求
	externalReq := &adapter.ExternalRequest{
		Protocol: adapter.ProtocolREST,
		Headers: map[string]string{
			"X-Service-Name": "user-service",
			"X-Method-Name":  "getUser",
			"Content-Type":   "application/json",
		},
		Body: map[string]interface{}{
			"userId": "12345",
		},
	}

	// 转换为内部请求
	internalReq, err := adapterInstance.TransformRequest(ctx, externalReq)
	if err != nil {
		fmt.Printf("Error: %v\n", err)
		return
	}

	fmt.Printf("Service: %s\n", internalReq.Service)
	fmt.Printf("Method: %s\n", internalReq.Method)
	fmt.Printf("Has TraceId: %v\n", internalReq.TraceId != "")
	// Output:
	// Service: user-service
	// Method: getUser
	// Has TraceId: true
}

// 示例：JSON-RPC 请求转换
func ExampleDefaultProtocolAdapter_TransformRequest_jsonrpc() {
	adapterInstance := adapter.NewDefaultProtocolAdapter()
	ctx := context.Background()

	// 创建 JSON-RPC 外部请求
	externalReq := &adapter.ExternalRequest{
		Protocol: adapter.ProtocolJSONRPC,
		Body: map[string]interface{}{
			"jsonrpc": "2.0",
			"method":  "Calculator.add",
			"params": map[string]interface{}{
				"a": 10,
				"b": 20,
			},
			"id": 1,
		},
	}

	// 转换为内部请求
	internalReq, err := adapterInstance.TransformRequest(ctx, externalReq)
	if err != nil {
		fmt.Printf("Error: %v\n", err)
		return
	}

	fmt.Printf("Service: %s\n", internalReq.Service)
	fmt.Printf("Method: %s\n", internalReq.Method)
	// Output:
	// Service: Calculator
	// Method: add
}

// 示例：内部响应转换为 REST 响应
func ExampleDefaultProtocolAdapter_TransformResponse_rest() {
	adapterInstance := adapter.NewDefaultProtocolAdapter()
	ctx := context.Background()

	// 创建内部响应
	internalResp := &adapter.InternalResponse{
		Payload: []byte(`{"userId":"12345","name":"John Doe"}`),
		Headers: map[string]string{
			"Content-Type": "application/json",
		},
	}

	// 转换为 REST 响应
	externalResp, err := adapterInstance.TransformResponse(ctx, internalResp, adapter.ProtocolREST)
	if err != nil {
		fmt.Printf("Error: %v\n", err)
		return
	}

	fmt.Printf("Status Code: %d\n", externalResp.StatusCode)
	fmt.Printf("Protocol: %s\n", externalResp.Protocol)
	// Output:
	// Status Code: 200
	// Protocol: REST
}

// 示例：错误响应转换
func ExampleDefaultProtocolAdapter_TransformResponse_error() {
	adapterInstance := adapter.NewDefaultProtocolAdapter()
	ctx := context.Background()

	// 创建包含错误的内部响应
	internalResp := &adapter.InternalResponse{
		Error: &adapter.FrameworkError{
			Code:    adapter.ErrorNotFound,
			Message: "User not found",
			Details: map[string]interface{}{
				"userId": "12345",
			},
		},
	}

	// 转换为 REST 响应
	externalResp, err := adapterInstance.TransformResponse(ctx, internalResp, adapter.ProtocolREST)
	if err != nil {
		fmt.Printf("Error: %v\n", err)
		return
	}

	fmt.Printf("Status Code: %d\n", externalResp.StatusCode)
	fmt.Printf("Has Error: %v\n", externalResp.Error != nil)
	// Output:
	// Status Code: 404
	// Has Error: true
}
