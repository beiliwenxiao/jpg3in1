package security

import (
	"fmt"
)

// SecurityConfig 安全配置
type SecurityConfig struct {
	TLS    *TLSConfig    `json:"tls" yaml:"tls"`
	JWT    *JWTConfig    `json:"jwt" yaml:"jwt"`
	APIKey *APIKeyConfig `json:"apiKey" yaml:"apiKey"`
	RBAC   *RBACConfig   `json:"rbac" yaml:"rbac"`
}

// SecurityManager 安全管理器
type SecurityManager struct {
	config           *SecurityConfig
	tlsManager       *TLSManager
	jwtAuth          *JWTAuthenticator
	apiKeyAuth       *APIKeyAuthenticator
	rbacAuthorizer   *RBACAuthorizer
}

// NewSecurityManager 创建安全管理器
func NewSecurityManager(config *SecurityConfig) (*SecurityManager, error) {
	if config == nil {
		return nil, fmt.Errorf("security config cannot be nil")
	}

	manager := &SecurityManager{
		config: config,
	}

	// 初始化TLS管理器
	if config.TLS != nil {
		tlsManager, err := NewTLSManager(config.TLS)
		if err != nil {
			return nil, fmt.Errorf("failed to create TLS manager: %w", err)
		}
		manager.tlsManager = tlsManager
	}

	// 初始化JWT认证器
	if config.JWT != nil {
		jwtAuth, err := NewJWTAuthenticator(config.JWT)
		if err != nil {
			return nil, fmt.Errorf("failed to create JWT authenticator: %w", err)
		}
		manager.jwtAuth = jwtAuth
	}

	// 初始化API密钥认证器
	if config.APIKey != nil {
		apiKeyAuth, err := NewAPIKeyAuthenticator(config.APIKey)
		if err != nil {
			return nil, fmt.Errorf("failed to create API key authenticator: %w", err)
		}
		manager.apiKeyAuth = apiKeyAuth
	}

	// 初始化RBAC授权器
	if config.RBAC != nil {
		rbacAuthorizer, err := NewRBACAuthorizer(config.RBAC)
		if err != nil {
			return nil, fmt.Errorf("failed to create RBAC authorizer: %w", err)
		}
		manager.rbacAuthorizer = rbacAuthorizer
	}

	return manager, nil
}

// GetTLSManager 获取TLS管理器
func (m *SecurityManager) GetTLSManager() *TLSManager {
	return m.tlsManager
}

// GetJWTAuthenticator 获取JWT认证器
func (m *SecurityManager) GetJWTAuthenticator() *JWTAuthenticator {
	return m.jwtAuth
}

// GetAPIKeyAuthenticator 获取API密钥认证器
func (m *SecurityManager) GetAPIKeyAuthenticator() *APIKeyAuthenticator {
	return m.apiKeyAuth
}

// GetRBACAuthorizer 获取RBAC授权器
func (m *SecurityManager) GetRBACAuthorizer() *RBACAuthorizer {
	return m.rbacAuthorizer
}

// AuthenticateJWT 使用JWT认证
func (m *SecurityManager) AuthenticateJWT(token string) (*Claims, error) {
	if m.jwtAuth == nil || !m.jwtAuth.IsEnabled() {
		return nil, fmt.Errorf("JWT authentication is not configured or enabled")
	}

	return m.jwtAuth.ValidateToken(token)
}

// AuthenticateAPIKey 使用API密钥认证
func (m *SecurityManager) AuthenticateAPIKey(key string) (*APIKey, error) {
	if m.apiKeyAuth == nil || !m.apiKeyAuth.IsEnabled() {
		return nil, fmt.Errorf("API key authentication is not configured or enabled")
	}

	return m.apiKeyAuth.ValidateAPIKey(key)
}

// Authorize 授权检查
func (m *SecurityManager) Authorize(roles []string, resource, action string) error {
	if m.rbacAuthorizer == nil || !m.rbacAuthorizer.IsEnabled() {
		return nil // RBAC未配置或未启用，允许所有访问
	}

	return m.rbacAuthorizer.CheckPermission(roles, resource, action)
}

// IsTLSEnabled 检查TLS是否启用
func (m *SecurityManager) IsTLSEnabled() bool {
	return m.tlsManager != nil && m.tlsManager.IsEnabled()
}
