package mqtt

import (
	"context"
	"testing"
	"time"
)

// TestMqttHandlerCreation 测试 MQTT 处理器创建
func TestMqttHandlerCreation(t *testing.T) {
	config := &MqttConfig{
		Broker:   "localhost",
		Port:     1883,
		ClientId: "test-client",
		Topics:   []string{"test/topic"},
	}
	
	handler := NewMqttProtocolHandler(config)
	if handler == nil {
		t.Fatal("Failed to create MQTT protocol handler")
	}
}

// TestMqttHandlerConfiguration 测试 MQTT 处理器配置
func TestMqttHandlerConfiguration(t *testing.T) {
	config := &MqttConfig{
		Broker:   "localhost",
		Port:     1883,
		ClientId: "test-client-config",
		Username: "testuser",
		Password: "testpass",
		Topics:   []string{"test/topic1", "test/topic2"},
	}
	
	handler := NewMqttProtocolHandler(config)
	if handler == nil {
		t.Fatal("Failed to create MQTT protocol handler")
	}
	
	if handler.config.Broker != config.Broker {
		t.Errorf("Expected broker %s, got %s", config.Broker, handler.config.Broker)
	}
	
	if handler.config.Port != config.Port {
		t.Errorf("Expected port %d, got %d", config.Port, handler.config.Port)
	}
	
	if len(handler.config.Topics) != len(config.Topics) {
		t.Errorf("Expected %d topics, got %d", len(config.Topics), len(handler.config.Topics))
	}
}

// TestMqttHandlerStartStopWithoutBroker 测试没有 Broker 时的启动和停止
func TestMqttHandlerStartStopWithoutBroker(t *testing.T) {
	config := &MqttConfig{
		Broker:   "localhost",
		Port:     1883,
		ClientId: "test-client-no-broker",
		Topics:   []string{"test/topic"},
	}
	
	handler := NewMqttProtocolHandler(config)
	
	// 尝试启动（预期会失败，因为没有 MQTT Broker）
	err := handler.Start()
	if err == nil {
		// 如果成功连接（可能本地有 MQTT Broker），则测试停止
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		
		err = handler.Stop(ctx)
		if err != nil {
			t.Fatalf("Failed to stop MQTT handler: %v", err)
		}
	} else {
		// 预期的失败情况
		t.Logf("Expected failure (no MQTT broker): %v", err)
	}
}

// TestMqttMessageStructure 测试 MQTT 消息结构
func TestMqttMessageStructure(t *testing.T) {
	message := &MqttMessage{
		Topic:    "test/topic",
		Payload:  []byte("test payload"),
		Qos:      1,
		Retained: false,
	}
	
	if message.Topic != "test/topic" {
		t.Errorf("Expected topic 'test/topic', got '%s'", message.Topic)
	}
	
	if string(message.Payload) != "test payload" {
		t.Errorf("Expected payload 'test payload', got '%s'", string(message.Payload))
	}
	
	if message.Qos != 1 {
		t.Errorf("Expected QoS 1, got %d", message.Qos)
	}
	
	if message.Retained {
		t.Error("Expected Retained to be false")
	}
}

// TestMqttPublishWithoutConnection 测试未连接时的发布
func TestMqttPublishWithoutConnection(t *testing.T) {
	config := &MqttConfig{
		Broker:   "localhost",
		Port:     1883,
		ClientId: "test-client-publish",
		Topics:   []string{},
	}
	
	handler := NewMqttProtocolHandler(config)
	
	// 尝试在未连接时发布消息
	err := handler.Publish("test/topic", []byte("test"), 0, false)
	if err == nil {
		t.Error("Expected error when publishing without connection")
	}
}
