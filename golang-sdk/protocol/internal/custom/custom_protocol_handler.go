package custom

import (
	"context"
	"encoding/binary"
	"fmt"
	"io"
	"net"
	"sync"

	"github.com/gogf/gf/v2/os/glog"
)

// CustomProtocolHandler 自定义协议处理器
type CustomProtocolHandler struct {
	listener net.Listener
	config   *CustomProtocolConfig
	handlers map[string]MessageHandler
	mu       sync.RWMutex
	stopChan chan struct{}
}

// CustomProtocolConfig 自定义协议配置
type CustomProtocolConfig struct {
	Host string
	Port int
}

// MessageHandler 消息处理器
type MessageHandler func(ctx context.Context, frame *CustomFrame) (*CustomFrame, error)

// NewCustomProtocolHandler 创建自定义协议处理器
func NewCustomProtocolHandler(config *CustomProtocolConfig) *CustomProtocolHandler {
	return &CustomProtocolHandler{
		config:   config,
		handlers: make(map[string]MessageHandler),
		stopChan: make(chan struct{}),
	}
}

// Start 启动自定义协议服务器
func (h *CustomProtocolHandler) Start() error {
	address := fmt.Sprintf("%s:%d", h.config.Host, h.config.Port)
	
	listener, err := net.Listen("tcp", address)
	if err != nil {
		return fmt.Errorf("failed to listen on %s: %v", address, err)
	}
	
	h.listener = listener
	glog.Infof(context.Background(), "Custom protocol server listening on %s", address)
	
	// 接受连接
	go h.acceptConnections()
	
	return nil
}

// Stop 停止自定义协议服务器
func (h *CustomProtocolHandler) Stop(ctx context.Context) error {
	close(h.stopChan)
	
	if h.listener != nil {
		h.listener.Close()
	}
	
	glog.Info(ctx, "Custom protocol server stopped")
	return nil
}

// RegisterHandler 注册消息处理器
func (h *CustomProtocolHandler) RegisterHandler(frameType FrameType, handler MessageHandler) {
	h.mu.Lock()
	defer h.mu.Unlock()
	
	h.handlers[frameType.String()] = handler
}

// acceptConnections 接受连接
func (h *CustomProtocolHandler) acceptConnections() {
	for {
		select {
		case <-h.stopChan:
			return
		default:
			conn, err := h.listener.Accept()
			if err != nil {
				select {
				case <-h.stopChan:
					return
				default:
					glog.Errorf(context.Background(), "Failed to accept connection: %v", err)
					continue
				}
			}
			
			// 处理连接
			go h.handleConnection(conn)
		}
	}
}

// handleConnection 处理连接
func (h *CustomProtocolHandler) handleConnection(conn net.Conn) {
	defer conn.Close()
	
	ctx := context.Background()
	
	for {
		// 读取帧
		frame, err := h.readFrame(conn)
		if err != nil {
			if err != io.EOF {
				glog.Errorf(ctx, "Failed to read frame: %v", err)
			}
			return
		}
		
		// 查找处理器
		h.mu.RLock()
		handler, exists := h.handlers[frame.Header.Type.String()]
		h.mu.RUnlock()
		
		if !exists {
			// 没有找到对应的处理器，跳过该帧
			continue
		}
		
		// 调用处理器
		response, err := handler(ctx, frame)
		if err != nil {
			glog.Errorf(ctx, "Handler error: %v", err)
			continue
		}
		
		// 发送响应
		if response != nil {
			if err := h.writeFrame(conn, response); err != nil {
				glog.Errorf(ctx, "Failed to write response: %v", err)
				return
			}
		}
	}
}

// readFrame 读取帧
func (h *CustomProtocolHandler) readFrame(conn net.Conn) (*CustomFrame, error) {
	// 读取帧头
	header := &FrameHeader{}
	
	// 读取魔数
	if err := binary.Read(conn, binary.BigEndian, &header.Magic); err != nil {
		return nil, err
	}
	
	// 验证魔数
	if header.Magic != MagicNumber {
		return nil, fmt.Errorf("invalid magic number: 0x%X", header.Magic)
	}
	
	// 读取版本
	if err := binary.Read(conn, binary.BigEndian, &header.Version); err != nil {
		return nil, err
	}
	
	// 读取帧类型
	var frameType uint32
	if err := binary.Read(conn, binary.BigEndian, &frameType); err != nil {
		return nil, err
	}
	header.Type = FrameType(frameType)
	
	// 读取标志
	if err := binary.Read(conn, binary.BigEndian, &header.Flags); err != nil {
		return nil, err
	}
	
	// 读取流 ID
	if err := binary.Read(conn, binary.BigEndian, &header.StreamId); err != nil {
		return nil, err
	}
	
	// 读取帧体长度
	if err := binary.Read(conn, binary.BigEndian, &header.BodyLength); err != nil {
		return nil, err
	}
	
	// 读取序列号
	if err := binary.Read(conn, binary.BigEndian, &header.Sequence); err != nil {
		return nil, err
	}
	
	// 读取时间戳
	if err := binary.Read(conn, binary.BigEndian, &header.Timestamp); err != nil {
		return nil, err
	}
	
	// 读取帧体
	body := make([]byte, header.BodyLength)
	if _, err := io.ReadFull(conn, body); err != nil {
		return nil, err
	}
	
	return &CustomFrame{
		Header: header,
		Body:   body,
	}, nil
}

// writeFrame 写入帧
func (h *CustomProtocolHandler) writeFrame(conn net.Conn, frame *CustomFrame) error {
	// 写入帧头
	if err := binary.Write(conn, binary.BigEndian, frame.Header.Magic); err != nil {
		return err
	}
	
	if err := binary.Write(conn, binary.BigEndian, frame.Header.Version); err != nil {
		return err
	}
	
	if err := binary.Write(conn, binary.BigEndian, uint32(frame.Header.Type)); err != nil {
		return err
	}
	
	if err := binary.Write(conn, binary.BigEndian, frame.Header.Flags); err != nil {
		return err
	}
	
	if err := binary.Write(conn, binary.BigEndian, frame.Header.StreamId); err != nil {
		return err
	}
	
	if err := binary.Write(conn, binary.BigEndian, frame.Header.BodyLength); err != nil {
		return err
	}
	
	if err := binary.Write(conn, binary.BigEndian, frame.Header.Sequence); err != nil {
		return err
	}
	
	if err := binary.Write(conn, binary.BigEndian, frame.Header.Timestamp); err != nil {
		return err
	}
	
	// 写入帧体
	if _, err := conn.Write(frame.Body); err != nil {
		return err
	}
	
	return nil
}

// CustomFrame 自定义协议帧
type CustomFrame struct {
	Header *FrameHeader
	Body   []byte
}

// FrameHeader 帧头
type FrameHeader struct {
	Magic      uint32    // 魔数：0x46524D57 ("FRMW")
	Version    uint32    // 协议版本
	Type       FrameType // 帧类型
	Flags      uint32    // 帧标志
	StreamId   uint32    // 流 ID
	BodyLength uint32    // 帧体长度
	Sequence   uint64    // 序列号
	Timestamp  int64     // 时间戳
}

// FrameType 帧类型
type FrameType uint32

const (
	FrameTypeData         FrameType = 1
	FrameTypePing         FrameType = 2
	FrameTypePong         FrameType = 3
	FrameTypeClose        FrameType = 4
	FrameTypeWindowUpdate FrameType = 5
	FrameTypeSettings     FrameType = 6
	FrameTypeError        FrameType = 7
	FrameTypeMetadata     FrameType = 8
)

func (t FrameType) String() string {
	switch t {
	case FrameTypeData:
		return "DATA"
	case FrameTypePing:
		return "PING"
	case FrameTypePong:
		return "PONG"
	case FrameTypeClose:
		return "CLOSE"
	case FrameTypeWindowUpdate:
		return "WINDOW_UPDATE"
	case FrameTypeSettings:
		return "SETTINGS"
	case FrameTypeError:
		return "ERROR"
	case FrameTypeMetadata:
		return "METADATA"
	default:
		return "UNKNOWN"
	}
}

// MagicNumber 魔数
const MagicNumber uint32 = 0x46524D57 // "FRMW"

// CustomProtocolClient 自定义协议客户端
type CustomProtocolClient struct {
	conn   net.Conn
	config *CustomProtocolConfig
}

// NewCustomProtocolClient 创建自定义协议客户端
func NewCustomProtocolClient(config *CustomProtocolConfig) *CustomProtocolClient {
	return &CustomProtocolClient{
		config: config,
	}
}

// Connect 连接到服务器
func (c *CustomProtocolClient) Connect() error {
	address := fmt.Sprintf("%s:%d", c.config.Host, c.config.Port)
	
	conn, err := net.Dial("tcp", address)
	if err != nil {
		return fmt.Errorf("failed to connect to %s: %v", address, err)
	}
	
	c.conn = conn
	return nil
}

// Close 关闭连接
func (c *CustomProtocolClient) Close() error {
	if c.conn != nil {
		return c.conn.Close()
	}
	return nil
}

// SendFrame 发送帧
func (c *CustomProtocolClient) SendFrame(frame *CustomFrame) error {
	if c.conn == nil {
		return fmt.Errorf("client not connected")
	}
	
	handler := &CustomProtocolHandler{}
	return handler.writeFrame(c.conn, frame)
}

// ReceiveFrame 接收帧
func (c *CustomProtocolClient) ReceiveFrame() (*CustomFrame, error) {
	if c.conn == nil {
		return nil, fmt.Errorf("client not connected")
	}
	
	handler := &CustomProtocolHandler{}
	return handler.readFrame(c.conn)
}
