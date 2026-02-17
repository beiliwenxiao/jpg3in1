package main

import (
	"fmt"
	"log"
	"time"

	"github.com/framework/golang-sdk/security"
)

func main() {
	// 创建安全配置
	config := &security.SecurityConfig{
		TLS: &security.TLSConfig{
			Enabled: false, // 示例中禁用TLS，实际使用时应启用
		},
		JWT: &security.JWTConfig{
			Enabled:    true,
			Secret:     "your-secret-key-change-in-production",
			Expiration: 24 * time.Hour,
			Issuer:     "multi-language-framework",
		},
		APIKey: &security.APIKeyConfig{
			Enabled: true,
		},
		RBAC: &security.RBACConfig{
			Enabled: true,
		},
	}

	// 创建安全管理器
	manager, err := security.NewSecurityManager(config)
	if err != nil {
		log.Fatalf("创建安全管理器失败: %v", err)
	}

	fmt.Println("=== 安全模块示例 ===\n")

	// 1. JWT认证示例
	fmt.Println("1. JWT认证示例")
	demonstrateJWT(manager)

	// 2. API密钥认证示例
	fmt.Println("\n2. API密钥认证示例")
	demonstrateAPIKey(manager)

	// 3. RBAC授权示例
	fmt.Println("\n3. RBAC授权示例")
	demonstrateRBAC(manager)

	// 4. 集成示例：JWT + RBAC
	fmt.Println("\n4. 集成示例：JWT认证 + RBAC授权")
	demonstrateIntegration(manager)
}

func demonstrateJWT(manager *security.SecurityManager) {
	jwtAuth := manager.GetJWTAuthenticator()

	// 生成JWT令牌
	userID := "user123"
	roles := []string{"user", "developer"}
	token, err := jwtAuth.GenerateToken(userID, roles)
	if err != nil {
		log.Printf("生成JWT令牌失败: %v", err)
		return
	}
	fmt.Printf("生成的JWT令牌: %s...\n", token[:50])

	// 验证JWT令牌
	claims, err := manager.AuthenticateJWT(token)
	if err != nil {
		log.Printf("JWT认证失败 (401): %v", err)
		return
	}
	fmt.Printf("JWT认证成功！用户ID: %s, 角色: %v\n", claims.UserID, claims.Roles)

	// 测试无效令牌
	_, err = manager.AuthenticateJWT("invalid-token")
	if err != nil {
		fmt.Printf("无效令牌被正确拒绝 (401): %v\n", err)
	}
}

func demonstrateAPIKey(manager *security.SecurityManager) {
	apiKeyAuth := manager.GetAPIKeyAuthenticator()

	// 生成API密钥
	userID := "service-account-1"
	roles := []string{"service"}
	expiresAt := time.Now().Add(30 * 24 * time.Hour)
	apiKey, err := apiKeyAuth.GenerateAPIKey(userID, roles, expiresAt)
	if err != nil {
		log.Printf("生成API密钥失败: %v", err)
		return
	}
	fmt.Printf("生成的API密钥: %s...\n", apiKey.Key[:32])
	fmt.Printf("过期时间: %s\n", apiKey.ExpiresAt.Format("2006-01-02 15:04:05"))

	// 验证API密钥
	validatedKey, err := manager.AuthenticateAPIKey(apiKey.Key)
	if err != nil {
		log.Printf("API密钥认证失败 (401): %v", err)
		return
	}
	fmt.Printf("API密钥认证成功！用户ID: %s, 角色: %v\n", validatedKey.UserID, validatedKey.Roles)

	// 撤销API密钥
	err = apiKeyAuth.RevokeAPIKey(apiKey.Key)
	if err != nil {
		log.Printf("撤销API密钥失败: %v", err)
		return
	}
	fmt.Println("API密钥已撤销")

	// 测试撤销后的密钥
	_, err = manager.AuthenticateAPIKey(apiKey.Key)
	if err != nil {
		fmt.Printf("撤销的密钥被正确拒绝 (401): %v\n", err)
	}
}

func demonstrateRBAC(manager *security.SecurityManager) {
	rbac := manager.GetRBACAuthorizer()

	// 测试管理员权限
	fmt.Println("测试管理员权限:")
	err := rbac.CheckPermission([]string{"admin"}, "service", "delete")
	if err != nil {
		fmt.Printf("  授权失败 (403): %v\n", err)
	} else {
		fmt.Println("  ✓ 管理员可以删除服务")
	}

	// 测试用户权限
	fmt.Println("测试用户权限:")
	err = rbac.CheckPermission([]string{"user"}, "service", "read")
	if err != nil {
		fmt.Printf("  授权失败 (403): %v\n", err)
	} else {
		fmt.Println("  ✓ 用户可以读取服务")
	}

	err = rbac.CheckPermission([]string{"user"}, "service", "delete")
	if err != nil {
		fmt.Printf("  ✗ 用户不能删除服务 (403): %v\n", err)
	} else {
		fmt.Println("  用户可以删除服务")
	}

	// 测试访客权限
	fmt.Println("测试访客权限:")
	err = rbac.CheckPermission([]string{"guest"}, "service", "read")
	if err != nil {
		fmt.Printf("  授权失败 (403): %v\n", err)
	} else {
		fmt.Println("  ✓ 访客可以读取服务")
	}

	err = rbac.CheckPermission([]string{"guest"}, "service", "write")
	if err != nil {
		fmt.Printf("  ✗ 访客不能写入服务 (403): %v\n", err)
	} else {
		fmt.Println("  访客可以写入服务")
	}

	// 添加自定义角色
	fmt.Println("\n添加自定义角色 'developer':")
	customRole := &security.Role{
		Name: "developer",
		Permissions: []security.Permission{
			{Resource: "service", Action: "read"},
			{Resource: "service", Action: "write"},
			{Resource: "deployment", Action: "read"},
		},
	}
	err = rbac.AddRole(customRole)
	if err != nil {
		log.Printf("添加角色失败: %v", err)
		return
	}
	fmt.Println("  ✓ 自定义角色添加成功")

	err = rbac.CheckPermission([]string{"developer"}, "deployment", "read")
	if err != nil {
		fmt.Printf("  授权失败 (403): %v\n", err)
	} else {
		fmt.Println("  ✓ 开发者可以读取部署信息")
	}
}

func demonstrateIntegration(manager *security.SecurityManager) {
	// 1. 生成JWT令牌（包含角色信息）
	token, err := manager.GetJWTAuthenticator().GenerateToken("user456", []string{"user"})
	if err != nil {
		log.Printf("生成令牌失败: %v", err)
		return
	}
	fmt.Printf("生成JWT令牌: %s...\n", token[:50])

	// 2. 验证JWT令牌
	claims, err := manager.AuthenticateJWT(token)
	if err != nil {
		fmt.Printf("认证失败 (401): %v\n", err)
		return
	}
	fmt.Printf("认证成功！用户: %s, 角色: %v\n", claims.UserID, claims.Roles)

	// 3. 使用令牌中的角色进行授权检查
	fmt.Println("\n授权检查:")
	
	// 用户可以读取服务
	err = manager.Authorize(claims.Roles, "service", "read")
	if err != nil {
		fmt.Printf("  ✗ 读取服务失败 (403): %v\n", err)
	} else {
		fmt.Println("  ✓ 用户可以读取服务")
	}

	// 用户可以写入服务
	err = manager.Authorize(claims.Roles, "service", "write")
	if err != nil {
		fmt.Printf("  ✗ 写入服务失败 (403): %v\n", err)
	} else {
		fmt.Println("  ✓ 用户可以写入服务")
	}

	// 用户不能删除部署
	err = manager.Authorize(claims.Roles, "deployment", "delete")
	if err != nil {
		fmt.Printf("  ✗ 删除部署失败 (403): %v\n", err)
	} else {
		fmt.Println("  用户可以删除部署")
	}

	fmt.Println("\n完整的认证授权流程演示完成！")
}
