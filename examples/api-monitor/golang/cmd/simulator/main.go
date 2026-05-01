package main

import (
	"fmt"
	"math/rand"
	"os"
	"os/signal"
	"syscall"
	"time"

	"gopkg.in/yaml.v3"

	"api-monitor-golang/internal/client"
)

// SimApi 模拟 API 定义
type SimApi struct {
	Class   string
	Method  string
	URI     string
	MinTime float64
	MaxTime float64
}

// Config 配置（只需要 monitor 部分）
type Config struct {
	Monitor struct {
		UdpHost    string  `yaml:"udp_host"`
		UdpPort    int     `yaml:"udp_port"`
		Project    string  `yaml:"project"`
		SampleRate float64 `yaml:"sample_rate"`
		BufferSize int     `yaml:"buffer_size"`
	} `yaml:"monitor"`
}

func main() {
	fmt.Println("========================================")
	fmt.Println("  API 监控 - Golang 模拟客户端")
	fmt.Println("========================================")

	// 读取配置
	cfg := loadConfig("config.yaml")
	fmt.Printf("目标: UDP %s:%d\n", cfg.Monitor.UdpHost, cfg.Monitor.UdpPort)
	fmt.Printf("项目: %s\n\n", cfg.Monitor.Project)

	// 创建 MonitorClient
	mc := client.NewMonitorClient(
		cfg.Monitor.UdpHost,
		cfg.Monitor.UdpPort,
		cfg.Monitor.Project,
		cfg.Monitor.SampleRate,
		cfg.Monitor.BufferSize,
	)

	// 15 个模拟 API（与 PHP 版 simulate.php 完全一致）
	apis := []SimApi{
		{"UserController", "index", "/api/user", 10, 50},
		{"UserController", "show", "/api/user/1", 20, 80},
		{"UserController", "store", "/api/user", 30, 150},
		{"OrderController", "index", "/api/order", 15, 60},
		{"OrderController", "create", "/api/order", 50, 300},
		{"OrderController", "pay", "/api/order/pay", 100, 2000},
		{"ProductController", "list", "/api/product", 5, 30},
		{"ProductController", "detail", "/api/product/1", 10, 40},
		{"ProductController", "search", "/api/product/search", 20, 500},
		{"AuthController", "login", "/api/auth/login", 30, 200},
		{"AuthController", "logout", "/api/auth/logout", 5, 20},
		{"ReportController", "daily", "/api/report/daily", 200, 3000},
		{"ReportController", "export", "/api/report/export", 500, 5000},
		{"CartController", "add", "/api/cart/add", 10, 60},
		{"CartController", "list", "/api/cart", 8, 35},
	}

	// 状态码加权概率（与 PHP 版完全一致）
	statusWeights := []struct {
		Status int
		Weight int
	}{
		{200, 85}, // 85% 成功
		{201, 5},  // 5% 创建成功
		{400, 3},  // 3% 参数错误
		{401, 2},  // 2% 未授权
		{404, 2},  // 2% 未找到
		{500, 3},  // 3% 服务器错误
	}

	// 信号处理：监听 SIGINT/SIGTERM，优雅退出
	sigCh := make(chan os.Signal, 1)
	signal.Notify(sigCh, syscall.SIGINT, syscall.SIGTERM)

	count := 0
	fmt.Println("开始发送模拟数据（Ctrl+C 停止）...\n")

	for {
		select {
		case <-sigCh:
			fmt.Printf("\n\n[模拟器] 收到退出信号，正在退出...\n")
			mc.Flush()
			mc.Close()
			fmt.Printf("[模拟器] 已发送 %d 条数据，退出完成\n", count)
			return
		default:
		}

		// 每轮随机发送 1~5 条
		batch := rand.Intn(5) + 1
		for i := 0; i < batch; i++ {
			api := apis[rand.Intn(len(apis))]
			duration := api.MinTime + rand.Float64()*(api.MaxTime-api.MinTime)
			status := randomStatus(statusWeights)

			// 模拟请求参数
			params := map[string]interface{}{
				"page": rand.Intn(10) + 1,
				"id":   rand.Intn(1000) + 1,
			}
			// 模拟响应
			response := map[string]interface{}{
				"code": 0,
				"msg":  "ok",
			}
			if status >= 400 {
				response["code"] = 1
				response["msg"] = "error"
			}

			mc.Report(api.Class, api.Method, api.URI, status, duration, params, response)
			count++

			// 彩色输出：成功绿色、失败红色
			statusColor := "\033[32m" // 绿色
			if status >= 400 {
				statusColor = "\033[31m" // 红色
			}
			fmt.Printf("[%s] #%d %s%d\033[0m %s.%s %.1fms\n",
				time.Now().Format("15:04:05"), count, statusColor, status,
				api.Class, api.Method, duration)
		}

		mc.Flush()

		// 随机间隔 100ms~1000ms
		time.Sleep(time.Duration(100+rand.Intn(900)) * time.Millisecond)
	}
}

// randomStatus 按加权概率随机选择状态码
func randomStatus(weights []struct{ Status, Weight int }) int {
	total := 0
	for _, w := range weights {
		total += w.Weight
	}
	r := rand.Intn(total) + 1
	sum := 0
	for _, w := range weights {
		sum += w.Weight
		if r <= sum {
			return w.Status
		}
	}
	return 200
}

// loadConfig 从 YAML 文件加载配置，文件不存在时使用默认值
func loadConfig(path string) Config {
	cfg := Config{}
	// 默认值
	cfg.Monitor.UdpHost = "127.0.0.1"
	cfg.Monitor.UdpPort = 9501
	cfg.Monitor.Project = "demo-project"
	cfg.Monitor.SampleRate = 1.0
	cfg.Monitor.BufferSize = 10

	data, err := os.ReadFile(path)
	if err != nil {
		fmt.Printf("[模拟器] 配置文件 %s 不存在，使用默认配置\n", path)
		return cfg
	}
	_ = yaml.Unmarshal(data, &cfg)
	return cfg
}
