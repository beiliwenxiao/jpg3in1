package router_test

import (
	"context"
	"fmt"

	"github.com/framework/golang-sdk/protocol/adapter"
	"github.com/framework/golang-sdk/protocol/router"
)

// 示例：基本消息路由
func ExampleDefaultMessageRouter_Route() {
	// 创建路由器
	messageRouter := router.NewDefaultMessageRouter(nil)

	// 添加服务端点
	endpoint := &router.ServiceEndpoint{
		ServiceId: "user-service-1",
		Address:   "localhost",
		Port:      8080,
		Protocol:  adapter.ProtocolGRPC,
	}
	messageRouter.AddServiceEndpoint("user-service", endpoint)

	// 创建请求
	request := &adapter.InternalRequest{
		Service: "user-service",
		Method:  "getUser",
	}

	// 路由请求
	selectedEndpoint, err := messageRouter.Route(context.Background(), request)
	if err != nil {
		fmt.Printf("Error: %v\n", err)
		return
	}

	fmt.Printf("Selected Endpoint: %s\n", selectedEndpoint.ServiceId)
	fmt.Printf("Address: %s:%d\n", selectedEndpoint.Address, selectedEndpoint.Port)
	// Output:
	// Selected Endpoint: user-service-1
	// Address: localhost:8080
}

// 示例：使用路由规则
func ExampleDefaultMessageRouter_RegisterRule() {
	// 创建路由器
	messageRouter := router.NewDefaultMessageRouter(nil)

	// 添加服务端点
	messageRouter.AddServiceEndpoint("service-a", &router.ServiceEndpoint{
		ServiceId: "service-a-1",
		Address:   "localhost",
		Port:      8080,
	})
	messageRouter.AddServiceEndpoint("service-b", &router.ServiceEndpoint{
		ServiceId: "service-b-1",
		Address:   "localhost",
		Port:      9090,
	})

	// 注册路由规则：特殊方法路由到 service-b
	rule := &router.RoutingRule{
		Name:     "special-method-rule",
		Priority: 10,
		Matcher: func(req *adapter.InternalRequest) bool {
			return req.Method == "specialMethod"
		},
		Target: func(req *adapter.InternalRequest) string {
			return "service-b"
		},
	}
	messageRouter.RegisterRule(rule)

	// 创建特殊方法请求
	request := &adapter.InternalRequest{
		Service: "service-a",
		Method:  "specialMethod",
	}

	// 路由请求（应该被路由到 service-b）
	selectedEndpoint, err := messageRouter.Route(context.Background(), request)
	if err != nil {
		fmt.Printf("Error: %v\n", err)
		return
	}

	fmt.Printf("Routed to: %s\n", selectedEndpoint.ServiceId)
	// Output:
	// Routed to: service-b-1
}

// 示例：轮询负载均衡
func ExampleRoundRobinLoadBalancer_Select() {
	lb := router.NewRoundRobinLoadBalancer()

	endpoints := []*router.ServiceEndpoint{
		{ServiceId: "endpoint-1", Address: "localhost", Port: 8080},
		{ServiceId: "endpoint-2", Address: "localhost", Port: 8081},
		{ServiceId: "endpoint-3", Address: "localhost", Port: 8082},
	}

	// 选择三次，应该轮询
	for i := 0; i < 3; i++ {
		endpoint, _ := lb.Select(endpoints)
		fmt.Printf("Round %d: %s\n", i+1, endpoint.ServiceId)
	}
	// Output:
	// Round 1: endpoint-1
	// Round 2: endpoint-2
	// Round 3: endpoint-3
}

// 示例：加权轮询负载均衡
func ExampleWeightedRoundRobinLoadBalancer_Select() {
	lb := router.NewWeightedRoundRobinLoadBalancer()

	endpoints := []*router.ServiceEndpoint{
		{
			ServiceId: "endpoint-1",
			Address:   "localhost",
			Port:      8080,
			Metadata:  map[string]string{"weight": "1"},
		},
		{
			ServiceId: "endpoint-2",
			Address:   "localhost",
			Port:      8081,
			Metadata:  map[string]string{"weight": "3"},
		},
	}

	// 选择多次，endpoint-2 应该被选中更多次
	results := make(map[string]int)
	for i := 0; i < 4; i++ {
		endpoint, _ := lb.Select(endpoints)
		results[endpoint.ServiceId]++
	}

	fmt.Printf("endpoint-1 selected: %d times\n", results["endpoint-1"])
	fmt.Printf("endpoint-2 selected: %d times\n", results["endpoint-2"])
	// Output:
	// endpoint-1 selected: 1 times
	// endpoint-2 selected: 3 times
}

// 示例：最少连接负载均衡
func ExampleLeastConnectionLoadBalancer_Select() {
	lb := router.NewLeastConnectionLoadBalancer()

	endpoints := []*router.ServiceEndpoint{
		{ServiceId: "endpoint-1", Address: "localhost", Port: 8080},
		{ServiceId: "endpoint-2", Address: "localhost", Port: 8081},
	}

	// 第一次选择
	endpoint1, _ := lb.Select(endpoints)
	fmt.Printf("First selection: %s\n", endpoint1.ServiceId)

	// 第二次选择（应该选择另一个）
	endpoint2, _ := lb.Select(endpoints)
	fmt.Printf("Second selection: %s\n", endpoint2.ServiceId)

	// 释放第一个连接
	lb.ReleaseConnection(endpoint1.ServiceId)

	// 第三次选择（应该再次选择第一个）
	endpoint3, _ := lb.Select(endpoints)
	fmt.Printf("Third selection: %s\n", endpoint3.ServiceId)
	// Output:
	// First selection: endpoint-1
	// Second selection: endpoint-2
	// Third selection: endpoint-1
}
