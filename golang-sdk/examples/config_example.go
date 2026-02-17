package main

import (
	"fmt"
	"log"
	"os"

	"github.com/framework/golang-sdk/config"
)

func main() {
	fmt.Println("=== Golang SDK 配置管理示例 ===\n")

	// 示例 1: 基本配置加载
	fmt.Println("1. 基本配置加载:")
	basicConfigExample()

	// 示例 2: 环境变量覆盖
	fmt.Println("\n2. 环境变量覆盖配置:")
	envOverrideExample()

	// 示例 3: 结构化配置
	fmt.Println("\n3. 结构化配置对象:")
	structuredConfigExample()

	// 示例 4: 配置验证
	fmt.Println("\n4. 配置验证:")
	validationExample()
}

func basicConfigExample() {
	// 加载配置文件
	cm, err := config.NewConfigManager("../config/config.yaml")
	if err != nil {
		log.Fatalf("加载配置失败: %v", err)
	}

	// 获取各种类型的配置
	serviceName := cm.GetString("framework.name")
	port := cm.GetInt("framework.network.port")
	keepAlive := cm.GetBool("framework.network.keepAlive")
	readTimeout := cm.GetDuration("framework.network.readTimeout")
	endpoints := cm.GetStringSlice("framework.registry.endpoints")

	fmt.Printf("  服务名称: %s\n", serviceName)
	fmt.Printf("  端口: %d\n", port)
	fmt.Printf("  保持连接: %v\n", keepAlive)
	fmt.Printf("  读取超时: %v\n", readTimeout)
	fmt.Printf("  注册中心端点: %v\n", endpoints)
}

func envOverrideExample() {
	// 设置环境变量
	os.Setenv("FRAMEWORK_NAME", "custom-service-from-env")
	os.Setenv("FRAMEWORK_NETWORK_PORT", "9999")
	os.Setenv("FRAMEWORK_NETWORK_KEEPALIVE", "false")
	defer func() {
		os.Unsetenv("FRAMEWORK_NAME")
		os.Unsetenv("FRAMEWORK_NETWORK_PORT")
		os.Unsetenv("FRAMEWORK_NETWORK_KEEPALIVE")
	}()

	cm, err := config.NewConfigManager("../config/config.yaml")
	if err != nil {
		log.Fatalf("加载配置失败: %v", err)
	}

	// 环境变量会覆盖配置文件中的值
	serviceName := cm.GetString("framework.name")
	port := cm.GetInt("framework.network.port")
	keepAlive := cm.GetBool("framework.network.keepAlive")

	fmt.Printf("  服务名称 (来自环境变量): %s\n", serviceName)
	fmt.Printf("  端口 (来自环境变量): %d\n", port)
	fmt.Printf("  保持连接 (来自环境变量): %v\n", keepAlive)
}

func structuredConfigExample() {
	cm, err := config.NewConfigManager("../config/config.yaml")
	if err != nil {
		log.Fatalf("加载配置失败: %v", err)
	}

	// 加载完整的框架配置
	frameworkConfig, err := cm.LoadFrameworkConfig()
	if err != nil {
		log.Fatalf("加载框架配置失败: %v", err)
	}

	// 访问结构化配置
	fmt.Printf("  服务: %s v%s (%s)\n", 
		frameworkConfig.Name, 
		frameworkConfig.Version, 
		frameworkConfig.Language)
	
	fmt.Printf("  网络配置:\n")
	fmt.Printf("    - 主机: %s:%d\n", 
		frameworkConfig.Network.Host, 
		frameworkConfig.Network.Port)
	fmt.Printf("    - 最大连接数: %d\n", 
		frameworkConfig.Network.MaxConnections)
	
	fmt.Printf("  注册中心配置:\n")
	fmt.Printf("    - 类型: %s\n", frameworkConfig.Registry.Type)
	fmt.Printf("    - 端点: %v\n", frameworkConfig.Registry.Endpoints)
	fmt.Printf("    - TTL: %d秒\n", frameworkConfig.Registry.TTL)
	
	fmt.Printf("  连接池配置:\n")
	fmt.Printf("    - 最大连接数: %d\n", 
		frameworkConfig.ConnectionPool.MaxConnections)
	fmt.Printf("    - 最小连接数: %d\n", 
		frameworkConfig.ConnectionPool.MinConnections)
	fmt.Printf("    - 空闲超时: %v\n", 
		frameworkConfig.ConnectionPool.IdleTimeout)
	
	fmt.Printf("  可观测性配置:\n")
	fmt.Printf("    - 日志级别: %s\n", 
		frameworkConfig.Observability.Logging.Level)
	fmt.Printf("    - 指标启用: %v\n", 
		frameworkConfig.Observability.Metrics.Enabled)
	fmt.Printf("    - 追踪启用: %v\n", 
		frameworkConfig.Observability.Tracing.Enabled)
}

func validationExample() {
	// 测试有效配置
	fmt.Println("  测试有效配置:")
	_, err := config.NewConfigManager("../config/config.yaml")
	if err != nil {
		fmt.Printf("    ✗ 配置验证失败: %v\n", err)
	} else {
		fmt.Println("    ✓ 配置验证通过")
	}

	// 测试无效配置（通过环境变量）
	fmt.Println("\n  测试无效配置 (端口超出范围):")
	os.Setenv("FRAMEWORK_NETWORK_PORT", "99999")
	defer os.Unsetenv("FRAMEWORK_NETWORK_PORT")

	_, err = config.NewConfigManager("../config/config.yaml")
	if err != nil {
		fmt.Printf("    ✓ 配置验证正确捕获错误: %v\n", err)
	} else {
		fmt.Println("    ✗ 配置验证应该失败但通过了")
	}
}
