package server

import (
	"context"
	"encoding/json"
	"fmt"
	"net"
	"strings"

	"github.com/framework/golang-sdk/observability"
)

// UdpReceiver UDP 数据接收器
// 监听 UDP 端口，接收客户端发送的 JSON 性能数据，写入 StorageInterface
type UdpReceiver struct {
	port    int
	storage StorageInterface
	conn    *net.UDPConn
	logger  observability.Logger
}

// NewUdpReceiver 创建 UDP 接收器
func NewUdpReceiver(port int, storage StorageInterface, logger observability.Logger) *UdpReceiver {
	return &UdpReceiver{
		port:    port,
		storage: storage,
		logger:  logger,
	}
}

// Start 启动 UDP 监听（阻塞，应在 goroutine 中调用）
func (r *UdpReceiver) Start(ctx context.Context) error {
	addr := &net.UDPAddr{
		IP:   net.IPv4(0, 0, 0, 0),
		Port: r.port,
	}
	conn, err := net.ListenUDP("udp", addr)
	if err != nil {
		return fmt.Errorf("UDP 监听失败: %w", err)
	}
	r.conn = conn

	fmt.Printf("[监控] UDP 接收器启动，监听端口 %d\n", r.port)
	if r.logger != nil {
		r.logger.Info(ctx, fmt.Sprintf("UDP 接收器启动，监听端口 %d", r.port))
	}

	buf := make([]byte, 65535) // UDP 最大包大小
	for {
		select {
		case <-ctx.Done():
			return nil
		default:
		}

		n, _, err := conn.ReadFromUDP(buf)
		if err != nil {
			// 连接关闭时正常退出
			if r.conn == nil {
				return nil
			}
			continue
		}

		if n > 0 {
			data := make([]byte, n)
			copy(data, buf[:n])
			go r.handleMessage(data)
		}
	}
}

// Stop 停止 UDP 监听
func (r *UdpReceiver) Stop() {
	if r.conn != nil {
		conn := r.conn
		r.conn = nil
		_ = conn.Close()
	}
}

// handleMessage 处理接收到的 UDP 数据
// 按 \n 分隔逐行解析 JSON 为 MonitorRecord，写入 storage
func (r *UdpReceiver) handleMessage(data []byte) {
	lines := strings.Split(strings.TrimSpace(string(data)), "\n")

	for _, line := range lines {
		line = strings.TrimSpace(line)
		if line == "" {
			continue
		}

		var record MonitorRecord
		if err := json.Unmarshal([]byte(line), &record); err != nil {
			continue // JSON 解析失败，跳过
		}

		// 验证必要字段
		if record.Project == "" || record.Class == "" || record.Method == "" || record.URI == "" {
			continue // 缺少必要字段，跳过
		}

		r.storage.Record(record)
	}
}

// HandleMessageForTest 暴露 handleMessage 供测试使用
func (r *UdpReceiver) HandleMessageForTest(data []byte) {
	r.handleMessage(data)
}
