package config

import (
	"context"
	"time"
)

// FrameworkConfig 框架配置
type FrameworkConfig struct {
	Name    string `json:"name"`
	Version string `json:"version"`
	Language string `json:"language"`
	
	Network        NetworkConfig        `json:"network"`
	Registry       RegistryConfig       `json:"registry"`
	Protocols      ProtocolsConfig      `json:"protocols"`
	ConnectionPool ConnectionPoolConfig `json:"connectionPool"`
	Security       SecurityConfig       `json:"security"`
	Observability  ObservabilityConfig  `json:"observability"`
}

// NetworkConfig 网络配置
type NetworkConfig struct {
	Host           string        `json:"host"`
	Port           int           `json:"port"`
	MaxConnections int           `json:"maxConnections"`
	ReadTimeout    time.Duration `json:"readTimeout"`
	WriteTimeout   time.Duration `json:"writeTimeout"`
	KeepAlive      bool          `json:"keepAlive"`
}

// RegistryConfig 注册中心配置
type RegistryConfig struct {
	Type              string   `json:"type"`
	Endpoints         []string `json:"endpoints"`
	Namespace         string   `json:"namespace"`
	TTL               int      `json:"ttl"`
	HeartbeatInterval int      `json:"heartbeatInterval"`
}

// ProtocolsConfig 协议配置
type ProtocolsConfig struct {
	External []ExternalProtocolConfig `json:"external"`
	Internal []InternalProtocolConfig `json:"internal"`
}

// ExternalProtocolConfig 外部协议配置
type ExternalProtocolConfig struct {
	Type    string                 `json:"type"`
	Enabled bool                   `json:"enabled"`
	Port    int                    `json:"port"`
	Path    string                 `json:"path,omitempty"`
	Options map[string]interface{} `json:"options,omitempty"`
}

// InternalProtocolConfig 内部协议配置
type InternalProtocolConfig struct {
	Type          string `json:"type"`
	Enabled       bool   `json:"enabled"`
	Port          int    `json:"port,omitempty"`
	Serialization string `json:"serialization,omitempty"`
	Compression   bool   `json:"compression,omitempty"`
}

// ConnectionPoolConfig 连接池配置
type ConnectionPoolConfig struct {
	MaxConnections    int           `json:"maxConnections"`
	MinConnections    int           `json:"minConnections"`
	IdleTimeout       time.Duration `json:"idleTimeout"`
	MaxLifetime       time.Duration `json:"maxLifetime"`
	ConnectionTimeout time.Duration `json:"connectionTimeout"`
}

// SecurityConfig 安全配置
type SecurityConfig struct {
	TLS            TLSConfig            `json:"tls"`
	Authentication AuthenticationConfig `json:"authentication"`
	Authorization  AuthorizationConfig  `json:"authorization"`
}

// TLSConfig TLS配置
type TLSConfig struct {
	Enabled  bool   `json:"enabled"`
	CertFile string `json:"certFile"`
	KeyFile  string `json:"keyFile"`
	CAFile   string `json:"caFile"`
}

// AuthenticationConfig 认证配置
type AuthenticationConfig struct {
	Enabled bool                   `json:"enabled"`
	Type    string                 `json:"type"`
	Options map[string]interface{} `json:"options,omitempty"`
}

// AuthorizationConfig 授权配置
type AuthorizationConfig struct {
	Enabled bool   `json:"enabled"`
	Type    string `json:"type"`
}

// ObservabilityConfig 可观测性配置
type ObservabilityConfig struct {
	Logging LoggingConfig `json:"logging"`
	Metrics MetricsConfig `json:"metrics"`
	Tracing TracingConfig `json:"tracing"`
}

// LoggingConfig 日志配置
type LoggingConfig struct {
	Level  string `json:"level"`
	Format string `json:"format"`
	Output string `json:"output"`
}

// MetricsConfig 指标配置
type MetricsConfig struct {
	Enabled bool   `json:"enabled"`
	Port    int    `json:"port"`
	Path    string `json:"path"`
}

// TracingConfig 追踪配置
type TracingConfig struct {
	Enabled      bool    `json:"enabled"`
	Exporter     string  `json:"exporter"`
	Endpoint     string  `json:"endpoint"`
	SamplingRate float64 `json:"samplingRate"`
}

// LoadFrameworkConfig 加载框架配置
func (cm *ConfigManager) LoadFrameworkConfig() (*FrameworkConfig, error) {
	config := &FrameworkConfig{}
	
	// 基础配置
	config.Name = cm.GetString("framework.name")
	config.Version = cm.GetString("framework.version")
	config.Language = cm.GetString("framework.language")
	
	// 网络配置
	config.Network = NetworkConfig{
		Host:           cm.GetString("framework.network.host"),
		Port:           cm.GetInt("framework.network.port"),
		MaxConnections: cm.GetInt("framework.network.maxConnections"),
		ReadTimeout:    cm.GetDuration("framework.network.readTimeout"),
		WriteTimeout:   cm.GetDuration("framework.network.writeTimeout"),
		KeepAlive:      cm.GetBool("framework.network.keepAlive"),
	}
	
	// 注册中心配置
	config.Registry = RegistryConfig{
		Type:              cm.GetString("framework.registry.type"),
		Endpoints:         cm.GetStringSlice("framework.registry.endpoints"),
		Namespace:         cm.GetString("framework.registry.namespace"),
		TTL:               cm.GetInt("framework.registry.ttl"),
		HeartbeatInterval: cm.GetInt("framework.registry.heartbeatInterval"),
	}
	
	// 连接池配置
	config.ConnectionPool = ConnectionPoolConfig{
		MaxConnections:    cm.GetInt("framework.connectionPool.maxConnections"),
		MinConnections:    cm.GetInt("framework.connectionPool.minConnections"),
		IdleTimeout:       cm.GetDuration("framework.connectionPool.idleTimeout"),
		MaxLifetime:       cm.GetDuration("framework.connectionPool.maxLifetime"),
		ConnectionTimeout: cm.GetDuration("framework.connectionPool.connectionTimeout"),
	}
	
	// 安全配置
	config.Security = SecurityConfig{
		TLS: TLSConfig{
			Enabled:  cm.GetBool("framework.security.tls.enabled"),
			CertFile: cm.GetString("framework.security.tls.certFile"),
			KeyFile:  cm.GetString("framework.security.tls.keyFile"),
			CAFile:   cm.GetString("framework.security.tls.caFile"),
		},
		Authentication: AuthenticationConfig{
			Enabled: cm.GetBool("framework.security.authentication.enabled"),
			Type:    cm.GetString("framework.security.authentication.type"),
		},
		Authorization: AuthorizationConfig{
			Enabled: cm.GetBool("framework.security.authorization.enabled"),
			Type:    cm.GetString("framework.security.authorization.type"),
		},
	}
	
	// 可观测性配置
	config.Observability = ObservabilityConfig{
		Logging: LoggingConfig{
			Level:  cm.GetString("framework.observability.logging.level"),
			Format: cm.GetString("framework.observability.logging.format"),
			Output: cm.GetString("framework.observability.logging.output"),
		},
		Metrics: MetricsConfig{
			Enabled: cm.GetBool("framework.observability.metrics.enabled"),
			Port:    cm.GetInt("framework.observability.metrics.port"),
			Path:    cm.GetString("framework.observability.metrics.path"),
		},
		Tracing: TracingConfig{
			Enabled:      cm.GetBool("framework.observability.tracing.enabled"),
			Exporter:     cm.GetString("framework.observability.tracing.exporter"),
			Endpoint:     cm.GetString("framework.observability.tracing.endpoint"),
			SamplingRate: cm.GetConfig().MustGet(context.Background(), "framework.observability.tracing.samplingRate").Float64(),
		},
	}
	
	return config, nil
}
