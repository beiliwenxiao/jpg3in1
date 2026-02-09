package com.framework.client;

import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;
import com.framework.model.ServiceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * FrameworkClient 的默认实现
 * 提供同步、异步和流式调用功能
 */
public class DefaultFrameworkClient implements FrameworkClient {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultFrameworkClient.class);
    
    private final Map<String, Object> registeredServices;
    private final ExecutorService executorService;
    private volatile boolean started;
    private volatile boolean shutdown;
    
    public DefaultFrameworkClient() {
        this.registeredServices = new ConcurrentHashMap<>();
        this.executorService = Executors.newCachedThreadPool();
        this.started = false;
        this.shutdown = false;
    }
    
    @Override
    public <T> T call(String service, String method, Object request, Class<T> responseType) {
        validateState();
        validateParameters(service, method, responseType);
        
        logger.debug("同步调用服务: service={}, method={}", service, method);
        
        try {
            // 查找服务实例
            Object serviceInstance = findService(service);
            
            // 调用服务方法
            Object result = invokeServiceMethod(serviceInstance, method, request);
            
            // 转换响应类型
            return convertResponse(result, responseType);
            
        } catch (FrameworkException e) {
            logger.error("服务调用失败: service={}, method={}, error={}", 
                        service, method, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("服务调用异常: service={}, method={}", service, method, e);
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, 
                                        "服务调用失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public <T> CompletableFuture<T> callAsync(String service, String method, 
                                               Object request, Class<T> responseType) {
        validateState();
        validateParameters(service, method, responseType);
        
        logger.debug("异步调用服务: service={}, method={}", service, method);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return call(service, method, request, responseType);
            } catch (Exception e) {
                logger.error("异步服务调用失败: service={}, method={}", service, method, e);
                throw new FrameworkException(ErrorCode.INTERNAL_ERROR, 
                                            "异步服务调用失败: " + e.getMessage(), e);
            }
        }, executorService);
    }
    
    @Override
    public <T> Stream<T> stream(String service, String method, Object request, Class<T> responseType) {
        validateState();
        validateParameters(service, method, responseType);
        
        logger.debug("流式调用服务: service={}, method={}", service, method);
        
        try {
            // 查找服务实例
            Object serviceInstance = findService(service);
            
            // 调用服务方法获取流
            Object result = invokeServiceMethod(serviceInstance, method, request);
            
            // 转换为流
            return convertToStream(result, responseType);
            
        } catch (FrameworkException e) {
            logger.error("流式调用失败: service={}, method={}, error={}", 
                        service, method, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            logger.error("流式调用异常: service={}, method={}", service, method, e);
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, 
                                        "流式调用失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void registerService(String name, Object implementation) {
        if (name == null || name.trim().isEmpty()) {
            throw new FrameworkException(ErrorCode.BAD_REQUEST, "服务名称不能为空");
        }
        
        if (implementation == null) {
            throw new FrameworkException(ErrorCode.BAD_REQUEST, "服务实现不能为空");
        }
        
        logger.info("注册服务: name={}, implementation={}", name, implementation.getClass().getName());
        
        registeredServices.put(name, implementation);
    }
    
    @Override
    public void start() {
        if (started) {
            logger.warn("客户端已经启动");
            return;
        }
        
        if (shutdown) {
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, "客户端已关闭，无法重新启动");
        }
        
        logger.info("启动 FrameworkClient");
        
        // 初始化连接
        initializeConnections();
        
        started = true;
        logger.info("FrameworkClient 启动成功");
    }
    
    @Override
    public void shutdown() {
        if (shutdown) {
            logger.warn("客户端已经关闭");
            return;
        }
        
        logger.info("关闭 FrameworkClient");
        
        // 关闭执行器
        executorService.shutdown();
        
        // 清理注册的服务
        registeredServices.clear();
        
        shutdown = true;
        started = false;
        
        logger.info("FrameworkClient 已关闭");
    }
    
    /**
     * 验证客户端状态
     */
    private void validateState() {
        if (shutdown) {
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, "客户端已关闭");
        }
        
        if (!started) {
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, "客户端未启动");
        }
    }
    
    /**
     * 验证参数
     */
    private void validateParameters(String service, String method, Class<?> responseType) {
        if (service == null || service.trim().isEmpty()) {
            throw new FrameworkException(ErrorCode.BAD_REQUEST, "服务名称不能为空");
        }
        
        if (method == null || method.trim().isEmpty()) {
            throw new FrameworkException(ErrorCode.BAD_REQUEST, "方法名称不能为空");
        }
        
        if (responseType == null) {
            throw new FrameworkException(ErrorCode.BAD_REQUEST, "响应类型不能为空");
        }
    }
    
    /**
     * 查找服务实例
     */
    private Object findService(String serviceName) {
        Object serviceInstance = registeredServices.get(serviceName);
        
        if (serviceInstance == null) {
            throw new FrameworkException(ErrorCode.NOT_FOUND, 
                                        "服务未找到: " + serviceName);
        }
        
        return serviceInstance;
    }
    
    /**
     * 调用服务方法
     */
    private Object invokeServiceMethod(Object serviceInstance, String methodName, Object request) {
        try {
            // 使用反射调用方法
            java.lang.reflect.Method[] methods = serviceInstance.getClass().getMethods();
            
            for (java.lang.reflect.Method method : methods) {
                if (method.getName().equals(methodName)) {
                    // 找到匹配的方法
                    if (request == null) {
                        return method.invoke(serviceInstance);
                    } else {
                        return method.invoke(serviceInstance, request);
                    }
                }
            }
            
            throw new FrameworkException(ErrorCode.NOT_FOUND, 
                                        "方法未找到: " + methodName);
            
        } catch (FrameworkException e) {
            throw e;
        } catch (Exception e) {
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, 
                                        "方法调用失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 转换响应类型
     */
    @SuppressWarnings("unchecked")
    private <T> T convertResponse(Object result, Class<T> responseType) {
        if (result == null) {
            return null;
        }
        
        if (responseType.isInstance(result)) {
            return (T) result;
        }
        
        // 尝试类型转换
        try {
            return responseType.cast(result);
        } catch (ClassCastException e) {
            throw new FrameworkException(ErrorCode.SERIALIZATION_ERROR, 
                                        "响应类型转换失败: 期望 " + responseType.getName() + 
                                        ", 实际 " + result.getClass().getName());
        }
    }
    
    /**
     * 转换为流
     */
    @SuppressWarnings("unchecked")
    private <T> Stream<T> convertToStream(Object result, Class<T> responseType) {
        if (result == null) {
            return Stream.empty();
        }
        
        if (result instanceof Stream) {
            return (Stream<T>) result;
        }
        
        if (result instanceof Iterable) {
            Iterable<?> iterable = (Iterable<?>) result;
            Stream.Builder<T> builder = Stream.builder();
            for (Object item : iterable) {
                builder.add(convertResponse(item, responseType));
            }
            return builder.build();
        }
        
        // 单个元素转换为流
        return Stream.of(convertResponse(result, responseType));
    }
    
    /**
     * 初始化连接
     */
    private void initializeConnections() {
        // 这里将来会初始化与服务注册中心、消息路由器等的连接
        logger.debug("初始化连接");
    }
}
