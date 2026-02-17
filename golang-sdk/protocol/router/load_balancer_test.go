package router

import (
	"testing"
)

func TestRoundRobinLoadBalancer_Select(t *testing.T) {
	lb := NewRoundRobinLoadBalancer()

	endpoints := []*ServiceEndpoint{
		{ServiceId: "e1", Address: "localhost", Port: 8080},
		{ServiceId: "e2", Address: "localhost", Port: 8081},
		{ServiceId: "e3", Address: "localhost", Port: 8082},
	}

	// 测试轮询
	results := make([]string, 6)
	for i := 0; i < 6; i++ {
		endpoint, err := lb.Select(endpoints)
		if err != nil {
			t.Fatalf("Select failed: %v", err)
		}
		results[i] = endpoint.ServiceId
	}

	// 验证轮询顺序
	expected := []string{"e1", "e2", "e3", "e1", "e2", "e3"}
	for i, result := range results {
		if result != expected[i] {
			t.Errorf("Round %d: expected %s, got %s", i, expected[i], result)
		}
	}
}

func TestRoundRobinLoadBalancer_Select_EmptyEndpoints(t *testing.T) {
	lb := NewRoundRobinLoadBalancer()

	_, err := lb.Select([]*ServiceEndpoint{})
	if err == nil {
		t.Error("Should return error for empty endpoints")
	}
}

func TestRandomLoadBalancer_Select(t *testing.T) {
	lb := NewRandomLoadBalancer()

	endpoints := []*ServiceEndpoint{
		{ServiceId: "e1", Address: "localhost", Port: 8080},
		{ServiceId: "e2", Address: "localhost", Port: 8081},
		{ServiceId: "e3", Address: "localhost", Port: 8082},
	}

	// 测试随机选择
	selectedIds := make(map[string]bool)
	for i := 0; i < 100; i++ {
		endpoint, err := lb.Select(endpoints)
		if err != nil {
			t.Fatalf("Select failed: %v", err)
		}
		selectedIds[endpoint.ServiceId] = true
	}

	// 验证所有端点都被选中过（概率测试）
	if len(selectedIds) != 3 {
		t.Errorf("Expected all 3 endpoints to be selected, got %d", len(selectedIds))
	}
}

func TestRandomLoadBalancer_Select_EmptyEndpoints(t *testing.T) {
	lb := NewRandomLoadBalancer()

	_, err := lb.Select([]*ServiceEndpoint{})
	if err == nil {
		t.Error("Should return error for empty endpoints")
	}
}

func TestWeightedRoundRobinLoadBalancer_Select(t *testing.T) {
	lb := NewWeightedRoundRobinLoadBalancer()

	endpoints := []*ServiceEndpoint{
		{
			ServiceId: "e1",
			Address:   "localhost",
			Port:      8080,
			Metadata:  map[string]string{"weight": "1"},
		},
		{
			ServiceId: "e2",
			Address:   "localhost",
			Port:      8081,
			Metadata:  map[string]string{"weight": "2"},
		},
		{
			ServiceId: "e3",
			Address:   "localhost",
			Port:      8082,
			Metadata:  map[string]string{"weight": "3"},
		},
	}

	// 测试加权轮询
	results := make(map[string]int)
	for i := 0; i < 60; i++ {
		endpoint, err := lb.Select(endpoints)
		if err != nil {
			t.Fatalf("Select failed: %v", err)
		}
		results[endpoint.ServiceId]++
	}

	// 验证权重分布（大致比例应该是 1:2:3）
	// e1 应该被选中约 10 次，e2 约 20 次，e3 约 30 次
	if results["e1"] < 5 || results["e1"] > 15 {
		t.Errorf("e1 selection count out of range: %d", results["e1"])
	}
	if results["e2"] < 15 || results["e2"] > 25 {
		t.Errorf("e2 selection count out of range: %d", results["e2"])
	}
	if results["e3"] < 25 || results["e3"] > 35 {
		t.Errorf("e3 selection count out of range: %d", results["e3"])
	}
}

func TestWeightedRoundRobinLoadBalancer_Select_NoWeight(t *testing.T) {
	lb := NewWeightedRoundRobinLoadBalancer()

	endpoints := []*ServiceEndpoint{
		{ServiceId: "e1", Address: "localhost", Port: 8080},
		{ServiceId: "e2", Address: "localhost", Port: 8081},
	}

	// 测试没有权重的情况（应该使用默认权重1）
	endpoint, err := lb.Select(endpoints)
	if err != nil {
		t.Fatalf("Select failed: %v", err)
	}

	if endpoint == nil {
		t.Error("Selected endpoint should not be nil")
	}
}

func TestLeastConnectionLoadBalancer_Select(t *testing.T) {
	lb := NewLeastConnectionLoadBalancer()

	endpoints := []*ServiceEndpoint{
		{ServiceId: "e1", Address: "localhost", Port: 8080},
		{ServiceId: "e2", Address: "localhost", Port: 8081},
		{ServiceId: "e3", Address: "localhost", Port: 8082},
	}

	// 第一次选择应该选中 e1（连接数都是0）
	endpoint1, err := lb.Select(endpoints)
	if err != nil {
		t.Fatalf("Select failed: %v", err)
	}
	if endpoint1.ServiceId != "e1" {
		t.Errorf("Expected e1, got %s", endpoint1.ServiceId)
	}

	// 第二次选择应该选中 e2（e1 连接数为1）
	endpoint2, err := lb.Select(endpoints)
	if err != nil {
		t.Fatalf("Select failed: %v", err)
	}
	if endpoint2.ServiceId != "e2" {
		t.Errorf("Expected e2, got %s", endpoint2.ServiceId)
	}

	// 释放 e1 的连接
	lb.ReleaseConnection("e1")

	// 第三次选择应该选中 e1（连接数又变成0）
	endpoint3, err := lb.Select(endpoints)
	if err != nil {
		t.Fatalf("Select failed: %v", err)
	}
	if endpoint3.ServiceId != "e1" {
		t.Errorf("Expected e1, got %s", endpoint3.ServiceId)
	}
}

func TestLeastConnectionLoadBalancer_ReleaseConnection(t *testing.T) {
	lb := NewLeastConnectionLoadBalancer()

	endpoints := []*ServiceEndpoint{
		{ServiceId: "e1", Address: "localhost", Port: 8080},
	}

	// 选择端点
	lb.Select(endpoints)
	lb.Select(endpoints)

	// 验证连接数
	if lb.connections["e1"] != 2 {
		t.Errorf("Expected 2 connections, got %d", lb.connections["e1"])
	}

	// 释放连接
	lb.ReleaseConnection("e1")

	// 验证连接数减少
	if lb.connections["e1"] != 1 {
		t.Errorf("Expected 1 connection after release, got %d", lb.connections["e1"])
	}
}

func TestLeastConnectionLoadBalancer_Select_EmptyEndpoints(t *testing.T) {
	lb := NewLeastConnectionLoadBalancer()

	_, err := lb.Select([]*ServiceEndpoint{})
	if err == nil {
		t.Error("Should return error for empty endpoints")
	}
}
