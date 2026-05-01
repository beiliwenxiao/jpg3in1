package main

import (
	"context"
	"fmt"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"

	"github.com/gogf/gf/v2/frame/g"
	"gopkg.in/yaml.v3"

	"github.com/framework/golang-sdk/observability"

	"api-monitor-golang/internal/server"
)

// Config 配置文件结构
type Config struct {
	Monitor MonitorConfig `yaml:"monitor"`
	Server  ServerConfig  `yaml:"server"`
}

// MonitorConfig 客户端配置
type MonitorConfig struct {
	Enabled    bool     `yaml:"enabled"`
	UdpHost    string   `yaml:"udp_host"`
	UdpPort    int      `yaml:"udp_port"`
	Project    string   `yaml:"project"`
	SampleRate float64  `yaml:"sample_rate"`
	BufferSize int      `yaml:"buffer_size"`
	Exclude    []string `yaml:"exclude"`
}

// ServerConfig 服务端配置
type ServerConfig struct {
	HttpPort      int       `yaml:"http_port"`
	UdpPort       int       `yaml:"udp_port"`
	StorageDriver string    `yaml:"storage_driver"`
	Password      string    `yaml:"password"`
	Log           LogConfig `yaml:"log"`
}

// LogConfig 日志持久化配置
type LogConfig struct {
	Enable   bool `yaml:"enable"`
	Interval int  `yaml:"interval"`
}

func main() {
	fmt.Println("========================================")
	fmt.Println("  API 性能监控 - Golang 服务端")
	fmt.Println("========================================")

	// 1. 读取配置
	cfg := loadConfig("config.yaml")

	// 2. 初始化日志
	logger := observability.NewLogger("api-monitor")

	// 3. 初始化存储引擎
	storage := server.NewMemoryStorage()
	fmt.Println("[监控] 使用内存存储")

	// 4. 初始化日志持久化
	logDir := filepath.Join("runtime", "logs", "monitor")
	monitorLogger := server.NewMonitorLogger(storage, logDir, cfg.Server.Log.Interval, logger)

	// 5. 初始化 UDP 接收器
	udpReceiver := server.NewUdpReceiver(cfg.Server.UdpPort, storage, logger)

	// 6. 初始化 HTTP 控制器
	publicDir := "public"
	httpController := server.NewHttpController(storage, monitorLogger, cfg.Server.Password, publicDir)

	// 7. 启动 UDP 接收器（goroutine）
	ctx, cancel := context.WithCancel(context.Background())
	go func() {
		if err := udpReceiver.Start(ctx); err != nil {
			fmt.Printf("[监控] UDP 接收器错误: %v\n", err)
		}
	}()

	// 8. 启动日志持久化（如果配置开启）
	if cfg.Server.Log.Enable {
		monitorLogger.Start()
		fmt.Printf("[监控] 日志持久化已启动，间隔 %d 秒\n", cfg.Server.Log.Interval)
	}

	// 9. 配置 GoFrame HTTP 服务器
	s := g.Server()
	s.SetPort(cfg.Server.HttpPort)
	httpController.RegisterRoutes(s)

	// 10. 注册优雅退出钩子
	// GoFrame 的 Run() 内部会监听 SIGINT/SIGTERM 并优雅关闭 HTTP 服务器
	// 我们在 Run() 之前注册信号处理，在 HTTP 服务器关闭前完成清理工作
	go gracefulShutdown(cancel, udpReceiver, monitorLogger, cfg.Server.Log.Enable)

	fmt.Printf("[监控] HTTP 服务启动，端口 %d\n", cfg.Server.HttpPort)
	fmt.Printf("[监控] UDP 接收端口 %d\n", cfg.Server.UdpPort)
	fmt.Printf("[监控] 仪表盘地址: http://localhost:%d\n", cfg.Server.HttpPort)

	// 启动 HTTP 服务器（阻塞，GoFrame 内部处理信号退出）
	s.Run()
}

// gracefulShutdown 监听退出信号，执行清理工作
func gracefulShutdown(cancel context.CancelFunc, udpReceiver *server.UdpReceiver, monitorLogger *server.MonitorLogger, logEnabled bool) {
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)
	<-sigCh

	fmt.Println("\n[监控] 收到退出信号，正在优雅退出...")

	// 停止 UDP 接收器
	cancel()
	udpReceiver.Stop()
	fmt.Println("[监控] UDP 接收器已停止")

	// 最后一次日志导出
	if logEnabled {
		monitorLogger.Stop()
		fmt.Println("[监控] 日志持久化已停止，最后一次导出完成")
	}

	// GoFrame 的 Run() 会自行处理 HTTP 服务器的优雅关闭
	fmt.Println("[监控] 清理完成，等待 HTTP 服务器关闭...")
}

// loadConfig 读取 YAML 配置文件
func loadConfig(path string) Config {
	// 默认配置
	cfg := Config{
		Monitor: MonitorConfig{
			Enabled:    true,
			UdpHost:    "127.0.0.1",
			UdpPort:    9501,
			Project:    "demo-project",
			SampleRate: 1.0,
			BufferSize: 10,
			Exclude:    []string{"/health", "/favicon.ico"},
		},
		Server: ServerConfig{
			HttpPort:      8095,
			UdpPort:       9501,
			StorageDriver: "memory",
			Password:      "888888",
			Log: LogConfig{
				Enable:   true,
				Interval: 60,
			},
		},
	}

	data, err := os.ReadFile(path)
	if err != nil {
		fmt.Printf("[监控] 配置文件 %s 不存在，使用默认配置\n", path)
		return cfg
	}

	if err := yaml.Unmarshal(data, &cfg); err != nil {
		fmt.Printf("[监控] 配置文件解析失败: %v，使用默认配置\n", err)
	}

	return cfg
}
