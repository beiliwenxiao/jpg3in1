package client

import (
	"fmt"
	"math"
	"math/rand"
	"net"
	"strings"
	"sync"
	"time"

	"github.com/framework/golang-sdk/serializer"
)

// MonitorRecord 客户端使用的监控记录结构
// 与 server 包的 MonitorRecord 字段一致，但在 client 包中独立定义避免循环依赖
type MonitorRecord struct {
	Project   string      `json:"project"`
	Class     string      `json:"class"`
	Method    string      `json:"method"`
	URI       string      `json:"uri"`
	Status    int         `json:"status"`
	Duration  float64     `json:"duration"`
	Timestamp int64       `json:"timestamp"`
	Params    interface{} `json:"params,omitempty"`
	Response  interface{} `json:"response,omitempty"`
}

// MonitorClient UDP 监控客户端
type MonitorClient struct {
	host       string
	port       int
	project    string
	sampleRate float64
	bufferSize int
	buffer     []string
	mu         sync.Mutex
	conn       net.Conn
	serializer serializer.Serializer
}

// NewMonitorClient 创建客户端实例
func NewMonitorClient(host string, port int, project string, sampleRate float64, bufferSize int) *MonitorClient {
	return &MonitorClient{
		host:       host,
		port:       port,
		project:    project,
		sampleRate: sampleRate,
		bufferSize: bufferSize,
		buffer:     make([]string, 0, bufferSize),
		serializer: serializer.NewJsonSerializer(),
	}
}

// Report 上报一条接口性能数据
func (c *MonitorClient) Report(class, method, uri string, status int, duration float64, params, response interface{}) {
	c.mu.Lock()
	defer c.mu.Unlock()

	// 采样率控制
	if c.sampleRate < 1.0 && rand.Float64() > c.sampleRate {
		return
	}

	record := MonitorRecord{
		Project:   c.project,
		Class:     class,
		Method:    method,
		URI:       uri,
		Status:    status,
		Duration:  math.Round(duration*100) / 100,
		Timestamp: time.Now().Unix(),
	}
	if params != nil {
		record.Params = params
	}
	if response != nil {
		record.Response = response
	}

	data, err := c.serializer.Serialize(record)
	if err != nil {
		return // 序列化失败，跳过
	}

	c.buffer = append(c.buffer, string(data))

	if len(c.buffer) >= c.bufferSize {
		c.flush()
	}
}

// Flush 刷新缓冲区，立即发送所有待发送数据
func (c *MonitorClient) Flush() {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.flush()
}

// flush 内部刷新方法（调用者需持有锁）
func (c *MonitorClient) flush() {
	if len(c.buffer) == 0 {
		return
	}

	payload := strings.Join(c.buffer, "\n")
	c.buffer = c.buffer[:0]

	c.send(payload)
}

// send 通过 UDP 发送数据
func (c *MonitorClient) send(data string) {
	if c.conn == nil {
		addr := fmt.Sprintf("%s:%d", c.host, c.port)
		conn, err := net.Dial("udp", addr)
		if err != nil {
			return // UDP 发送失败静默忽略
		}
		c.conn = conn
	}
	_, _ = c.conn.Write([]byte(data)) // 发送失败静默忽略
}

// Close 关闭客户端，自动 Flush 剩余数据
func (c *MonitorClient) Close() {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.flush()
	if c.conn != nil {
		_ = c.conn.Close()
		c.conn = nil
	}
}

// GetBufferLen 获取当前缓冲区长度（用于测试）
func (c *MonitorClient) GetBufferLen() int {
	c.mu.Lock()
	defer c.mu.Unlock()
	return len(c.buffer)
}
