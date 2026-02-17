package config

import (
	"context"
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"

	"github.com/gogf/gf/v2/os/gcfg"
)

// ConfigManager 配置管理器
type ConfigManager struct {
	adapter *gcfg.AdapterFile
	config  *gcfg.Config
}

// NewConfigManager 创建配置管理器
func NewConfigManager(configPath string) (*ConfigManager, error) {
	// 检查配置文件是否存在
	if _, err := os.Stat(configPath); os.IsNotExist(err) {
		return nil, fmt.Errorf("config file not found: %s", configPath)
	}

	// 创建配置适配器
	adapter, err := gcfg.NewAdapterFile(configPath)
	if err != nil {
		return nil, fmt.Errorf("failed to create config adapter: %w", err)
	}

	// 创建配置对象
	config := gcfg.NewWithAdapter(adapter)

	cm := &ConfigManager{
		adapter: adapter,
		config:  config,
	}

	// 应用环境变量覆盖
	if err := cm.applyEnvOverrides(); err != nil {
		return nil, fmt.Errorf("failed to apply environment overrides: %w", err)
	}

	// 验证配置
	if err := cm.Validate(); err != nil {
		return nil, fmt.Errorf("config validation failed: %w", err)
	}

	return cm, nil
}

// GetString 获取字符串配置
func (cm *ConfigManager) GetString(pattern string, def ...interface{}) string {
	// 先检查环境变量
	envValue := cm.getEnvValue(pattern)
	if envValue != "" {
		return envValue
	}
	// 如果环境变量存在但为空字符串，也返回空字符串（覆盖配置文件）
	if _, exists := os.LookupEnv(strings.ToUpper(strings.ReplaceAll(pattern, ".", "_"))); exists {
		return envValue
	}
	return cm.config.MustGet(context.Background(), pattern, def...).String()
}

// GetInt 获取整数配置
func (cm *ConfigManager) GetInt(pattern string, def ...interface{}) int {
	// 先检查环境变量
	if envValue := cm.getEnvValue(pattern); envValue != "" {
		if val, err := strconv.Atoi(envValue); err == nil {
			return val
		}
	}
	return cm.config.MustGet(context.Background(), pattern, def...).Int()
}

// GetBool 获取布尔配置
func (cm *ConfigManager) GetBool(pattern string, def ...interface{}) bool {
	// 先检查环境变量
	if envValue := cm.getEnvValue(pattern); envValue != "" {
		if val, err := strconv.ParseBool(envValue); err == nil {
			return val
		}
	}
	return cm.config.MustGet(context.Background(), pattern, def...).Bool()
}

// GetDuration 获取时间间隔配置
func (cm *ConfigManager) GetDuration(pattern string, def ...interface{}) time.Duration {
	// 先检查环境变量
	if envValue := cm.getEnvValue(pattern); envValue != "" {
		if val, err := time.ParseDuration(envValue); err == nil {
			return val
		}
	}
	return cm.config.MustGet(context.Background(), pattern, def...).Duration()
}

// GetStringSlice 获取字符串切片配置
func (cm *ConfigManager) GetStringSlice(pattern string, def ...interface{}) []string {
	// 先检查环境变量
	if envValue := cm.getEnvValue(pattern); envValue != "" {
		return strings.Split(envValue, ",")
	}
	return cm.config.MustGet(context.Background(), pattern, def...).Strings()
}

// GetConfig 获取原始配置对象
func (cm *ConfigManager) GetConfig() *gcfg.Config {
	return cm.config
}

// applyEnvOverrides 应用环境变量覆盖
func (cm *ConfigManager) applyEnvOverrides() error {
	// 环境变量命名规则: FRAMEWORK_SECTION_KEY
	// 例如: FRAMEWORK_NETWORK_HOST, FRAMEWORK_REGISTRY_TYPE
	
	// 这里不需要手动设置，getEnvValue 会在获取时自动检查环境变量
	return nil
}

// getEnvValue 获取环境变量值
func (cm *ConfigManager) getEnvValue(pattern string) string {
	// 将配置路径转换为环境变量名
	// 例如: framework.network.host -> FRAMEWORK_NETWORK_HOST
	envKey := strings.ToUpper(strings.ReplaceAll(pattern, ".", "_"))
	return os.Getenv(envKey)
}

// Validate 验证配置
func (cm *ConfigManager) Validate() error {
	// 验证必需的配置项
	if cm.GetString("framework.name") == "" {
		return fmt.Errorf("framework.name is required")
	}

	if cm.GetString("framework.version") == "" {
		return fmt.Errorf("framework.version is required")
	}

	if cm.GetString("framework.language") == "" {
		return fmt.Errorf("framework.language is required")
	}

	// 验证网络配置
	if cm.GetString("framework.network.host") == "" {
		return fmt.Errorf("framework.network.host is required")
	}

	port := cm.GetInt("framework.network.port")
	if port <= 0 || port > 65535 {
		return fmt.Errorf("framework.network.port must be between 1 and 65535, got %d", port)
	}

	maxConnections := cm.GetInt("framework.network.maxConnections")
	if maxConnections <= 0 {
		return fmt.Errorf("framework.network.maxConnections must be positive, got %d", maxConnections)
	}

	// 验证注册中心配置
	registryType := cm.GetString("framework.registry.type")
	if registryType == "" {
		return fmt.Errorf("framework.registry.type is required")
	}

	endpoints := cm.GetStringSlice("framework.registry.endpoints")
	if len(endpoints) == 0 {
		return fmt.Errorf("framework.registry.endpoints is required")
	}

	// 验证连接池配置
	maxPoolConnections := cm.GetInt("framework.connectionPool.maxConnections")
	minPoolConnections := cm.GetInt("framework.connectionPool.minConnections")
	
	if maxPoolConnections <= 0 {
		return fmt.Errorf("framework.connectionPool.maxConnections must be positive, got %d", maxPoolConnections)
	}

	if minPoolConnections < 0 {
		return fmt.Errorf("framework.connectionPool.minConnections must be non-negative, got %d", minPoolConnections)
	}

	if minPoolConnections > maxPoolConnections {
		return fmt.Errorf("framework.connectionPool.minConnections (%d) cannot be greater than maxConnections (%d)", 
			minPoolConnections, maxPoolConnections)
	}

	// 验证日志级别
	logLevel := cm.GetString("framework.observability.logging.level")
	validLevels := map[string]bool{"debug": true, "info": true, "warn": true, "error": true}
	if !validLevels[logLevel] {
		return fmt.Errorf("framework.observability.logging.level must be one of [debug, info, warn, error], got %s", logLevel)
	}

	return nil
}
