package security

import (
	"fmt"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

// JWTConfig JWT配置
type JWTConfig struct {
	Enabled    bool          `json:"enabled" yaml:"enabled"`
	Secret     string        `json:"secret" yaml:"secret"`
	Expiration time.Duration `json:"expiration" yaml:"expiration"`
	Issuer     string        `json:"issuer" yaml:"issuer"`
}

// Claims JWT声明
type Claims struct {
	UserID string   `json:"userId"`
	Roles  []string `json:"roles"`
	jwt.RegisteredClaims
}

// JWTAuthenticator JWT认证器
type JWTAuthenticator struct {
	config *JWTConfig
}

// NewJWTAuthenticator 创建JWT认证器
func NewJWTAuthenticator(config *JWTConfig) (*JWTAuthenticator, error) {
	if config == nil {
		return nil, fmt.Errorf("JWT config cannot be nil")
	}

	if config.Enabled && config.Secret == "" {
		return nil, fmt.Errorf("JWT secret cannot be empty when enabled")
	}

	return &JWTAuthenticator{
		config: config,
	}, nil
}

// GenerateToken 生成JWT令牌
func (a *JWTAuthenticator) GenerateToken(userID string, roles []string) (string, error) {
	if !a.config.Enabled {
		return "", fmt.Errorf("JWT authentication is not enabled")
	}

	now := time.Now()
	claims := &Claims{
		UserID: userID,
		Roles:  roles,
		RegisteredClaims: jwt.RegisteredClaims{
			Issuer:    a.config.Issuer,
			IssuedAt:  jwt.NewNumericDate(now),
			ExpiresAt: jwt.NewNumericDate(now.Add(a.config.Expiration)),
		},
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	tokenString, err := token.SignedString([]byte(a.config.Secret))
	if err != nil {
		return "", fmt.Errorf("failed to sign token: %w", err)
	}

	return tokenString, nil
}

// ValidateToken 验证JWT令牌
func (a *JWTAuthenticator) ValidateToken(tokenString string) (*Claims, error) {
	if !a.config.Enabled {
		return nil, fmt.Errorf("JWT authentication is not enabled")
	}

	token, err := jwt.ParseWithClaims(tokenString, &Claims{}, func(token *jwt.Token) (interface{}, error) {
		// 验证签名方法
		if _, ok := token.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", token.Header["alg"])
		}
		return []byte(a.config.Secret), nil
	})

	if err != nil {
		return nil, fmt.Errorf("failed to parse token: %w", err)
	}

	if !token.Valid {
		return nil, fmt.Errorf("invalid token")
	}

	claims, ok := token.Claims.(*Claims)
	if !ok {
		return nil, fmt.Errorf("invalid token claims")
	}

	return claims, nil
}

// IsEnabled 检查JWT认证是否启用
func (a *JWTAuthenticator) IsEnabled() bool {
	return a.config.Enabled
}
