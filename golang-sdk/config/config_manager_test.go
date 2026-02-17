package config

import (
	"os"
	"testing"
	"time"
)

func TestNewConfigManager(t *testing.T) {
	// 测试加载有效配置文件
	cm, err := NewConfigManager("config.yaml")
	if err != nil {
		t.Fatalf("Failed to create config manager: %v", err)
	}

	if cm == nil {
		t.Fatal("Config manager should not be nil")
	}

	// 验证基础配置
	if cm.GetString("framework.name") == "" {
		t.Error("framework.name should not be empty")
	}

	if cm.GetString("framework.version") == "" {
		t.Error("framework.version should not be empty")
	}

	if cm.GetString("framework.language") != "golang" {
		t.Errorf("Expected language 'golang', got '%s'", cm.GetString("framework.language"))
	}
}

func TestNewConfigManager_InvalidPath(t *testing.T) {
	// 测试加载不存在的配置文件
	_, err := NewConfigManager("nonexistent.yaml")
	if err == nil {
		t.Error("Expected error when loading nonexistent config file")
	}
}

func TestConfigManager_GetString(t *testing.T) {
	cm, err := NewConfigManager("config.yaml")
	if err != nil {
		t.Fatalf("Failed to create config manager: %v", err)
	}

	// 测试获取字符串配置
	name := cm.GetString("framework.name")
	if name == "" {
		t.Error("framework.name should not be empty")
	}

	// 测试默认值
	defaultValue := cm.GetString("nonexistent.key", "default")
	if defaultValue != "default" {
		t.Errorf("Expected default value 'default', got '%s'", defaultValue)
	}
}

func TestConfigManager_GetInt(t *testing.T) {
	cm, err := NewConfigManager("config.yaml")
	if err != nil {
		t.Fatalf("Failed to create config manager: %v", err)
	}

	// 测试获取整数配置
	port := cm.GetInt("framework.network.port")
	if port <= 0 || port > 65535 {
		t.Errorf("Invalid port number: %d", port)
	}

	// 测试默认值
	defaultValue := cm.GetInt("nonexistent.key", 9999)
	if defaultValue != 9999 {
		t.Errorf("Expected default value 9999, got %d", defaultValue)
	}
}

func TestConfigManager_GetBool(t *testing.T) {
	cm, err := NewConfigManager("config.yaml")
	if err != nil {
		t.Fatalf("Failed to create config manager: %v", err)
	}

	// 测试获取布尔配置
	keepAlive := cm.GetBool("framework.network.keepAlive")
	if !keepAlive {
		t.Error("Expected keepAlive to be true")
	}

	// 测试默认值
	defaultValue := cm.GetBool("nonexistent.key", false)
	if defaultValue != false {
		t.Error("Expected default value false")
	}
}

func TestConfigManager_GetDuration(t *testing.T) {
	cm, err := NewConfigManager("config.yaml")
	if err != nil {
		t.Fatalf("Failed to create config manager: %v", err)
	}

	// 测试获取时间间隔配置
	readTimeout := cm.GetDuration("framework.network.readTimeout")
	if readTimeout <= 0 {
		t.Errorf("Invalid read timeout: %v", readTimeout)
	}

	// 测试默认值
	defaultValue := cm.GetDuration("nonexistent.key", 10*time.Second)
	if defaultValue != 10*time.Second {
		t.Errorf("Expected default value 10s, got %v", defaultValue)
	}
}

func TestConfigManager_GetStringSlice(t *testing.T) {
	cm, err := NewConfigManager("config.yaml")
	if err != nil {
		t.Fatalf("Failed to create config manager: %v", err)
	}

	// 测试获取字符串切片配置
	endpoints := cm.GetStringSlice("framework.registry.endpoints")
	if len(endpoints) == 0 {
		t.Error("registry.endpoints should not be empty")
	}

	// 测试默认值
	defaultValue := cm.GetStringSlice("nonexistent.key", []string{"default1", "default2"})
	if len(defaultValue) != 2 {
		t.Errorf("Expected 2 default values, got %d", len(defaultValue))
	}
}

func TestConfigManager_EnvOverride(t *testing.T) {
	// 设置环境变量
	os.Setenv("FRAMEWORK_NAME", "test-service-from-env")
	os.Setenv("FRAMEWORK_NETWORK_PORT", "9999")
	os.Setenv("FRAMEWORK_NETWORK_KEEPALIVE", "false")
	defer func() {
		os.Unsetenv("FRAMEWORK_NAME")
		os.Unsetenv("FRAMEWORK_NETWORK_PORT")
		os.Unsetenv("FRAMEWORK_NETWORK_KEEPALIVE")
	}()

	cm, err := NewConfigManager("config.yaml")
	if err != nil {
		t.Fatalf("Failed to create config manager: %v", err)
	}

	// 验证环境变量覆盖
	name := cm.GetString("framework.name")
	if name != "test-service-from-env" {
		t.Errorf("Expected name from env 'test-service-from-env', got '%s'", name)
	}

	port := cm.GetInt("framework.network.port")
	if port != 9999 {
		t.Errorf("Expected port from env 9999, got %d", port)
	}

	keepAlive := cm.GetBool("framework.network.keepAlive")
	if keepAlive != false {
		t.Error("Expected keepAlive from env to be false")
	}
}

func TestConfigManager_Validate(t *testing.T) {
	tests := []struct {
		name        string
		setupEnv    func()
		cleanupEnv  func()
		expectError bool
		errorMsg    string
	}{
		{
			name: "valid config",
			setupEnv: func() {
				// 使用默认配置，应该是有效的
			},
			cleanupEnv:  func() {},
			expectError: false,
		},
		{
			name: "missing framework name",
			setupEnv: func() {
				os.Setenv("FRAMEWORK_NAME", "")
			},
			cleanupEnv: func() {
				os.Unsetenv("FRAMEWORK_NAME")
			},
			expectError: true,
			errorMsg:    "framework.name is required",
		},
		{
			name: "invalid port",
			setupEnv: func() {
				os.Setenv("FRAMEWORK_NETWORK_PORT", "99999")
			},
			cleanupEnv: func() {
				os.Unsetenv("FRAMEWORK_NETWORK_PORT")
			},
			expectError: true,
			errorMsg:    "framework.network.port must be between 1 and 65535",
		},
		{
			name: "invalid max connections",
			setupEnv: func() {
				os.Setenv("FRAMEWORK_NETWORK_MAXCONNECTIONS", "-1")
			},
			cleanupEnv: func() {
				os.Unsetenv("FRAMEWORK_NETWORK_MAXCONNECTIONS")
			},
			expectError: true,
			errorMsg:    "framework.network.maxConnections must be positive",
		},
		{
			name: "invalid log level",
			setupEnv: func() {
				os.Setenv("FRAMEWORK_OBSERVABILITY_LOGGING_LEVEL", "invalid")
			},
			cleanupEnv: func() {
				os.Unsetenv("FRAMEWORK_OBSERVABILITY_LOGGING_LEVEL")
			},
			expectError: true,
			errorMsg:    "framework.observability.logging.level must be one of",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			tt.setupEnv()
			defer tt.cleanupEnv()

			_, err := NewConfigManager("config.yaml")
			
			if tt.expectError {
				if err == nil {
					t.Error("Expected error but got none")
				} else if tt.errorMsg != "" && !contains(err.Error(), tt.errorMsg) {
					t.Errorf("Expected error containing '%s', got '%s'", tt.errorMsg, err.Error())
				}
			} else {
				if err != nil {
					t.Errorf("Expected no error but got: %v", err)
				}
			}
		})
	}
}

func TestLoadFrameworkConfig(t *testing.T) {
	cm, err := NewConfigManager("config.yaml")
	if err != nil {
		t.Fatalf("Failed to create config manager: %v", err)
	}

	config, err := cm.LoadFrameworkConfig()
	if err != nil {
		t.Fatalf("Failed to load framework config: %v", err)
	}

	// 验证基础配置
	if config.Name == "" {
		t.Error("Name should not be empty")
	}

	if config.Version == "" {
		t.Error("Version should not be empty")
	}

	if config.Language != "golang" {
		t.Errorf("Expected language 'golang', got '%s'", config.Language)
	}

	// 验证网络配置
	if config.Network.Host == "" {
		t.Error("Network host should not be empty")
	}

	if config.Network.Port <= 0 {
		t.Error("Network port should be positive")
	}

	// 验证注册中心配置
	if config.Registry.Type == "" {
		t.Error("Registry type should not be empty")
	}

	if len(config.Registry.Endpoints) == 0 {
		t.Error("Registry endpoints should not be empty")
	}

	// 验证连接池配置
	if config.ConnectionPool.MaxConnections <= 0 {
		t.Error("Max connections should be positive")
	}

	if config.ConnectionPool.MinConnections < 0 {
		t.Error("Min connections should be non-negative")
	}

	// 验证可观测性配置
	if config.Observability.Logging.Level == "" {
		t.Error("Log level should not be empty")
	}
}

func TestLoadFrameworkConfig_WithEnvOverride(t *testing.T) {
	// 设置环境变量
	os.Setenv("FRAMEWORK_NAME", "env-service")
	os.Setenv("FRAMEWORK_NETWORK_PORT", "8888")
	defer func() {
		os.Unsetenv("FRAMEWORK_NAME")
		os.Unsetenv("FRAMEWORK_NETWORK_PORT")
	}()

	cm, err := NewConfigManager("config.yaml")
	if err != nil {
		t.Fatalf("Failed to create config manager: %v", err)
	}

	config, err := cm.LoadFrameworkConfig()
	if err != nil {
		t.Fatalf("Failed to load framework config: %v", err)
	}

	// 验证环境变量覆盖
	if config.Name != "env-service" {
		t.Errorf("Expected name 'env-service', got '%s'", config.Name)
	}

	if config.Network.Port != 8888 {
		t.Errorf("Expected port 8888, got %d", config.Network.Port)
	}
}

// 辅助函数
func contains(s, substr string) bool {
	return len(s) >= len(substr) && (s == substr || len(substr) == 0 || 
		(len(s) > 0 && len(substr) > 0 && containsHelper(s, substr)))
}

func containsHelper(s, substr string) bool {
	for i := 0; i <= len(s)-len(substr); i++ {
		if s[i:i+len(substr)] == substr {
			return true
		}
	}
	return false
}
