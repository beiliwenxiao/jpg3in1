package security

import (
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"sync"
	"time"
)

// APIKeyConfig API密钥配置
type APIKeyConfig struct {
	Enabled bool `json:"enabled" yaml:"enabled"`
}

// APIKey API密钥信息
type APIKey struct {
	Key       string    `json:"key"`
	UserID    string    `json:"userId"`
	Roles     []string  `json:"roles"`
	CreatedAt time.Time `json:"createdAt"`
	ExpiresAt time.Time `json:"expiresAt"`
	Active    bool      `json:"active"`
}

// APIKeyAuthenticator API密钥认证器
type APIKeyAuthenticator struct {
	config  *APIKeyConfig
	keys    map[string]*APIKey
	keysMux sync.RWMutex
}

// NewAPIKeyAuthenticator 创建API密钥认证器
func NewAPIKeyAuthenticator(config *APIKeyConfig) (*APIKeyAuthenticator, error) {
	if config == nil {
		return nil, fmt.Errorf("API key config cannot be nil")
	}

	return &APIKeyAuthenticator{
		config: config,
		keys:   make(map[string]*APIKey),
	}, nil
}

// GenerateAPIKey 生成API密钥
func (a *APIKeyAuthenticator) GenerateAPIKey(userID string, roles []string, expiresAt time.Time) (*APIKey, error) {
	if !a.config.Enabled {
		return nil, fmt.Errorf("API key authentication is not enabled")
	}

	// 生成随机密钥
	keyBytes := make([]byte, 32)
	if _, err := rand.Read(keyBytes); err != nil {
		return nil, fmt.Errorf("failed to generate random key: %w", err)
	}
	key := hex.EncodeToString(keyBytes)

	apiKey := &APIKey{
		Key:       key,
		UserID:    userID,
		Roles:     roles,
		CreatedAt: time.Now(),
		ExpiresAt: expiresAt,
		Active:    true,
	}

	a.keysMux.Lock()
	a.keys[key] = apiKey
	a.keysMux.Unlock()

	return apiKey, nil
}

// ValidateAPIKey 验证API密钥
func (a *APIKeyAuthenticator) ValidateAPIKey(key string) (*APIKey, error) {
	if !a.config.Enabled {
		return nil, fmt.Errorf("API key authentication is not enabled")
	}

	a.keysMux.RLock()
	apiKey, exists := a.keys[key]
	a.keysMux.RUnlock()

	if !exists {
		return nil, fmt.Errorf("invalid API key")
	}

	if !apiKey.Active {
		return nil, fmt.Errorf("API key is inactive")
	}

	if time.Now().After(apiKey.ExpiresAt) {
		return nil, fmt.Errorf("API key has expired")
	}

	return apiKey, nil
}

// RevokeAPIKey 撤销API密钥
func (a *APIKeyAuthenticator) RevokeAPIKey(key string) error {
	if !a.config.Enabled {
		return fmt.Errorf("API key authentication is not enabled")
	}

	a.keysMux.Lock()
	defer a.keysMux.Unlock()

	apiKey, exists := a.keys[key]
	if !exists {
		return fmt.Errorf("API key not found")
	}

	apiKey.Active = false
	return nil
}

// IsEnabled 检查API密钥认证是否启用
func (a *APIKeyAuthenticator) IsEnabled() bool {
	return a.config.Enabled
}
