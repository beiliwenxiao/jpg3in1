package security

import (
	"testing"
	"time"
)

func TestNewAPIKeyAuthenticator(t *testing.T) {
	tests := []struct {
		name    string
		config  *APIKeyConfig
		wantErr bool
	}{
		{
			name:    "nil config",
			config:  nil,
			wantErr: true,
		},
		{
			name: "valid config",
			config: &APIKeyConfig{
				Enabled: true,
			},
			wantErr: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			auth, err := NewAPIKeyAuthenticator(tt.config)
			if (err != nil) != tt.wantErr {
				t.Errorf("NewAPIKeyAuthenticator() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if !tt.wantErr && auth == nil {
				t.Error("NewAPIKeyAuthenticator() returned nil authenticator")
			}
		})
	}
}

func TestAPIKeyAuthenticator_GenerateAndValidateAPIKey(t *testing.T) {
	config := &APIKeyConfig{
		Enabled: true,
	}

	auth, err := NewAPIKeyAuthenticator(config)
	if err != nil {
		t.Fatalf("NewAPIKeyAuthenticator() error = %v", err)
	}

	userID := "user123"
	roles := []string{"admin", "user"}
	expiresAt := time.Now().Add(24 * time.Hour)

	// 生成API密钥
	apiKey, err := auth.GenerateAPIKey(userID, roles, expiresAt)
	if err != nil {
		t.Fatalf("GenerateAPIKey() error = %v", err)
	}

	if apiKey.Key == "" {
		t.Error("GenerateAPIKey() returned empty key")
	}

	if apiKey.UserID != userID {
		t.Errorf("UserID = %v, want %v", apiKey.UserID, userID)
	}

	if !apiKey.Active {
		t.Error("Generated API key should be active")
	}

	// 验证API密钥
	validatedKey, err := auth.ValidateAPIKey(apiKey.Key)
	if err != nil {
		t.Fatalf("ValidateAPIKey() error = %v", err)
	}

	if validatedKey.UserID != userID {
		t.Errorf("UserID = %v, want %v", validatedKey.UserID, userID)
	}

	if len(validatedKey.Roles) != len(roles) {
		t.Errorf("Roles length = %v, want %v", len(validatedKey.Roles), len(roles))
	}
}

func TestAPIKeyAuthenticator_ValidateAPIKey_InvalidKey(t *testing.T) {
	config := &APIKeyConfig{
		Enabled: true,
	}

	auth, err := NewAPIKeyAuthenticator(config)
	if err != nil {
		t.Fatalf("NewAPIKeyAuthenticator() error = %v", err)
	}

	// 测试无效密钥
	_, err = auth.ValidateAPIKey("invalid-key")
	if err == nil {
		t.Error("ValidateAPIKey() should return error for invalid key")
	}
}

func TestAPIKeyAuthenticator_ValidateAPIKey_ExpiredKey(t *testing.T) {
	config := &APIKeyConfig{
		Enabled: true,
	}

	auth, err := NewAPIKeyAuthenticator(config)
	if err != nil {
		t.Fatalf("NewAPIKeyAuthenticator() error = %v", err)
	}

	// 生成已过期的密钥
	expiresAt := time.Now().Add(-1 * time.Hour)
	apiKey, _ := auth.GenerateAPIKey("user123", []string{"user"}, expiresAt)

	// 验证过期密钥
	_, err = auth.ValidateAPIKey(apiKey.Key)
	if err == nil {
		t.Error("ValidateAPIKey() should return error for expired key")
	}
}

func TestAPIKeyAuthenticator_RevokeAPIKey(t *testing.T) {
	config := &APIKeyConfig{
		Enabled: true,
	}

	auth, err := NewAPIKeyAuthenticator(config)
	if err != nil {
		t.Fatalf("NewAPIKeyAuthenticator() error = %v", err)
	}

	// 生成API密钥
	expiresAt := time.Now().Add(24 * time.Hour)
	apiKey, _ := auth.GenerateAPIKey("user123", []string{"user"}, expiresAt)

	// 撤销密钥
	err = auth.RevokeAPIKey(apiKey.Key)
	if err != nil {
		t.Fatalf("RevokeAPIKey() error = %v", err)
	}

	// 验证撤销后的密钥
	_, err = auth.ValidateAPIKey(apiKey.Key)
	if err == nil {
		t.Error("ValidateAPIKey() should return error for revoked key")
	}
}

func TestAPIKeyAuthenticator_RevokeAPIKey_NotFound(t *testing.T) {
	config := &APIKeyConfig{
		Enabled: true,
	}

	auth, err := NewAPIKeyAuthenticator(config)
	if err != nil {
		t.Fatalf("NewAPIKeyAuthenticator() error = %v", err)
	}

	// 撤销不存在的密钥
	err = auth.RevokeAPIKey("non-existent-key")
	if err == nil {
		t.Error("RevokeAPIKey() should return error for non-existent key")
	}
}

func TestAPIKeyAuthenticator_IsEnabled(t *testing.T) {
	tests := []struct {
		name   string
		config *APIKeyConfig
		want   bool
	}{
		{
			name: "enabled",
			config: &APIKeyConfig{
				Enabled: true,
			},
			want: true,
		},
		{
			name: "disabled",
			config: &APIKeyConfig{
				Enabled: false,
			},
			want: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			auth, err := NewAPIKeyAuthenticator(tt.config)
			if err != nil {
				t.Fatalf("NewAPIKeyAuthenticator() error = %v", err)
			}
			if got := auth.IsEnabled(); got != tt.want {
				t.Errorf("IsEnabled() = %v, want %v", got, tt.want)
			}
		})
	}
}

func TestAPIKeyAuthenticator_GenerateAPIKey_Disabled(t *testing.T) {
	config := &APIKeyConfig{
		Enabled: false,
	}

	auth, _ := NewAPIKeyAuthenticator(config)
	_, err := auth.GenerateAPIKey("user123", []string{"user"}, time.Now().Add(24*time.Hour))
	if err == nil {
		t.Error("GenerateAPIKey() should return error when API key auth is disabled")
	}
}

func TestAPIKeyAuthenticator_ValidateAPIKey_Disabled(t *testing.T) {
	config := &APIKeyConfig{
		Enabled: false,
	}

	auth, _ := NewAPIKeyAuthenticator(config)
	_, err := auth.ValidateAPIKey("some-key")
	if err == nil {
		t.Error("ValidateAPIKey() should return error when API key auth is disabled")
	}
}
