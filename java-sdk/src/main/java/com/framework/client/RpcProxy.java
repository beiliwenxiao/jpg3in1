package com.framework.client;

import com.framework.config.FrameworkConfig;
import com.framework.protocol.internal.jsonrpc.JsonRpcInternalClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RPC 服务代理
 *
 * 通过配置文件定义远程服务，调用时像本地方法一样简单：
 * <pre>
 *   RpcProxy rpc = RpcProxy.fromConfig("config.yaml");
 *   String msg = rpc.call("php-service", "hello.sayHello", String.class);
 * </pre>
 *
 * 配置示例 (config.yaml):
 * <pre>
 *   framework:
 *     services:
 *       php-service:
 *         host: localhost
 *         port: 8092
 *       go-service:
 *         host: localhost
 *         port: 8093
 * </pre>
 */
public class RpcProxy {

    private static final Logger logger = LoggerFactory.getLogger(RpcProxy.class);

    private final Map<String, JsonRpcInternalClient> clients = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 从配置文件创建 RpcProxy
     */
    public static RpcProxy fromConfig(String configPath) {
        FrameworkConfig config = new FrameworkConfig();
        config.loadFromYaml(configPath);
        return fromConfig(config);
    }

    /**
     * 从 FrameworkConfig 创建 RpcProxy
     */
    public static RpcProxy fromConfig(FrameworkConfig config) {
        RpcProxy proxy = new RpcProxy();
        // 读取 framework.services.* 下的所有服务定义
        Map<String, String> allProps = config.getAllProperties();
        // 收集所有服务名
        java.util.Set<String> serviceNames = new java.util.LinkedHashSet<>();
        String prefix = "framework.services.";
        for (String key : allProps.keySet()) {
            if (key.startsWith(prefix)) {
                String rest = key.substring(prefix.length());
                int dot = rest.indexOf('.');
                if (dot > 0) {
                    serviceNames.add(rest.substring(0, dot));
                }
            }
        }
        for (String name : serviceNames) {
            String host = config.getString(prefix + name + ".host", "localhost");
            int port = config.getInt(prefix + name + ".port", 8080);
            proxy.addService(name, host, port);
        }
        return proxy;
    }

    /**
     * 手动添加远程服务
     */
    public RpcProxy addService(String name, String host, int port) {
        JsonRpcInternalClient client = new JsonRpcInternalClient(host, port);
        client.start();
        clients.put(name, client);
        logger.info("注册远程服务: {} -> {}:{}", name, host, port);
        return this;
    }

    /**
     * 调用远程服务（无参数）
     */
    public <T> T call(String service, String method, Class<T> responseType) {
        return call(service, method, null, responseType);
    }

    /**
     * 调用远程服务（带参数）
     */
    public <T> T call(String service, String method, Object params, Class<T> responseType) {
        JsonRpcInternalClient client = clients.get(service);
        if (client == null) {
            throw new IllegalArgumentException("未知服务: " + service
                    + "，请在配置文件 framework.services 中定义");
        }
        try {
            // 将 method 拆分为 service.method 格式（JsonRpcInternalClient 会拼接）
            // 这里 method 已经是完整的 "hello.sayHello" 格式，直接拆分
            String svc, mtd;
            int dot = method.indexOf('.');
            if (dot > 0) {
                svc = method.substring(0, dot);
                mtd = method.substring(dot + 1);
            } else {
                svc = service;
                mtd = method;
            }

            byte[] payload = (params != null) ? mapper.writeValueAsBytes(params) : null;
            byte[] result = client.call(svc, mtd, payload, Map.of(), 5000);

            if (result == null || result.length == 0) {
                return null;
            }
            return mapper.readValue(result, responseType);
        } catch (Exception e) {
            logger.error("RPC 调用失败: service={}, method={}", service, method, e);
            throw new RuntimeException("RPC 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 关闭所有连接
     */
    public void shutdown() {
        clients.values().forEach(JsonRpcInternalClient::shutdown);
        clients.clear();
    }
}
