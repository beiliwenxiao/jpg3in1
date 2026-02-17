package router

import (
	"context"
	"testing"

	"github.com/framework/golang-sdk/protocol/adapter"
)

func TestDefaultMessageRouter_Route_Success(t *testing.T) {
	router := NewDefaultMessageRouter(nil)
	ctx := context.Background()

	// 添加服务端点
	endpoint := &ServiceEndpoint{
		ServiceId: "user-service-1",
		Address:   "localhost",
		Port:      8080,
		Protocol:  adapter.ProtocolGRPC,
	}
	router.AddServiceEndpoint("user-service", endpoint)

	// 创建请求
	request := &adapter.InternalRequest{
		Service: "user-service",
		Method:  "getUser",
	}

	// 路由请求
	result, err := router.Route(ctx, request)
	if err != nil {
		t.Fatalf("Route failed: %v", err)
	}

	if result.ServiceId != "user-service-1" {
		t.Errorf("Expected service ID 'user-service-1', got '%s'", result.ServiceId)
	}
}

func TestDefaultMessageRouter_Route_ServiceNotFound(t *testing.T) {
	router := NewDefaultMessageRouter(nil)
	ctx := context.Background()

	// 创建请求（服务不存在）
	request := &adapter.InternalRequest{
		Service: "non-existent-service",
		Method:  "someMethod",
	}

	// 路由请求
	_, err := router.Route(ctx, request)
	if err == nil {
		t.Error("Should return error for non-existent service")
	}
}

func TestDefaultMessageRouter_RegisterRule(t *testing.T) {
	router := NewDefaultMessageRouter(nil)

	// 注册路由规则
	rule := &RoutingRule{
		Name:     "test-rule",
		Priority: 10,
		Matcher: func(req *adapter.InternalRequest) bool {
			return req.Service == "test-service"
		},
		Target: func(req *adapter.InternalRequest) string {
			return "target-service"
		},
	}

	err := router.RegisterRule(rule)
	if err != nil {
		t.Fatalf("RegisterRule failed: %v", err)
	}

	// 验证规则已注册
	if len(router.rules) != 1 {
		t.Errorf("Expected 1 rule, got %d", len(router.rules))
	}
}

func TestDefaultMessageRouter_RegisterRule_NilRule(t *testing.T) {
	router := NewDefaultMessageRouter(nil)

	// 注册 nil 规则
	err := router.RegisterRule(nil)
	if err == nil {
		t.Error("Should return error for nil rule")
	}
}

func TestDefaultMessageRouter_RegisterRule_NilMatcher(t *testing.T) {
	router := NewDefaultMessageRouter(nil)

	// 注册没有 Matcher 的规则
	rule := &RoutingRule{
		Name:     "test-rule",
		Priority: 10,
		Target: func(req *adapter.InternalRequest) string {
			return "target-service"
		},
	}

	err := router.RegisterRule(rule)
	if err == nil {
		t.Error("Should return error for rule without matcher")
	}
}

func TestDefaultMessageRouter_UpdateRoutingTable(t *testing.T) {
	router := NewDefaultMessageRouter(nil)

	// 更新路由表
	services := map[string][]*ServiceEndpoint{
		"service1": {
			{ServiceId: "s1-1", Address: "localhost", Port: 8080},
			{ServiceId: "s1-2", Address: "localhost", Port: 8081},
		},
		"service2": {
			{ServiceId: "s2-1", Address: "localhost", Port: 9090},
		},
	}

	err := router.UpdateRoutingTable(services)
	if err != nil {
		t.Fatalf("UpdateRoutingTable failed: %v", err)
	}

	// 验证路由表
	endpoints, err := router.GetServiceEndpoints("service1")
	if err != nil {
		t.Fatalf("GetServiceEndpoints failed: %v", err)
	}

	if len(endpoints) != 2 {
		t.Errorf("Expected 2 endpoints for service1, got %d", len(endpoints))
	}
}

func TestDefaultMessageRouter_GetServiceEndpoints(t *testing.T) {
	router := NewDefaultMessageRouter(nil)

	// 添加端点
	endpoint1 := &ServiceEndpoint{ServiceId: "e1", Address: "localhost", Port: 8080}
	endpoint2 := &ServiceEndpoint{ServiceId: "e2", Address: "localhost", Port: 8081}

	router.AddServiceEndpoint("test-service", endpoint1)
	router.AddServiceEndpoint("test-service", endpoint2)

	// 获取端点
	endpoints, err := router.GetServiceEndpoints("test-service")
	if err != nil {
		t.Fatalf("GetServiceEndpoints failed: %v", err)
	}

	if len(endpoints) != 2 {
		t.Errorf("Expected 2 endpoints, got %d", len(endpoints))
	}
}

func TestDefaultMessageRouter_AddServiceEndpoint(t *testing.T) {
	router := NewDefaultMessageRouter(nil)

	endpoint := &ServiceEndpoint{
		ServiceId: "test-endpoint",
		Address:   "localhost",
		Port:      8080,
	}

	err := router.AddServiceEndpoint("test-service", endpoint)
	if err != nil {
		t.Fatalf("AddServiceEndpoint failed: %v", err)
	}

	// 验证端点已添加
	endpoints, _ := router.GetServiceEndpoints("test-service")
	if len(endpoints) != 1 {
		t.Errorf("Expected 1 endpoint, got %d", len(endpoints))
	}
}

func TestDefaultMessageRouter_RemoveServiceEndpoint(t *testing.T) {
	router := NewDefaultMessageRouter(nil)

	// 添加端点
	endpoint := &ServiceEndpoint{
		ServiceId: "test-endpoint",
		Address:   "localhost",
		Port:      8080,
	}
	router.AddServiceEndpoint("test-service", endpoint)

	// 移除端点
	err := router.RemoveServiceEndpoint("test-service", "test-endpoint")
	if err != nil {
		t.Fatalf("RemoveServiceEndpoint failed: %v", err)
	}

	// 验证端点已移除
	endpoints, _ := router.GetServiceEndpoints("test-service")
	if len(endpoints) != 0 {
		t.Errorf("Expected 0 endpoints, got %d", len(endpoints))
	}
}

func TestDefaultMessageRouter_RoutingRulePriority(t *testing.T) {
	router := NewDefaultMessageRouter(nil)
	ctx := context.Background()

	// 添加端点
	router.AddServiceEndpoint("service-a", &ServiceEndpoint{
		ServiceId: "service-a-1",
		Address:   "localhost",
		Port:      8080,
	})
	router.AddServiceEndpoint("service-b", &ServiceEndpoint{
		ServiceId: "service-b-1",
		Address:   "localhost",
		Port:      9090,
	})

	// 注册低优先级规则
	router.RegisterRule(&RoutingRule{
		Name:     "low-priority",
		Priority: 1,
		Matcher: func(req *adapter.InternalRequest) bool {
			return true // 匹配所有请求
		},
		Target: func(req *adapter.InternalRequest) string {
			return "service-a"
		},
	})

	// 注册高优先级规则
	router.RegisterRule(&RoutingRule{
		Name:     "high-priority",
		Priority: 10,
		Matcher: func(req *adapter.InternalRequest) bool {
			return req.Method == "specialMethod"
		},
		Target: func(req *adapter.InternalRequest) string {
			return "service-b"
		},
	})

	// 测试高优先级规则
	request := &adapter.InternalRequest{
		Service: "any-service",
		Method:  "specialMethod",
	}

	result, err := router.Route(ctx, request)
	if err != nil {
		t.Fatalf("Route failed: %v", err)
	}

	if result.ServiceId != "service-b-1" {
		t.Errorf("Expected service-b-1 (high priority rule), got %s", result.ServiceId)
	}
}

func TestDefaultMessageRouter_Route_NilRequest(t *testing.T) {
	router := NewDefaultMessageRouter(nil)
	ctx := context.Background()

	// 测试 nil 请求
	_, err := router.Route(ctx, nil)
	if err == nil {
		t.Error("Should return error for nil request")
	}
}
