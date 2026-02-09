package com.framework.registry;

import com.framework.model.ServiceInfo;
import com.framework.registry.loadbalance.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 服务注册与发现属性测试
 * 
 * Feature: multi-language-communication-framework
 * 验证需求: 5.1, 5.2, 5.3
 */
class ServiceRegistryPropertyTest {

    // ==================== 属性 13: 服务注册可发现性 ====================

    /**
     * 属性 13: 服务注册可发现性
     * 
     * 对于任意服务，启动并注册后，应该能够通过服务注册中心查询到该服务的元数据。
     * 
     * **Validates: Requirements 5.1**
     */
    @Property(tries = 100)
    @Label("Feature: multi-language-communication-framework, Property 13: 服务注册可发现性")
    void registeredServiceShouldBeDiscoverable(
            @ForAll("serviceInfos") ServiceInfo serviceInfo
    ) {
        InMemoryServiceRegistry registry = new InMemoryServiceRegistry();
        registry.start();

        try {
            // 注册服务
            registry.register(serviceInfo);

            // 发现服务
            List<ServiceInfo> discovered = registry.discover(serviceInfo.getName());

            // 验证: 注册的服务应该能被发现
            assertFalse(discovered.isEmpty(), "注册后应该能发现服务");

            boolean found = discovered.stream()
                    .anyMatch(s -> s.getId().equals(serviceInfo.getId()));
            assertTrue(found, "应该能通过服务名称发现已注册的服务实例");

            // 验证元数据一致性
            ServiceInfo foundService = discovered.stream()
                    .filter(s -> s.getId().equals(serviceInfo.getId()))
                    .findFirst()
                    .orElseThrow();

            assertEquals(serviceInfo.getName(), foundService.getName(), "服务名称应一致");
            assertEquals(serviceInfo.getAddress(), foundService.getAddress(), "服务地址应一致");
            assertEquals(serviceInfo.getPort(), foundService.getPort(), "服务端口应一致");
            assertEquals(serviceInfo.getVersion(), foundService.getVersion(), "服务版本应一致");
            assertEquals(serviceInfo.getLanguage(), foundService.getLanguage(), "服务语言应一致");
        } finally {
            registry.close();
        }
    }

    /**
     * 属性 13 扩展: 多个服务注册后都应该可发现
     * 
     * **Validates: Requirements 5.1**
     */
    @Property(tries = 100)
    @Label("Feature: multi-language-communication-framework, Property 13: 多服务注册可发现性")
    void multipleRegisteredServicesShouldAllBeDiscoverable(
            @ForAll @IntRange(min = 1, max = 10) int count
    ) {
        InMemoryServiceRegistry registry = new InMemoryServiceRegistry();
        registry.start();

        try {
            String serviceName = "test-service";
            List<ServiceInfo> registered = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                ServiceInfo info = createServiceInfo(
                        "svc-" + i, serviceName, "1.0.0", "java",
                        "192.168.1." + (i + 1), 8080 + i
                );
                registry.register(info);
                registered.add(info);
            }

            List<ServiceInfo> discovered = registry.discover(serviceName);

            // 验证: 所有注册的服务都应该被发现
            assertEquals(count, discovered.size(), "发现的服务数量应等于注册数量");

            Set<String> discoveredIds = discovered.stream()
                    .map(ServiceInfo::getId)
                    .collect(Collectors.toSet());

            for (ServiceInfo info : registered) {
                assertTrue(discoveredIds.contains(info.getId()),
                        "服务 " + info.getId() + " 应该被发现");
            }
        } finally {
            registry.close();
        }
    }

    // ==================== 属性 14: 服务注销不可见性 ====================

    /**
     * 属性 14: 服务注销不可见性
     * 
     * 对于任意已注册服务，注销后，服务注册中心不应该再返回该服务。
     * 
     * **Validates: Requirements 5.2**
     */
    @Property(tries = 100)
    @Label("Feature: multi-language-communication-framework, Property 14: 服务注销不可见性")
    void deregisteredServiceShouldNotBeDiscoverable(
            @ForAll("serviceInfos") ServiceInfo serviceInfo
    ) {
        InMemoryServiceRegistry registry = new InMemoryServiceRegistry();
        registry.start();

        try {
            // 注册服务
            registry.register(serviceInfo);

            // 确认已注册
            List<ServiceInfo> beforeDeregister = registry.discover(serviceInfo.getName());
            assertTrue(beforeDeregister.stream().anyMatch(s -> s.getId().equals(serviceInfo.getId())),
                    "注销前服务应该可发现");

            // 注销服务
            registry.deregister(serviceInfo.getId());

            // 验证: 注销后不应该再被发现
            List<ServiceInfo> afterDeregister = registry.discover(serviceInfo.getName());
            boolean stillFound = afterDeregister.stream()
                    .anyMatch(s -> s.getId().equals(serviceInfo.getId()));
            assertFalse(stillFound, "注销后服务不应该再被发现");
        } finally {
            registry.close();
        }
    }

    /**
     * 属性 14 扩展: 注销一个服务不影响其他服务
     * 
     * **Validates: Requirements 5.2**
     */
    @Property(tries = 100)
    @Label("Feature: multi-language-communication-framework, Property 14: 注销不影响其他服务")
    void deregisterOneShouldNotAffectOthers(
            @ForAll @IntRange(min = 2, max = 8) int count,
            @ForAll @IntRange(min = 0, max = 7) int removeIndex
    ) {
        Assume.that(removeIndex < count);

        InMemoryServiceRegistry registry = new InMemoryServiceRegistry();
        registry.start();

        try {
            String serviceName = "test-service";
            List<ServiceInfo> registered = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                ServiceInfo info = createServiceInfo(
                        "svc-" + i, serviceName, "1.0.0", "java",
                        "192.168.1." + (i + 1), 8080 + i
                );
                registry.register(info);
                registered.add(info);
            }

            // 注销一个服务
            String removedId = registered.get(removeIndex).getId();
            registry.deregister(removedId);

            // 验证: 其他服务仍然可发现
            List<ServiceInfo> discovered = registry.discover(serviceName);
            assertEquals(count - 1, discovered.size(), "应该少一个服务");

            Set<String> discoveredIds = discovered.stream()
                    .map(ServiceInfo::getId)
                    .collect(Collectors.toSet());

            assertFalse(discoveredIds.contains(removedId), "被注销的服务不应该被发现");

            for (int i = 0; i < count; i++) {
                if (i != removeIndex) {
                    assertTrue(discoveredIds.contains(registered.get(i).getId()),
                            "未注销的服务应该仍然可发现");
                }
            }
        } finally {
            registry.close();
        }
    }

    // ==================== 属性 15: 服务发现返回可用实例 ====================

    /**
     * 属性 15: 服务发现返回可用实例
     * 
     * 对于任意服务查询，服务注册中心应该只返回已注册且健康的服务实例。
     * 
     * **Validates: Requirements 5.3**
     */
    @Property(tries = 100)
    @Label("Feature: multi-language-communication-framework, Property 15: 服务发现返回可用实例")
    void discoverShouldOnlyReturnHealthyInstances(
            @ForAll @IntRange(min = 1, max = 10) int totalCount,
            @ForAll @IntRange(min = 0, max = 9) int unhealthyCount
    ) {
        Assume.that(unhealthyCount <= totalCount);

        InMemoryServiceRegistry registry = new InMemoryServiceRegistry();
        registry.start();

        try {
            String serviceName = "test-service";
            Set<String> unhealthyIds = new HashSet<>();

            for (int i = 0; i < totalCount; i++) {
                ServiceInfo info = createServiceInfo(
                        "svc-" + i, serviceName, "1.0.0", "java",
                        "192.168.1." + (i + 1), 8080 + i
                );
                registry.register(info);

                // 将部分服务标记为不健康
                if (i < unhealthyCount) {
                    registry.updateHealthStatus(info.getId(), HealthStatus.UNHEALTHY);
                    unhealthyIds.add(info.getId());
                }
            }

            // 发现服务
            List<ServiceInfo> discovered = registry.discover(serviceName);

            // 验证: 只返回健康的实例
            int expectedHealthy = totalCount - unhealthyCount;
            assertEquals(expectedHealthy, discovered.size(),
                    "应该只返回健康的服务实例");

            // 验证: 返回的实例都不在不健康列表中
            for (ServiceInfo info : discovered) {
                assertFalse(unhealthyIds.contains(info.getId()),
                        "不健康的服务不应该被返回");
                assertEquals(HealthStatus.HEALTHY, registry.getHealthStatus(info.getId()),
                        "返回的服务应该是健康状态");
            }
        } finally {
            registry.close();
        }
    }

    /**
     * 属性 15 扩展: 服务恢复健康后应该重新可发现
     * 
     * **Validates: Requirements 5.3**
     */
    @Property(tries = 100)
    @Label("Feature: multi-language-communication-framework, Property 15: 恢复健康后可发现")
    void recoveredServiceShouldBeDiscoverable(
            @ForAll("serviceInfos") ServiceInfo serviceInfo
    ) {
        InMemoryServiceRegistry registry = new InMemoryServiceRegistry();
        registry.start();

        try {
            registry.register(serviceInfo);

            // 标记为不健康
            registry.updateHealthStatus(serviceInfo.getId(), HealthStatus.UNHEALTHY);
            List<ServiceInfo> unhealthyResult = registry.discover(serviceInfo.getName());
            assertFalse(unhealthyResult.stream().anyMatch(s -> s.getId().equals(serviceInfo.getId())),
                    "不健康的服务不应该被发现");

            // 恢复健康
            registry.updateHealthStatus(serviceInfo.getId(), HealthStatus.HEALTHY);
            List<ServiceInfo> healthyResult = registry.discover(serviceInfo.getName());
            assertTrue(healthyResult.stream().anyMatch(s -> s.getId().equals(serviceInfo.getId())),
                    "恢复健康后服务应该重新可发现");
        } finally {
            registry.close();
        }
    }

    // ==================== 数据生成器 ====================

    @Provide
    Arbitrary<ServiceInfo> serviceInfos() {
        Arbitrary<String> ids = Arbitraries.strings()
                .alpha().ofMinLength(3).ofMaxLength(16)
                .map(s -> "svc-" + s);
        Arbitrary<String> names = Arbitraries.of(
                "user-service", "order-service", "payment-service",
                "auth-service", "gateway-service", "config-service"
        );
        Arbitrary<String> versions = Arbitraries.of("1.0.0", "1.1.0", "2.0.0", "2.1.0");
        Arbitrary<String> languages = Arbitraries.of("java", "golang", "php");
        Arbitrary<String> addresses = Arbitraries.integers()
                .between(1, 254)
                .map(i -> "192.168.1." + i);
        Arbitrary<Integer> ports = Arbitraries.integers().between(1024, 65535);

        return Combinators.combine(ids, names, versions, languages, addresses, ports)
                .as((id, name, version, language, address, port) -> {
                    ServiceInfo info = new ServiceInfo();
                    info.setId(id);
                    info.setName(name);
                    info.setVersion(version);
                    info.setLanguage(language);
                    info.setAddress(address);
                    info.setPort(port);
                    info.setProtocols(Arrays.asList("gRPC", "JSON-RPC"));
                    return info;
                });
    }

    private ServiceInfo createServiceInfo(String id, String name, String version,
                                           String language, String address, int port) {
        ServiceInfo info = new ServiceInfo();
        info.setId(id);
        info.setName(name);
        info.setVersion(version);
        info.setLanguage(language);
        info.setAddress(address);
        info.setPort(port);
        info.setProtocols(Arrays.asList("gRPC", "JSON-RPC"));
        return info;
    }
}
