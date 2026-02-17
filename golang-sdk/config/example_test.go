package config_test

import (
	"fmt"
	"log"
	"os"

	"github.com/framework/golang-sdk/config"
)

// Example_basicUsage 演示基本的配置使用
func Example_basicUsage() {
	// 创建配置管理器
	cm, err := config.NewConfigManager("config.yaml")
	if err != nil {
		log.Fatalf("Failed to load config: %v", err)
	}

	// 获取配置值
	serviceName := cm.GetString("framework.name")
	port := cm.GetInt("framework.network.port")
	keepAlive := cm.GetBool("framework.network.keepAlive")

	fmt.Printf("Service: %s\n", serviceName)
	fmt.Printf("Port: %d\n", port)
	fmt.Printf("KeepAlive: %v\n", keepAlive)
}

// Example_envOverride 演示环境变量覆盖配置
func Example_envOverride() {
	// 设置环境变量
	os.Setenv("FRAMEWORK_NAME", "my-custom-service")
	os.Setenv("FRAMEWORK_NETWORK_PORT", "9090")
	defer func() {
		os.Unsetenv("FRAMEWORK_NAME")
		os.Unsetenv("FRAMEWORK_NETWORK_PORT")
	}()

	// 创建配置管理器
	cm, err := config.NewConfigManager("config.yaml")
	if err != nil {
		log.Fatalf("Failed to load config: %v", err)
	}

	// 环境变量会覆盖配置文件中的值
	serviceName := cm.GetString("framework.name")
	port := cm.GetInt("framework.network.port")

	fmt.Printf("Service from env: %s\n", serviceName)
	fmt.Printf("Port from env: %d\n", port)
}

// Example_structuredConfig 演示加载结构化配置
func Example_structuredConfig() {
	// 创建配置管理器
	cm, err := config.NewConfigManager("config.yaml")
	if err != nil {
		log.Fatalf("Failed to load config: %v", err)
	}

	// 加载完整的框架配置
	frameworkConfig, err := cm.LoadFrameworkConfig()
	if err != nil {
		log.Fatalf("Failed to load framework config: %v", err)
	}

	// 访问结构化配置
	fmt.Printf("Service: %s v%s\n", frameworkConfig.Name, frameworkConfig.Version)
	fmt.Printf("Language: %s\n", frameworkConfig.Language)
	fmt.Printf("Network Host: %s\n", frameworkConfig.Network.Host)
	fmt.Printf("Network Port: %d\n", frameworkConfig.Network.Port)
	fmt.Printf("Registry Type: %s\n", frameworkConfig.Registry.Type)
	fmt.Printf("Registry Endpoints: %v\n", frameworkConfig.Registry.Endpoints)
	fmt.Printf("Connection Pool Max: %d\n", frameworkConfig.ConnectionPool.MaxConnections)
	fmt.Printf("Log Level: %s\n", frameworkConfig.Observability.Logging.Level)
}

// Example_defaultValues 演示使用默认值
func Example_defaultValues() {
	cm, err := config.NewConfigManager("config.yaml")
	if err != nil {
		log.Fatalf("Failed to load config: %v", err)
	}

	// 获取不存在的配置项，使用默认值
	customValue := cm.GetString("custom.nonexistent.key", "default-value")
	customPort := cm.GetInt("custom.nonexistent.port", 8080)
	customEnabled := cm.GetBool("custom.nonexistent.enabled", true)

	fmt.Printf("Custom Value: %s\n", customValue)
	fmt.Printf("Custom Port: %d\n", customPort)
	fmt.Printf("Custom Enabled: %v\n", customEnabled)
}

// Example_multipleEnvironments 演示多环境配置
func Example_multipleEnvironments() {
	// 根据环境变量选择配置文件
	env := os.Getenv("ENV")
	if env == "" {
		env = "dev"
	}

	configFile := fmt.Sprintf("config.%s.yaml", env)
	
	// 如果环境特定的配置文件不存在，使用默认配置
	if _, err := os.Stat(configFile); os.IsNotExist(err) {
		configFile = "config.yaml"
	}

	cm, err := config.NewConfigManager(configFile)
	if err != nil {
		log.Fatalf("Failed to load config: %v", err)
	}

	fmt.Printf("Loaded config from: %s\n", configFile)
	fmt.Printf("Service: %s\n", cm.GetString("framework.name"))
}
