package security

import (
	"testing"
	"time"
)

func TestNewSecurityManager(t *testing.T) {
	tests := []struct {
		name    string
		config  *SecurityConfig
		wantErr bool
	}{
		{
			name:    "nil config",
			config:  nil,
			wantErr: true,
		},
		{
			name: "valid config with all components",
			config: &SecurityConfig{
				TLS: &TLSConfig{
					Enabled: false,
				},
				JWT: &JWTConfig{
					Enabled:    true,
					Secret:     "test-secret",
					Expiration: 1 * time.Hour,
					Issuer:     "test",
				},
				APIKey: &APIKeyConfig{
					Enabled: true,
				},
				RBAC: &RBACConfig{
					Enabled: true,
				},
			},
			wantErr: false,
		},
		{
			name: "config with only JWT",
			config: &SecurityConfig{
				JWT: &JWTConfig{
					Enabled:    true,
					Secret:     "test-secret",
					Expiration: 1 * time.Hour,
				},
			},
			wantErr: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			manager, err := NewSecurityManager(tt.config)
			if (err != nil) != tt.wantErr {
				t.Errorf("NewSecurityManager() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if !tt.wantErr && manager == nil {
				t.Error("NewSecurityManager() returned nil manager")
			}
		})
	}
}

func TestSecurityManager_GetComponents(t *testing.T) {
	config := &SecurityConfig{
		TLS: &TLSConfig{
			Enabled: false,
		},
		JWT: &JWTConfig{
			Enabled:    true,
			Secret:     "test-secret",
			Expiration: 1 * time.Hour,
		},
		APIKey: &APIKeyConfig{
			Enabled: true,
		},
		RBAC: &RBACConfig{
			Enabled: true,
		},
	}

	manager, err := NewSecurityManager(config)
	if err != nil {
		t.Fatalf("NewSecurityManager() error = %v", err)
	}

	// 测试获取各个组件
	if manager.GetTLSManager() == nil {
		t.Error("GetTLSManager() returned nil")
	}

	if manager.GetJWTAuthenticator() == nil {
		t.Error("GetJWTAuthenticator() returned nil")
	}

	if manager.GetAPIKeyAuthenticator() == nil {
		t.Error("GetAPIKeyAuthenticator() returned nil")
	}

	if manager.GetRBACAuthorizer() == nil {
		t.Error("GetRBACAuthorizer() returned nil")
	}
}

func TestSecurityManager_AuthenticateJWT(t *testing.T) {
	config := &SecurityConfig{
		JWT: &JWTConfig{
			Enabled:    true,
			Secret:     "test-secret",
			Expiration: 1 * time.Hour,
			Issuer:     "test",
		},
	}

	manager, err := NewSecurityManager(config)
	if err != nil {
		t.Fatalf("NewSecurityManager() error = %v", err)
	}

	// 生成令牌
	token, err := manager.GetJWTAuthenticator().GenerateToken("user123", []string{"user"})
	if err != nil {
		t.Fatalf("GenerateToken() error = %v", err)
	}

	// 使用SecurityManager验证令牌
	claims, err := manager.AuthenticateJWT(token)
	if err != nil {
		t.Fatalf("AuthenticateJWT() error = %v", err)
	}

	if claims.UserID != "user123" {
		t.Errorf("UserID = %v, want %v", claims.UserID, "user123")
	}
}

func TestSecurityManager_AuthenticateJWT_NotConfigured(t *testing.T) {
	config := &SecurityConfig{
		// JWT未配置
	}

	manager, err := NewSecurityManager(config)
	if err != nil {
		t.Fatalf("NewSecurityManager() error = %v", err)
	}

	_, err = manager.AuthenticateJWT("some-token")
	if err == nil {
		t.Error("AuthenticateJWT() should return error when JWT is not configured")
	}
}

func TestSecurityManager_AuthenticateAPIKey(t *testing.T) {
	config := &SecurityConfig{
		APIKey: &APIKeyConfig{
			Enabled: true,
		},
	}

	manager, err := NewSecurityManager(config)
	if err != nil {
		t.Fatalf("NewSecurityManager() error = %v", err)
	}

	// 生成API密钥
	apiKey, err := manager.GetAPIKeyAuthenticator().GenerateAPIKey(
		"user123",
		[]string{"user"},
		time.Now().Add(24*time.Hour),
	)
	if err != nil {
		t.Fatalf("GenerateAPIKey() error = %v", err)
	}

	// 使用SecurityManager验证API密钥
	validatedKey, err := manager.AuthenticateAPIKey(apiKey.Key)
	if err != nil {
		t.Fatalf("AuthenticateAPIKey() error = %v", err)
	}

	if validatedKey.UserID != "user123" {
		t.Errorf("UserID = %v, want %v", validatedKey.UserID, "user123")
	}
}

func TestSecurityManager_AuthenticateAPIKey_NotConfigured(t *testing.T) {
	config := &SecurityConfig{
		// API Key未配置
	}

	manager, err := NewSecurityManager(config)
	if err != nil {
		t.Fatalf("NewSecurityManager() error = %v", err)
	}

	_, err = manager.AuthenticateAPIKey("some-key")
	if err == nil {
		t.Error("AuthenticateAPIKey() should return error when API Key is not configured")
	}
}

func TestSecurityManager_Authorize(t *testing.T) {
	config := &SecurityConfig{
		RBAC: &RBACConfig{
			Enabled: true,
		},
	}

	manager, err := NewSecurityManager(config)
	if err != nil {
		t.Fatalf("NewSecurityManager() error = %v", err)
	}

	// 测试授权
	err = manager.Authorize([]string{"admin"}, "service", "read")
	if err != nil {
		t.Errorf("Authorize() error = %v", err)
	}

	// 测试授权失败
	err = manager.Authorize([]string{"guest"}, "service", "write")
	if err == nil {
		t.Error("Authorize() should return error for insufficient permissions")
	}
}

func TestSecurityManager_Authorize_NotConfigured(t *testing.T) {
	config := &SecurityConfig{
		// RBAC未配置
	}

	manager, err := NewSecurityManager(config)
	if err != nil {
		t.Fatalf("NewSecurityManager() error = %v", err)
	}

	// RBAC未配置时应该允许所有访问
	err = manager.Authorize([]string{}, "service", "read")
	if err != nil {
		t.Error("Authorize() should succeed when RBAC is not configured")
	}
}

func TestSecurityManager_IsTLSEnabled(t *testing.T) {
	tests := []struct {
		name    string
		config  *SecurityConfig
		want    bool
		skipErr bool
	}{
		{
			name: "TLS enabled",
			config: &SecurityConfig{
				TLS: &TLSConfig{
					Enabled: true,
				},
			},
			want:    true,
			skipErr: true, // 跳过错误，因为没有实际证书文件
		},
		{
			name: "TLS disabled",
			config: &SecurityConfig{
				TLS: &TLSConfig{
					Enabled: false,
				},
			},
			want:    false,
			skipErr: false,
		},
		{
			name:    "TLS not configured",
			config:  &SecurityConfig{},
			want:    false,
			skipErr: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			manager, err := NewSecurityManager(tt.config)
			if err != nil {
				if tt.skipErr {
					t.Skipf("Skipping test due to missing certificate files: %v", err)
					return
				}
				t.Fatalf("NewSecurityManager() error = %v", err)
			}
			if got := manager.IsTLSEnabled(); got != tt.want {
				t.Errorf("IsTLSEnabled() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestSecurityManager_IntegrationTest(t *testing.T) {
	// 集成测试：JWT认证 + RBAC授权
	config := &SecurityConfig{
		JWT: &JWTConfig{
			Enabled:    true,
			Secret:     "test-secret",
			Expiration: 1 * time.Hour,
			Issuer:     "test",
		},
		RBAC: &RBACConfig{
			Enabled: true,
		},
	}

	manager, err := NewSecurityManager(config)
	if err != nil {
		t.Fatalf("NewSecurityManager() error = %v", err)
	}

	// 1. 生成JWT令牌
	token, err := manager.GetJWTAuthenticator().GenerateToken("user123", []string{"user"})
	if err != nil {
		t.Fatalf("GenerateToken() error = %v", err)
	}

	// 2. 验证JWT令牌
	claims, err := manager.AuthenticateJWT(token)
	if err != nil {
		t.Fatalf("AuthenticateJWT() error = %v", err)
	}

	// 3. 使用令牌中的角色进行授权检查
	err = manager.Authorize(claims.Roles, "service", "read")
	if err != nil {
		t.Errorf("Authorize() error = %v", err)
	}

	// 4. 测试权限不足的情况
	err = manager.Authorize(claims.Roles, "deployment", "delete")
	if err == nil {
		t.Error("Authorize() should return error for insufficient permissions")
	}
}
