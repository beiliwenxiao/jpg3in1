# Security 安全模块

安全模块提供了多层次的安全机制，包括TLS加密通信、JWT认证、API密钥认证和基于角色的访问控制（RBAC）。

## 功能特性

### 1. TLS/SSL 加密通信
- 支持TLS 1.2及以上版本
- 支持双向TLS认证
- 支持自定义CA证书

### 2. JWT认证
- 基于golang-jwt库实现
- 支持令牌生成和验证
- 支持自定义过期时间
- 包含用户ID和角色信息

### 3. API密钥认证
- 支持API密钥生成和验证
- 支持密钥过期时间
- 支持密钥撤销

### 4. RBAC授权
- 基于角色的访问控制
- 支持资源和操作的细粒度权限控制
- 支持通配符权限
- 预定义admin、user、guest角色

## 使用示例

### 创建安全管理器

```go
import "golang-sdk/security"

config := &security.SecurityConfig{
    TLS: &security.TLSConfig{
        Enabled:  true,
        CertFile: "/path/to/cert.pem",
        KeyFile:  "/path/to/key.pem",
        CAFile:   "/path/to/ca.pem",
    },
    JWT: &security.JWTConfig{
        Enabled:    true,
        Secret:     "your-secret-key",
        Expiration: 24 * time.Hour,
        Issuer:     "your-service",
    },
    APIKey: &security.APIKeyConfig{
        Enabled: true,
    },
    RBAC: &security.RBACConfig{
        Enabled: true,
    },
}

manager, err := security.NewSecurityManager(config)
if err != nil {
    log.Fatal(err)
}
```

### JWT认证

```go
// 生成JWT令牌
token, err := manager.GetJWTAuthenticator().GenerateToken("user123", []string{"user", "admin"})
if err != nil {
    log.Fatal(err)
}

// 验证JWT令牌
claims, err := manager.AuthenticateJWT(token)
if err != nil {
    // 返回401错误
    log.Printf("Authentication failed: %v", err)
}
```

### API密钥认证

```go
// 生成API密钥
apiKey, err := manager.GetAPIKeyAuthenticator().GenerateAPIKey(
    "user123",
    []string{"user"},
    time.Now().Add(30 * 24 * time.Hour),
)
if err != nil {
    log.Fatal(err)
}

// 验证API密钥
validatedKey, err := manager.AuthenticateAPIKey(apiKey.Key)
if err != nil {
    // 返回401错误
    log.Printf("Authentication failed: %v", err)
}
```

### RBAC授权

```go
// 检查权限
err := manager.Authorize([]string{"user"}, "service", "read")
if err != nil {
    // 返回403错误
    log.Printf("Authorization failed: %v", err)
}

// 添加自定义角色
customRole := &security.Role{
    Name: "developer",
    Permissions: []security.Permission{
        {Resource: "service", Action: "read"},
        {Resource: "service", Action: "write"},
        {Resource: "deployment", Action: "read"},
    },
}
manager.GetRBACAuthorizer().AddRole(customRole)
```

### TLS配置

```go
// 获取TLS配置用于HTTP服务器
if manager.IsTLSEnabled() {
    tlsConfig := manager.GetTLSManager().GetTLSConfig()
    server := &http.Server{
        Addr:      ":8443",
        TLSConfig: tlsConfig,
    }
    server.ListenAndServeTLS("", "")
}
```

## 错误处理

安全模块遵循框架的错误处理规范：

- **401 Unauthorized**: 认证失败（JWT无效、API密钥无效等）
- **403 Forbidden**: 授权失败（权限不足）

## 配置说明

### TLS配置
- `enabled`: 是否启用TLS
- `certFile`: 证书文件路径
- `keyFile`: 密钥文件路径
- `caFile`: CA证书文件路径（可选，用于双向认证）

### JWT配置
- `enabled`: 是否启用JWT认证
- `secret`: JWT签名密钥
- `expiration`: 令牌过期时间
- `issuer`: 令牌签发者

### API密钥配置
- `enabled`: 是否启用API密钥认证

### RBAC配置
- `enabled`: 是否启用RBAC

## 安全最佳实践

1. **密钥管理**
   - 使用环境变量或密钥管理服务存储敏感信息
   - 定期轮换JWT密钥和API密钥
   - 使用强随机密钥

2. **TLS配置**
   - 使用TLS 1.2或更高版本
   - 使用受信任的CA签发的证书
   - 定期更新证书

3. **权限控制**
   - 遵循最小权限原则
   - 定期审查角色和权限
   - 使用细粒度的权限控制

4. **令牌管理**
   - 设置合理的令牌过期时间
   - 实现令牌刷新机制
   - 支持令牌撤销

## 验证需求

本模块实现了以下需求：
- 需求 11.1: TLS/SSL加密通信
- 需求 11.2: 基于令牌的身份认证（JWT）
- 需求 11.3: 基于角色的访问控制（RBAC）
- 需求 11.4: 认证失败返回401错误
- 需求 11.5: 授权失败返回403错误
- 需求 11.6: API密钥认证机制
