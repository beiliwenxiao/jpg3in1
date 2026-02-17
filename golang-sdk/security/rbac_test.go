package security

import (
	"testing"
)

func TestNewRBACAuthorizer(t *testing.T) {
	tests := []struct {
		name    string
		config  *RBACConfig
		wantErr bool
	}{
		{
			name:    "nil config",
			config:  nil,
			wantErr: true,
		},
		{
			name: "valid config",
			config: &RBACConfig{
				Enabled: true,
			},
			wantErr: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			auth, err := NewRBACAuthorizer(tt.config)
			if (err != nil) != tt.wantErr {
				t.Errorf("NewRBACAuthorizer() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if !tt.wantErr && auth == nil {
				t.Error("NewRBACAuthorizer() returned nil authorizer")
			}
		})
	}
}

func TestRBACAuthorizer_DefaultRoles(t *testing.T) {
	config := &RBACConfig{
		Enabled: true,
	}

	auth, err := NewRBACAuthorizer(config)
	if err != nil {
		t.Fatalf("NewRBACAuthorizer() error = %v", err)
	}

	// 测试默认角色是否存在
	defaultRoles := []string{"admin", "user", "guest"}
	for _, roleName := range defaultRoles {
		role, err := auth.GetRole(roleName)
		if err != nil {
			t.Errorf("GetRole(%s) error = %v", roleName, err)
		}
		if role == nil {
			t.Errorf("GetRole(%s) returned nil", roleName)
		}
	}
}

func TestRBACAuthorizer_CheckPermission_Admin(t *testing.T) {
	config := &RBACConfig{
		Enabled: true,
	}

	auth, err := NewRBACAuthorizer(config)
	if err != nil {
		t.Fatalf("NewRBACAuthorizer() error = %v", err)
	}

	// 管理员应该有所有权限
	tests := []struct {
		resource string
		action   string
	}{
		{"service", "read"},
		{"service", "write"},
		{"deployment", "delete"},
		{"anything", "anything"},
	}

	for _, tt := range tests {
		err := auth.CheckPermission([]string{"admin"}, tt.resource, tt.action)
		if err != nil {
			t.Errorf("CheckPermission(admin, %s, %s) error = %v", tt.resource, tt.action, err)
		}
	}
}

func TestRBACAuthorizer_CheckPermission_User(t *testing.T) {
	config := &RBACConfig{
		Enabled: true,
	}

	auth, err := NewRBACAuthorizer(config)
	if err != nil {
		t.Fatalf("NewRBACAuthorizer() error = %v", err)
	}

	// 用户角色应该有读写权限
	tests := []struct {
		resource string
		action   string
		wantErr  bool
	}{
		{"service", "read", false},
		{"service", "write", false},
		{"service", "delete", true}, // 用户没有删除权限
		{"deployment", "read", true}, // 用户没有deployment权限
	}

	for _, tt := range tests {
		err := auth.CheckPermission([]string{"user"}, tt.resource, tt.action)
		if (err != nil) != tt.wantErr {
			t.Errorf("CheckPermission(user, %s, %s) error = %v, wantErr %v", tt.resource, tt.action, err, tt.wantErr)
		}
	}
}

func TestRBACAuthorizer_CheckPermission_Guest(t *testing.T) {
	config := &RBACConfig{
		Enabled: true,
	}

	auth, err := NewRBACAuthorizer(config)
	if err != nil {
		t.Fatalf("NewRBACAuthorizer() error = %v", err)
	}

	// 访客角色只有读权限
	tests := []struct {
		resource string
		action   string
		wantErr  bool
	}{
		{"service", "read", false},
		{"service", "write", true},
		{"service", "delete", true},
	}

	for _, tt := range tests {
		err := auth.CheckPermission([]string{"guest"}, tt.resource, tt.action)
		if (err != nil) != tt.wantErr {
			t.Errorf("CheckPermission(guest, %s, %s) error = %v, wantErr %v", tt.resource, tt.action, err, tt.wantErr)
		}
	}
}

func TestRBACAuthorizer_AddRole(t *testing.T) {
	config := &RBACConfig{
		Enabled: true,
	}

	auth, err := NewRBACAuthorizer(config)
	if err != nil {
		t.Fatalf("NewRBACAuthorizer() error = %v", err)
	}

	// 添加自定义角色
	customRole := &Role{
		Name: "developer",
		Permissions: []Permission{
			{Resource: "service", Action: "read"},
			{Resource: "service", Action: "write"},
			{Resource: "deployment", Action: "read"},
		},
	}

	err = auth.AddRole(customRole)
	if err != nil {
		t.Fatalf("AddRole() error = %v", err)
	}

	// 验证角色是否添加成功
	role, err := auth.GetRole("developer")
	if err != nil {
		t.Fatalf("GetRole() error = %v", err)
	}

	if role.Name != "developer" {
		t.Errorf("Role name = %v, want %v", role.Name, "developer")
	}

	if len(role.Permissions) != 3 {
		t.Errorf("Permissions length = %v, want %v", len(role.Permissions), 3)
	}
}

func TestRBACAuthorizer_RemoveRole(t *testing.T) {
	config := &RBACConfig{
		Enabled: true,
	}

	auth, err := NewRBACAuthorizer(config)
	if err != nil {
		t.Fatalf("NewRBACAuthorizer() error = %v", err)
	}

	// 添加角色
	customRole := &Role{
		Name: "temp",
		Permissions: []Permission{
			{Resource: "service", Action: "read"},
		},
	}
	auth.AddRole(customRole)

	// 移除角色
	err = auth.RemoveRole("temp")
	if err != nil {
		t.Fatalf("RemoveRole() error = %v", err)
	}

	// 验证角色是否移除
	_, err = auth.GetRole("temp")
	if err == nil {
		t.Error("GetRole() should return error for removed role")
	}
}

func TestRBACAuthorizer_CheckPermission_MultipleRoles(t *testing.T) {
	config := &RBACConfig{
		Enabled: true,
	}

	auth, err := NewRBACAuthorizer(config)
	if err != nil {
		t.Fatalf("NewRBACAuthorizer() error = %v", err)
	}

	// 用户同时拥有guest和user角色
	err = auth.CheckPermission([]string{"guest", "user"}, "service", "write")
	if err != nil {
		t.Error("CheckPermission() should succeed when any role has permission")
	}
}

func TestRBACAuthorizer_CheckPermission_NoRoles(t *testing.T) {
	config := &RBACConfig{
		Enabled: true,
	}

	auth, err := NewRBACAuthorizer(config)
	if err != nil {
		t.Fatalf("NewRBACAuthorizer() error = %v", err)
	}

	// 没有角色应该返回错误
	err = auth.CheckPermission([]string{}, "service", "read")
	if err == nil {
		t.Error("CheckPermission() should return error when no roles provided")
	}
}

func TestRBACAuthorizer_CheckPermission_Disabled(t *testing.T) {
	config := &RBACConfig{
		Enabled: false,
	}

	auth, err := NewRBACAuthorizer(config)
	if err != nil {
		t.Fatalf("NewRBACAuthorizer() error = %v", err)
	}

	// RBAC未启用时应该允许所有访问
	err = auth.CheckPermission([]string{}, "service", "read")
	if err != nil {
		t.Error("CheckPermission() should succeed when RBAC is disabled")
	}
}

func TestRBACAuthorizer_IsEnabled(t *testing.T) {
	tests := []struct {
		name   string
		config *RBACConfig
		want   bool
	}{
		{
			name: "enabled",
			config: &RBACConfig{
				Enabled: true,
			},
			want: true,
		},
		{
			name: "disabled",
			config: &RBACConfig{
				Enabled: false,
			},
			want: false,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			auth, err := NewRBACAuthorizer(tt.config)
			if err != nil {
				t.Fatalf("NewRBACAuthorizer() error = %v", err)
			}
			if got := auth.IsEnabled(); got != tt.want {
				t.Errorf("IsEnabled() = %v, want %v", got, tt.want)
			}
		})
	}
}
