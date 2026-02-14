package mqtt

import (
	"context"
	"fmt"
	"time"

	mqtt "github.com/eclipse/paho.mqtt.golang"
	"github.com/gogf/gf/v2/os/glog"
)

// MqttProtocolHandler MQTT 协议处理器
type MqttProtocolHandler struct {
	client mqtt.Client
	config *MqttConfig
}

// MqttConfig MQTT 配置
type MqttConfig struct {
	Broker   string
	Port     int
	ClientId string
	Username string
	Password string
	Topics   []string
}

// NewMqttProtocolHandler 创建 MQTT 协议处理器
func NewMqttProtocolHandler(config *MqttConfig) *MqttProtocolHandler {
	return &MqttProtocolHandler{
		config: config,
	}
}

// Start 启动 MQTT 客户端
func (h *MqttProtocolHandler) Start() error {
	// 配置 MQTT 客户端选项
	opts := mqtt.NewClientOptions()
	opts.AddBroker(fmt.Sprintf("tcp://%s:%d", h.config.Broker, h.config.Port))
	opts.SetClientID(h.config.ClientId)
	
	if h.config.Username != "" {
		opts.SetUsername(h.config.Username)
	}
	
	if h.config.Password != "" {
		opts.SetPassword(h.config.Password)
	}
	
	// 设置连接丢失处理器
	opts.SetConnectionLostHandler(h.onConnectionLost)
	
	// 设置连接成功处理器
	opts.SetOnConnectHandler(h.onConnect)
	
	// 创建客户端
	h.client = mqtt.NewClient(opts)
	
	// 连接到 MQTT Broker
	if token := h.client.Connect(); token.Wait() && token.Error() != nil {
		return fmt.Errorf("failed to connect to MQTT broker: %v", token.Error())
	}
	
	glog.Info(context.Background(), "MQTT client connected")
	
	// 订阅主题
	for _, topic := range h.config.Topics {
		if token := h.client.Subscribe(topic, 0, h.handleMessage); token.Wait() && token.Error() != nil {
			return fmt.Errorf("failed to subscribe to topic %s: %v", topic, token.Error())
		}
		glog.Infof(context.Background(), "Subscribed to topic: %s", topic)
	}
	
	return nil
}

// Stop 停止 MQTT 客户端
func (h *MqttProtocolHandler) Stop(ctx context.Context) error {
	if h.client != nil && h.client.IsConnected() {
		// 取消订阅所有主题
		for _, topic := range h.config.Topics {
			if token := h.client.Unsubscribe(topic); token.Wait() && token.Error() != nil {
				glog.Errorf(ctx, "Failed to unsubscribe from topic %s: %v", topic, token.Error())
			}
		}
		
		// 断开连接
		h.client.Disconnect(250)
		glog.Info(ctx, "MQTT client disconnected")
	}
	
	return nil
}

// handleMessage 处理 MQTT 消息
func (h *MqttProtocolHandler) handleMessage(client mqtt.Client, msg mqtt.Message) {
	ctx := context.Background()
	
	glog.Infof(ctx, "Received MQTT message on topic %s: %s", msg.Topic(), string(msg.Payload()))
	
	// 创建消息对象
	message := &MqttMessage{
		Topic:   msg.Topic(),
		Payload: msg.Payload(),
		Qos:     msg.Qos(),
		Retained: msg.Retained(),
	}
	
	// TODO: 调用协议适配器转换请求
	// TODO: 调用消息路由器路由到目标服务
	// TODO: 处理响应（如果需要）
	
	_ = message
}

// Publish 发布 MQTT 消息
func (h *MqttProtocolHandler) Publish(topic string, payload []byte, qos byte, retained bool) error {
	if h.client == nil || !h.client.IsConnected() {
		return fmt.Errorf("MQTT client is not connected")
	}
	
	token := h.client.Publish(topic, qos, retained, payload)
	token.Wait()
	
	return token.Error()
}

// onConnect 连接成功回调
func (h *MqttProtocolHandler) onConnect(client mqtt.Client) {
	glog.Info(context.Background(), "MQTT client connected successfully")
}

// onConnectionLost 连接丢失回调
func (h *MqttProtocolHandler) onConnectionLost(client mqtt.Client, err error) {
	glog.Errorf(context.Background(), "MQTT connection lost: %v", err)
	
	// TODO: 实现重连逻辑
	time.Sleep(5 * time.Second)
}

// MqttMessage MQTT 消息
type MqttMessage struct {
	Topic    string
	Payload  []byte
	Qos      byte
	Retained bool
}
