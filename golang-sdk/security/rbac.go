package security

import (
	"fmt"
	"sync"
)

// RBACConfig RBAC配置
type RBACConfig struct {
	Enabled bool `json:"enabled" yaml:"enabled"`
}

// Permission 权限
type Permission struct {
	Resource string `json:"resource"`
	Action   string `json:"action"`
}

// Role 角色
type Role struct {
	Name        string       `json:"name"`
	Permissions []Permission `json:"permissions"`
}

// RBACAuthorizer RBAC授权器
type RBACAuthorizer struct {
	config   *RBACConfig
	roles    map[string]*Role
	rolesMux sync.RWMutex
}

// NewRBACAuthorizer 创建RBAC授权器
func NewRBACAuthorizer(config *RBACConfig) (*RBACAuthorizer, error) {
	if config == nil {
		return nil, fmt.Errorf("RBAC config cannot be nil")
	}

	authorizer := &RBACAuthorizer{
		config: config,
		roles:  make(map[string]*Role),
	}

	// 初始化默认角色
	authorizer.initializeDefaultRoles()

	return authorizer, nil
}

// initializeDefaultRoles 初始化默认角色
func (a *RBACAuthorizer) initializeDefaultRoles() {
	// 管理员角色 - 拥有所有权限
	a.roles["admin"] = &Role{
		Name: "admin",
		Permissions: []Permission{
			{Resource: "*", Action: "*"},
		},
	}

	// 用户角色 - 基本读写权限
	a.roles["user"] = &Role{
		Name: "user",
		Permissions: []Permission{
			{Resource: "service", Action: "read"},
			{Resource: "service", Action: "write"},
		},
	}

	// 访客角色 - 只读权限
	a.roles["guest"] = &Role{
		Name: "guest",
		Permissions: []Permission{
			{Resource: "service", Action: "read"},
		},
	}
}

// AddRole 添加角色
func (a *RBACAuthorizer) AddRole(role *Role) error {
	if !a.config.Enabled {
		return fmt.Errorf("RBAC is not enabled")
	}

	if role == nil || role.Name == "" {
		return fmt.Errorf("invalid role")
	}

	a.rolesMux.Lock()
	a.roles[role.Name] = role
	a.rolesMux.Unlock()

	return nil
}

// RemoveRole 移除角色
func (a *RBACAuthorizer) RemoveRole(roleName string) error {
	if !a.config.Enabled {
		return fmt.Errorf("RBAC is not enabled")
	}

	a.rolesMux.Lock()
	delete(a.roles, roleName)
	a.rolesMux.Unlock()

	return nil
}

// GetRole 获取角色
func (a *RBACAuthorizer) GetRole(roleName string) (*Role, error) {
	a.rolesMux.RLock()
	defer a.rolesMux.RUnlock()

	role, exists := a.roles[roleName]
	if !exists {
		return nil, fmt.Errorf("role not found: %s", roleName)
	}

	return role, nil
}

// CheckPermission 检查权限
func (a *RBACAuthorizer) CheckPermission(roles []string, resource, action string) error {
	if !a.config.Enabled {
		return nil // RBAC未启用，允许所有访问
	}

	if len(roles) == 0 {
		return fmt.Errorf("no roles provided")
	}

	a.rolesMux.RLock()
	defer a.rolesMux.RUnlock()

	// 检查用户的所有角色
	for _, roleName := range roles {
		role, exists := a.roles[roleName]
		if !exists {
			continue
		}

		// 检查角色的权限
		for _, perm := range role.Permissions {
			if a.matchPermission(perm, resource, action) {
				return nil // 找到匹配的权限
			}
		}
	}

	return fmt.Errorf("permission denied: resource=%s, action=%s", resource, action)
}

// matchPermission 匹配权限
func (a *RBACAuthorizer) matchPermission(perm Permission, resource, action string) bool {
	// 通配符匹配
	if perm.Resource == "*" && perm.Action == "*" {
		return true
	}

	if perm.Resource == "*" && perm.Action == action {
		return true
	}

	if perm.Resource == resource && perm.Action == "*" {
		return true
	}

	// 精确匹配
	return perm.Resource == resource && perm.Action == action
}

// IsEnabled 检查RBAC是否启用
func (a *RBACAuthorizer) IsEnabled() bool {
	return a.config.Enabled
}
