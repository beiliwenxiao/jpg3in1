package grpc

import (
	"context"
	"fmt"
	"net"

	"github.com/gogf/gf/v2/os/glog"
	"google.golang.org/grpc"
)

// GrpcServer gRPC 服务器
type GrpcServer struct {
	server   *grpc.Server
	config   *GrpcServerConfig
	listener net.Listener
}

// GrpcServerConfig gRPC 服务器配置
type GrpcServerConfig struct {
	Host string
	Port int
	// TLS 配置（可选）
	UseTLS   bool
	CertFile string
	KeyFile  string
}

// NewGrpcServer 创建 gRPC 服务器
func NewGrpcServer(config *GrpcServerConfig) *GrpcServer {
	return &GrpcServer{
		config: config,
	}
}

// Start 启动 gRPC 服务器
func (s *GrpcServer) Start() error {
	// 创建监听器
	address := fmt.Sprintf("%s:%d", s.config.Host, s.config.Port)
	listener, err := net.Listen("tcp", address)
	if err != nil {
		return fmt.Errorf("failed to listen on %s: %v", address, err)
	}
	
	s.listener = listener
	
	// 配置服务器选项
	opts := []grpc.ServerOption{}
	
	// 配置 TLS
	if s.config.UseTLS {
		// TODO: 实现 TLS 配置
		return fmt.Errorf("TLS not implemented yet")
	}
	
	// 创建 gRPC 服务器
	s.server = grpc.NewServer(opts...)
	
	// TODO: 注册服务
	// pb.RegisterFrameworkServiceServer(s.server, &frameworkServiceImpl{})
	
	glog.Infof(context.Background(), "gRPC server starting on %s", address)
	
	// 启动服务器（阻塞）
	go func() {
		if err := s.server.Serve(listener); err != nil {
			glog.Errorf(context.Background(), "gRPC server error: %v", err)
		}
	}()
	
	return nil
}

// Stop 停止 gRPC 服务器
func (s *GrpcServer) Stop(ctx context.Context) error {
	if s.server != nil {
		// 优雅关闭
		stopped := make(chan struct{})
		go func() {
			s.server.GracefulStop()
			close(stopped)
		}()
		
		// 等待关闭或超时
		select {
		case <-stopped:
			glog.Info(ctx, "gRPC server stopped gracefully")
		case <-ctx.Done():
			// 强制关闭
			s.server.Stop()
			glog.Info(ctx, "gRPC server stopped forcefully")
		}
	}
	
	if s.listener != nil {
		s.listener.Close()
	}
	
	return nil
}

// GetServer 获取底层 gRPC 服务器
func (s *GrpcServer) GetServer() *grpc.Server {
	return s.server
}

// RegisterService 注册服务
func (s *GrpcServer) RegisterService(desc *grpc.ServiceDesc, impl interface{}) {
	if s.server != nil {
		s.server.RegisterService(desc, impl)
	}
}
