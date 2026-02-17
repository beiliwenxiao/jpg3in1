package security

import (
	"crypto/tls"
	"crypto/x509"
	"fmt"
	"os"
)

// TLSConfig TLS配置
type TLSConfig struct {
	Enabled  bool   `json:"enabled" yaml:"enabled"`
	CertFile string `json:"certFile" yaml:"certFile"`
	KeyFile  string `json:"keyFile" yaml:"keyFile"`
	CAFile   string `json:"caFile" yaml:"caFile"`
}

// TLSManager TLS管理器
type TLSManager struct {
	config    *TLSConfig
	tlsConfig *tls.Config
}

// NewTLSManager 创建TLS管理器
func NewTLSManager(config *TLSConfig) (*TLSManager, error) {
	if config == nil {
		return nil, fmt.Errorf("TLS config cannot be nil")
	}

	manager := &TLSManager{
		config: config,
	}

	if config.Enabled {
		tlsConfig, err := manager.buildTLSConfig()
		if err != nil {
			return nil, fmt.Errorf("failed to build TLS config: %w", err)
		}
		manager.tlsConfig = tlsConfig
	}

	return manager, nil
}

// buildTLSConfig 构建TLS配置
func (m *TLSManager) buildTLSConfig() (*tls.Config, error) {
	// 加载证书和密钥
	cert, err := tls.LoadX509KeyPair(m.config.CertFile, m.config.KeyFile)
	if err != nil {
		return nil, fmt.Errorf("failed to load certificate and key: %w", err)
	}

	tlsConfig := &tls.Config{
		Certificates: []tls.Certificate{cert},
		MinVersion:   tls.VersionTLS12,
	}

	// 如果提供了CA文件，加载CA证书池
	if m.config.CAFile != "" {
		caCert, err := os.ReadFile(m.config.CAFile)
		if err != nil {
			return nil, fmt.Errorf("failed to read CA file: %w", err)
		}

		caCertPool := x509.NewCertPool()
		if !caCertPool.AppendCertsFromPEM(caCert) {
			return nil, fmt.Errorf("failed to parse CA certificate")
		}

		tlsConfig.RootCAs = caCertPool
		tlsConfig.ClientCAs = caCertPool
		tlsConfig.ClientAuth = tls.RequireAndVerifyClientCert
	}

	return tlsConfig, nil
}

// GetTLSConfig 获取TLS配置
func (m *TLSManager) GetTLSConfig() *tls.Config {
	return m.tlsConfig
}

// IsEnabled 检查TLS是否启用
func (m *TLSManager) IsEnabled() bool {
	return m.config.Enabled
}
