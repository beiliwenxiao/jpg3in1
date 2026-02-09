package com.framework.client;

/**
 * FrameworkClient 工厂类
 * 提供创建客户端实例的便捷方法
 */
public class FrameworkClientFactory {
    
    /**
     * 创建默认的 FrameworkClient 实例
     * 
     * @return FrameworkClient 实例
     */
    public static FrameworkClient createClient() {
        return new DefaultFrameworkClient();
    }
    
    /**
     * 创建并启动 FrameworkClient 实例
     * 
     * @return 已启动的 FrameworkClient 实例
     */
    public static FrameworkClient createAndStartClient() {
        FrameworkClient client = new DefaultFrameworkClient();
        client.start();
        return client;
    }
}
