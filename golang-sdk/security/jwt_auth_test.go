package security

import (
	"testing"
	"time"
)

func TestNewJWTAuthenticator(t *testing.T) {
	tests := []struct {
		name    string
		config  *JWTConfig
		wantErr bool
	}{
		{
			name:    "nil config",
			config:  nil,
			wantErr: true,
		},
		{
			name: "disabled JWT",
			config: &JWTConfig{
				Enabled: false,
			},
			wantErr: false,
		},
		{
			name: "enabled JWT without secret",
			config: &JWTConfig{
				Enabled: true,
				Secret:  "",
			},
			wantErr: true,
		},
		{
			name: "enabled JWT with secret",
			config: &JWTConfig{
				Enabled:    true,
				Secret:     "test-secret",
				Expiration: 1 * time.Hour,
				Issuer:     "test-issuer",
			},
			wantErr: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			auth, err := NewJWTAuthenticator(tt.config)
			if (err != nil) != tt.wantErr {
				t.Errorf("NewJWTAuthenticator() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if !tt.wantErr && auth == nil {
				t.Error("NewJWTAuthenticator() returned nil authenticator")
			}
		})
	}
}

func TestJWTAuthenticator_GenerateAndValidateToken(t *testing.T) {
	config := &JWTConfig{
		Enabled:    true,
		Secret:     "test-secret-key",
		Expiration: 1 * time.Hour,
		Issuer:     "test-issuer",
	}

	auth, err := NewJWTAuthenticator(config)
	if err != nil {
		t.Fatalf("NewJWTAuthenticator() error = %v", err)
	}

	userID := "user123"
	roles := []string{"admin", "user"}

	// 生成令牌
	token, err := auth.GenerateToken(userID, roles)
	if err != nil {
		t.Fatalf("GenerateToken() error = %v", err)
	}
	if token == "" {
		t.Error("GenerateToken() returned empty token")
	}

	// 验证令牌
	claims, err := auth.ValidateToken(token)
	if err != nil {
		t.Fatalf("ValidateToken() error = %v", err)
	}

	if claims.UserID != userID {
		t.Errorf("UserID = %v, want %v", claims.UserID, userID)
	}

	if len(claims.Roles) != len(roles) {
		t.Errorf("Roles length = %v, want %v", len(claims.Roles), len(roles))
	}

	for i, role := range roles {
		if claims.Roles[i] != role {
			t.Errorf("Role[%d] = %v, want %v", i, claims.Roles[i], role)
		}
	}
}

func TestJWTAuthenticator_ValidateToken_InvalidToken(t *testing.T) {
	config := &JWTConfig{
		Enabled:    true,
		Secret:     "test-secret-key",
		Expiration: 1 * time.Hour,
		Issuer:     "test-issuer",
	}

	auth, err := NewJWTAuthenticator(config)
	if err != nil {
		t.Fatalf("NewJWTAuthenticator() error = %v", err)
	}

	// 测试无效令牌
	_, err = auth.ValidateToken("invalid-token")
	if err == nil {
		t.Error("ValidateToken() should return error for invalid token")
	}
}

func TestJWTAuthenticator_ValidateToken_WrongSecret(t *testing.T) {
	config1 := &JWTConfig{
		Enabled:    true,
		Secret:     "secret1",
		Expiration: 1 * time.Hour,
		Issuer:     "test-issuer",
	}

	auth1, _ := NewJWTAuthenticator(config1)
	token, _ := auth1.GenerateToken("user123", []string{"user"})

	// 使用不同的密钥验证
	config2 := &JWTConfig{
		Enabled:    true,
		Secret:     "secret2",
		Expiration: 1 * time.Hour,
		Issuer:     "test-issuer",
	}

	auth2, _ := NewJWTAuthenticator(config2)
	_, err := auth2.ValidateToken(token)
	if err == nil {
		t.Error("ValidateToken() should return error for token signed with different secret")
	}
}

func TestJWTAuthenticator_IsEnabled(t *testing.T) {
	tests := []struct {
		name   string
		config *JWTConfig
		want   bool
	}{
		{
			name: "enabled",
			config: &JWTConfig{
				Enabled:    true,
				Secret:     "test-secret",
				Expiration: 1 * time.Hour,
			},
			want: true,
		},
		{
			name: "disabled",
			config: &JWTConfig{
				Enabled: false,
			},
			want: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			auth, err := NewJWTAuthenticator(tt.config)
			if err != nil {
				t.Fatalf("NewJWTAuthenticator() error = %v", err)
			}
			if got := auth.IsEnabled(); got != tt.want {
				t.Errorf("IsEnabled() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestJWTAuthenticator_GenerateToken_Disabled(t *testing.T) {
	config := &JWTConfig{
		Enabled: false,
	}

	auth, _ := NewJWTAuthenticator(config)
	_, err := auth.GenerateToken("user123", []string{"user"})
	if err == nil {
		t.Error("GenerateToken() should return error when JWT is disabled")
	}
}

func TestJWTAuthenticator_ValidateToken_Disabled(t *testing.T) {
	config := &JWTConfig{
		Enabled: false,
	}

	auth, _ := NewJWTAuthenticator(config)
	_, err := auth.ValidateToken("some-token")
	if err == nil {
		t.Error("ValidateToken() should return error when JWT is disabled")
	}
}
