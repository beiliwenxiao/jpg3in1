package com.framework.registry;

/**
 * 服务健康状态枚举
 * 
 * 需求: 5.4
 */
public enum HealthStatus {

    /** 健康 */
    HEALTHY("healthy"),

    /** 不健康 */
    UNHEALTHY("unhealthy"),

    /** 未知 */
    UNKNOWN("unknown");

    private final String value;

    HealthStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static HealthStatus fromValue(String value) {
        for (HealthStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        return UNKNOWN;
    }
}
