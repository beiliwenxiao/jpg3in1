package com.framework.security;

import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于角色的访问控制 (RBAC)
 * 
 * 管理角色和权限的映射关系，验证用户是否有权访问指定资源。
 * 
 * 需求: 11.3, 11.5
 */
public class RbacAuthorizer {

    private static final Logger logger = LoggerFactory.getLogger(RbacAuthorizer.class);

    // 角色 -> 权限集合
    private final ConcurrentHashMap<String, Set<String>> rolePermissions = new ConcurrentHashMap<>();

    /**
     * 定义角色及其权限
     */
    public RbacAuthorizer defineRole(String role, String... permissions) {
        rolePermissions.put(role, new HashSet<>(Arrays.asList(permissions)));
        logger.debug("定义角色: {} -> {}", role, Arrays.asList(permissions));
        return this;
    }

    /**
     * 为角色添加权限
     */
    public RbacAuthorizer addPermission(String role, String permission) {
        rolePermissions.computeIfAbsent(role, k -> ConcurrentHashMap.newKeySet()).add(permission);
        return this;
    }

    /**
     * 检查角色列表是否有指定权限
     * 
     * @param roles 用户角色列表
     * @param requiredPermission 所需权限（支持通配符，如 "service:*"）
     * @return true 如果有权限
     */
    public boolean hasPermission(List<String> roles, String requiredPermission) {
        if (roles == null || roles.isEmpty()) return false;

        for (String role : roles) {
            Set<String> permissions = rolePermissions.get(role);
            if (permissions == null) continue;

            for (String permission : permissions) {
                if (matchPermission(permission, requiredPermission)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 授权检查，失败时抛出异常
     * 
     * @throws FrameworkException 403 Forbidden
     */
    public void authorize(List<String> roles, String requiredPermission) {
        if (!hasPermission(roles, requiredPermission)) {
            throw new FrameworkException(ErrorCode.FORBIDDEN,
                    "权限不足: 需要 " + requiredPermission);
        }
    }

    /**
     * 获取角色的所有权限
     */
    public Set<String> getPermissions(String role) {
        return Collections.unmodifiableSet(
                rolePermissions.getOrDefault(role, Collections.emptySet()));
    }

    /**
     * 获取角色列表的合并权限
     */
    public Set<String> getMergedPermissions(List<String> roles) {
        Set<String> merged = new HashSet<>();
        for (String role : roles) {
            Set<String> perms = rolePermissions.get(role);
            if (perms != null) merged.addAll(perms);
        }
        return Collections.unmodifiableSet(merged);
    }

    public Set<String> getAllRoles() {
        return Collections.unmodifiableSet(rolePermissions.keySet());
    }

    /**
     * 权限匹配（支持通配符 *）
     * 例如: "service:*" 匹配 "service:read", "service:write"
     */
    private boolean matchPermission(String granted, String required) {
        if (granted.equals("*") || granted.equals(required)) return true;

        // 通配符匹配
        if (granted.endsWith(":*")) {
            String prefix = granted.substring(0, granted.length() - 1);
            return required.startsWith(prefix);
        }
        return false;
    }
}
