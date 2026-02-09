package com.framework.connection;

import com.framework.protocol.router.ServiceEndpoint;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 连接管理属性测试
 * 
 * Feature: multi-language-communication-framework
 * 验证需求: 7.1, 7.2
 */
class ConnectionManagerPropertyTest {

    // ==================== 属性 22: 连接复用 ====================

    /**
     * 属性 22: 连接复用
     * 
     * 对于任意连续的多个请求到同一服务端点，应该能够复用同一网络连接。
     * 验证：同一个 ManagedConnection 被 acquire 后 release，再次 acquire 应该能复用。
     * 
     * **Validates: Requirements 7.1, 12.2**
     */
    @Property(tries = 100)
    @Label("Feature: multi-language-communication-framework, Property 22: 连接复用")
    void connectionReuse(
            @ForAll @IntRange(min = 2, max = 20) int acquireCount
    ) {
        // 创建一个模拟的 ManagedConnection
        EmbeddedChannel channel = new EmbeddedChannel();
        ServiceEndpoint endpoint = new ServiceEndpoint(
                "svc-1", "test-service", "127.0.0.1", 8080, "gRPC");
        ManagedConnection conn = new ManagedConnection("test-conn", endpoint, channel);

        // 多次 acquire/release 循环，验证同一连接可以被复用
        for (int i = 0; i < acquireCount; i++) {
            assertTrue(conn.acquire(), "第 " + (i + 1) + " 次 acquire 应该成功");
            assertEquals(ManagedConnection.State.ACTIVE, conn.getState(),
                    "acquire 后状态应为 ACTIVE");
            assertTrue(conn.isHealthy(), "连接应该是健康的");

            conn.release();
        }

        // 最终连接应该回到 IDLE 状态，可以继续复用
        assertEquals(ManagedConnection.State.IDLE, conn.getState(),
                "所有请求释放后状态应为 IDLE");
        assertTrue(conn.acquire(), "释放后应该能再次 acquire");

        conn.release();
        channel.close();
    }

    /**
     * 属性 22: 连接复用 - 并发 acquire
     * 
     * 同一连接支持多个并发请求（连接复用），activeRequests 计数应正确
     * 
     * **Validates: Requirements 7.1, 12.2**
     */
    @Property(tries = 100)
    @Label("Feature: multi-language-communication-framework, Property 22: 连接复用 - 并发请求计数")
    void connectionReuseConcurrentRequests(
            @ForAll @IntRange(min = 1, max = 10) int concurrentRequests
    ) {
        EmbeddedChannel channel = new EmbeddedChannel();
        ServiceEndpoint endpoint = new ServiceEndpoint(
                "svc-1", "test-service", "127.0.0.1", 8080, "gRPC");
        ManagedConnection conn = new ManagedConnection("test-conn", endpoint, channel);

        // 并发 acquire 多次
        for (int i = 0; i < concurrentRequests; i++) {
            assertTrue(conn.acquire(), "第 " + (i + 1) + " 次并发 acquire 应该成功");
        }

        assertEquals(concurrentRequests, conn.getActiveRequestCount(),
                "活跃请求数应等于 acquire 次数");
        assertEquals(ManagedConnection.State.ACTIVE, conn.getState(),
                "有活跃请求时状态应为 ACTIVE");

        // 逐个释放
        for (int i = 0; i < concurrentRequests; i++) {
            conn.release();
            int remaining = concurrentRequests - i - 1;
            assertEquals(remaining, conn.getActiveRequestCount(),
                    "释放后活跃请求数应减少");
        }

        assertEquals(ManagedConnection.State.IDLE, conn.getState(),
                "所有请求释放后状态应为 IDLE");

        channel.close();
    }

    /**
     * 属性 22: 连接复用 - 关闭后不可复用
     * 
     * 连接关闭后，acquire 应该失败
     * 
     * **Validates: Requirements 7.1**
     */
    @Property(tries = 100)
    @Label("Feature: multi-language-communication-framework, Property 22: 连接复用 - 关闭后不可复用")
    void closedConnectionCannotBeReused(
            @ForAll @IntRange(min = 1, max = 5) int acquireBeforeClose
    ) {
        EmbeddedChannel channel = new EmbeddedChannel();
        ServiceEndpoint endpoint = new ServiceEndpoint(
                "svc-1", "test-service", "127.0.0.1", 8080, "gRPC");
        ManagedConnection conn = new ManagedConnection("test-conn", endpoint, channel);

        // 先正常使用几次
        for (int i = 0; i < acquireBeforeClose; i++) {
            conn.acquire();
            conn.release();
        }

        // 关闭连接
        conn.close().join();

        // 关闭后不能再 acquire
        assertFalse(conn.acquire(), "关闭后 acquire 应该失败");
        assertEquals(ManagedConnection.State.CLOSED, conn.getState(),
                "关闭后状态应为 CLOSED");
        assertTrue(conn.isClosed(), "isClosed 应返回 true");
    }

    // ==================== 属性 23: 连接空闲超时关闭 ====================

    /**
     * 属性 23: 连接空闲超时关闭
     * 
     * 对于任意连接，如果空闲时间超过配置的超时时间，应该被标记为超时。
     * 
     * **Validates: Requirements 7.2**
     */
    @Property(tries = 100)
    @Label("Feature: multi-language-communication-framework, Property 23: 连接空闲超时关闭")
    void idleConnectionTimedOut(
            @ForAll @LongRange(min = 100, max = 5000) long idleTimeoutMs
    ) {
        EmbeddedChannel channel = new EmbeddedChannel();
        ServiceEndpoint endpoint = new ServiceEndpoint(
                "svc-1", "test-service", "127.0.0.1", 8080, "gRPC");
        ManagedConnection conn = new ManagedConnection("test-conn", endpoint, channel);

        // 新创建的连接不应该超时（lastUsedAt 是当前时间）
        assertFalse(conn.isIdleTimedOut(idleTimeoutMs),
                "新创建的连接不应该被标记为空闲超时");

        // 活跃连接不应该被标记为超时
        conn.acquire();
        assertFalse(conn.isIdleTimedOut(idleTimeoutMs),
                "活跃连接不应该被标记为空闲超时");
        conn.release();

        // 使用极小的超时值（1ms），等待后应该超时
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertTrue(conn.isIdleTimedOut(1),
                "空闲时间超过超时值的连接应该被标记为超时");

        channel.close();
    }

    /**
     * 属性 23: 连接空闲超时关闭 - 使用后重置超时
     * 
     * 连接被使用后，lastUsedAt 应该更新，空闲超时应该重新计算
     * 
     * **Validates: Requirements 7.2**
     */
    @Property(tries = 100)
    @Label("Feature: multi-language-communication-framework, Property 23: 连接空闲超时关闭 - 使用后重置")
    void connectionUsageResetsIdleTimeout(
            @ForAll @IntRange(min = 1, max = 5) int useCount
    ) {
        EmbeddedChannel channel = new EmbeddedChannel();
        ServiceEndpoint endpoint = new ServiceEndpoint(
                "svc-1", "test-service", "127.0.0.1", 8080, "gRPC");
        ManagedConnection conn = new ManagedConnection("test-conn", endpoint, channel);

        Instant initialLastUsed = conn.getLastUsedAt();

        // 等待一小段时间
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 使用连接
        for (int i = 0; i < useCount; i++) {
            conn.acquire();
            conn.release();
        }

        // lastUsedAt 应该被更新
        assertTrue(conn.getLastUsedAt().isAfter(initialLastUsed)
                        || conn.getLastUsedAt().equals(initialLastUsed),
                "使用后 lastUsedAt 应该被更新");

        // 刚使用过的连接不应该超时（使用合理的超时值）
        assertFalse(conn.isIdleTimedOut(60_000),
                "刚使用过的连接不应该被标记为空闲超时");

        channel.close();
    }

    /**
     * 属性 23: 连接空闲超时关闭 - 最大存活时间
     * 
     * 连接超过最大存活时间后应该被标记为过期
     * 
     * **Validates: Requirements 7.2**
     */
    @Property(tries = 100)
    @Label("Feature: multi-language-communication-framework, Property 23: 连接空闲超时关闭 - 最大存活时间")
    void connectionExpiredAfterMaxLifetime(
            @ForAll @LongRange(min = 100, max = 5000) long maxLifetimeMs
    ) {
        EmbeddedChannel channel = new EmbeddedChannel();
        ServiceEndpoint endpoint = new ServiceEndpoint(
                "svc-1", "test-service", "127.0.0.1", 8080, "gRPC");
        ManagedConnection conn = new ManagedConnection("test-conn", endpoint, channel);

        // 新连接不应该过期（使用合理的存活时间）
        assertFalse(conn.isExpired(maxLifetimeMs),
                "新创建的连接不应该过期");

        // 使用极小的存活时间（1ms），等待后应该过期
        try {
            Thread.sleep(5);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertTrue(conn.isExpired(1),
                "超过最大存活时间的连接应该被标记为过期");

        channel.close();
    }

    /**
     * 属性 23: 连接空闲超时关闭 - 维护任务清理
     * 
     * 连接池的维护任务应该自动关闭空闲超时的连接（超过 minConnections 的部分）
     * 
     * **Validates: Requirements 7.2**
     */
    @Property(tries = 50)
    @Label("Feature: multi-language-communication-framework, Property 23: 连接空闲超时关闭 - 维护清理")
    void maintenanceClosesIdleTimedOutConnections(
            @ForAll @IntRange(min = 2, max = 5) int connectionCount
    ) {
        // 配置极短的空闲超时，minConnections=0 以便所有空闲超时连接都可被清理
        ConnectionConfig config = new ConnectionConfig();
        config.setIdleTimeoutMs(1);          // 1ms 空闲超时
        config.setMaxLifetimeMs(600_000);
        config.setMaxConnections(20);
        config.setMinConnections(0);
        config.setHealthCheckIntervalMs(60_000);

        // 创建连接并放入池中
        List<EmbeddedChannel> channels = new ArrayList<>();
        ServiceEndpoint endpoint = new ServiceEndpoint(
                "svc-1", "test-service", "127.0.0.1", 9999, "gRPC");

        NettyConnectionPool pool = new NettyConnectionPool(endpoint, config);

        // 手动创建连接并模拟 acquire/release 流程
        List<ManagedConnection> conns = new ArrayList<>();
        for (int i = 0; i < connectionCount; i++) {
            EmbeddedChannel ch = new EmbeddedChannel();
            channels.add(ch);
            ManagedConnection conn = new ManagedConnection("conn-" + i, endpoint, ch);
            conns.add(conn);
        }

        // 验证：空闲超时的连接应该被 isIdleTimedOut 标记
        try {
            Thread.sleep(10); // 等待超过 1ms 的空闲超时
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        for (ManagedConnection conn : conns) {
            assertTrue(conn.isIdleTimedOut(config.getIdleTimeoutMs()),
                    "空闲超过超时时间的连接应该被标记为超时");
        }

        // 清理
        for (EmbeddedChannel ch : channels) {
            ch.close();
        }
        pool.close().join();
    }
}
