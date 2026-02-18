<?php

declare(strict_types=1);

namespace Framework\Security;

use Framework\Errors\FrameworkException;
use Framework\Errors\ErrorCode;

/**
 * 基于角色的访问控制 (RBAC)
 */
class Rbac
{
    private array $roles = [];
    private array $userRoles = [];

    public function defineRole(string $role, array $permissions): void
    {
        $this->roles[$role] = $permissions;
    }

    public function assignRole(string $userId, string $role): void
    {
        if (!isset($this->roles[$role])) {
            throw new FrameworkException("Role not found: $role", ErrorCode::BAD_REQUEST);
        }
        $this->userRoles[$userId][] = $role;
    }

    public function hasPermission(string $userId, string $permission): bool
    {
        $roles = $this->userRoles[$userId] ?? [];
        foreach ($roles as $role) {
            $permissions = $this->roles[$role] ?? [];
            if (in_array($permission, $permissions) || in_array('*', $permissions)) {
                return true;
            }
        }
        return false;
    }

    public function authorize(string $userId, string $permission): void
    {
        if (!$this->hasPermission($userId, $permission)) {
            throw new FrameworkException(
                "Access denied: $userId lacks permission $permission",
                ErrorCode::FORBIDDEN
            );
        }
    }

    public function getUserRoles(string $userId): array
    {
        return $this->userRoles[$userId] ?? [];
    }
}
