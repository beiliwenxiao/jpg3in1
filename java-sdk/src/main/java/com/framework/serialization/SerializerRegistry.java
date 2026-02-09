package com.framework.serialization;

import com.framework.serialization.json.JsonSerializer;
import com.framework.serialization.protobuf.ProtobufSerializer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 序列化器注册表
 * 
 * 管理序列化器的注册、查找和生命周期
 * 支持自定义序列化器的扩展
 * 
 * 需求: 6.5
 */
public class SerializerRegistry {
    
    // 按格式存储的序列化器
    private final Map<SerializationFormat, Serializer> serializersByFormat = new ConcurrentHashMap<>();
    
    // 按类型存储的自定义序列化器
    private final Map<Class<?>, Serializer> serializersByType = new ConcurrentHashMap<>();
    
    // 默认序列化器
    private volatile Serializer defaultSerializer;
    
    // 单例实例
    private static volatile SerializerRegistry instance;
    
    /**
     * 私有构造函数，初始化默认序列化器
     */
    private SerializerRegistry() {
        // 注册默认的 JSON 序列化器
        JsonSerializer jsonSerializer = new JsonSerializer();
        registerSerializer(jsonSerializer);
        setDefaultSerializer(jsonSerializer);
        
        // 注册 Protobuf 序列化器
        ProtobufSerializer protobufSerializer = new ProtobufSerializer();
        registerSerializer(protobufSerializer);
    }
    
    /**
     * 获取单例实例
     * 
     * @return SerializerRegistry 实例
     */
    public static SerializerRegistry getInstance() {
        if (instance == null) {
            synchronized (SerializerRegistry.class) {
                if (instance == null) {
                    instance = new SerializerRegistry();
                }
            }
        }
        return instance;
    }
    
    /**
     * 创建新的注册表实例（用于测试或隔离场景）
     * 
     * @return 新的 SerializerRegistry 实例
     */
    public static SerializerRegistry createNew() {
        return new SerializerRegistry();
    }
    
    /**
     * 注册序列化器
     * 
     * @param serializer 要注册的序列化器
     */
    public void registerSerializer(Serializer serializer) {
        Objects.requireNonNull(serializer, "Serializer cannot be null");
        
        // 按格式注册
        for (SerializationFormat format : serializer.getSupportedFormats()) {
            serializersByFormat.put(format, serializer);
        }
    }
    
    /**
     * 为特定类型注册序列化器
     * 
     * @param type 类型
     * @param serializer 序列化器
     */
    public void registerSerializerForType(Class<?> type, Serializer serializer) {
        Objects.requireNonNull(type, "Type cannot be null");
        Objects.requireNonNull(serializer, "Serializer cannot be null");
        
        serializersByType.put(type, serializer);
    }
    
    /**
     * 注销序列化器
     * 
     * @param format 要注销的格式
     * @return 被注销的序列化器，如果不存在返回 null
     */
    public Serializer unregisterSerializer(SerializationFormat format) {
        return serializersByFormat.remove(format);
    }
    
    /**
     * 注销特定类型的序列化器
     * 
     * @param type 类型
     * @return 被注销的序列化器，如果不存在返回 null
     */
    public Serializer unregisterSerializerForType(Class<?> type) {
        return serializersByType.remove(type);
    }
    
    /**
     * 根据格式获取序列化器
     * 
     * @param format 序列化格式
     * @return 对应的序列化器
     * @throws SerializationException 如果找不到对应的序列化器
     */
    public Serializer getSerializer(SerializationFormat format) throws SerializationException {
        Serializer serializer = serializersByFormat.get(format);
        if (serializer == null) {
            throw new SerializationException(
                "No serializer registered for format: " + format,
                format
            );
        }
        return serializer;
    }
    
    /**
     * 根据格式获取序列化器（可选）
     * 
     * @param format 序列化格式
     * @return 对应的序列化器，如果不存在返回 Optional.empty()
     */
    public Optional<Serializer> findSerializer(SerializationFormat format) {
        return Optional.ofNullable(serializersByFormat.get(format));
    }
    
    /**
     * 根据类型获取序列化器
     * 
     * @param type 目标类型
     * @return 对应的序列化器
     * @throws SerializationException 如果找不到对应的序列化器
     */
    public Serializer getSerializerForType(Class<?> type) throws SerializationException {
        // 首先检查是否有为该类型专门注册的序列化器
        Serializer serializer = serializersByType.get(type);
        if (serializer != null) {
            return serializer;
        }
        
        // 检查父类和接口
        for (Map.Entry<Class<?>, Serializer> entry : serializersByType.entrySet()) {
            if (entry.getKey().isAssignableFrom(type)) {
                return entry.getValue();
            }
        }
        
        // 查找支持该类型的序列化器
        for (Serializer s : serializersByFormat.values()) {
            if (s.supportsType(type)) {
                return s;
            }
        }
        
        // 返回默认序列化器
        if (defaultSerializer != null && defaultSerializer.supportsType(type)) {
            return defaultSerializer;
        }
        
        throw new SerializationException(
            "No serializer found for type: " + type.getName()
        );
    }
    
    /**
     * 根据类型获取序列化器（可选）
     * 
     * @param type 目标类型
     * @return 对应的序列化器，如果不存在返回 Optional.empty()
     */
    public Optional<Serializer> findSerializerForType(Class<?> type) {
        try {
            return Optional.of(getSerializerForType(type));
        } catch (SerializationException e) {
            return Optional.empty();
        }
    }
    
    /**
     * 获取默认序列化器
     * 
     * @return 默认序列化器
     */
    public Serializer getDefaultSerializer() {
        return defaultSerializer;
    }
    
    /**
     * 设置默认序列化器
     * 
     * @param serializer 默认序列化器
     */
    public void setDefaultSerializer(Serializer serializer) {
        this.defaultSerializer = serializer;
    }
    
    /**
     * 获取所有已注册的序列化格式
     * 
     * @return 已注册的格式集合
     */
    public Set<SerializationFormat> getRegisteredFormats() {
        return Collections.unmodifiableSet(serializersByFormat.keySet());
    }
    
    /**
     * 获取所有已注册的类型
     * 
     * @return 已注册的类型集合
     */
    public Set<Class<?>> getRegisteredTypes() {
        return Collections.unmodifiableSet(serializersByType.keySet());
    }
    
    /**
     * 检查是否支持指定格式
     * 
     * @param format 序列化格式
     * @return 如果支持返回 true
     */
    public boolean supportsFormat(SerializationFormat format) {
        return serializersByFormat.containsKey(format);
    }
    
    /**
     * 检查是否支持指定类型
     * 
     * @param type 类型
     * @return 如果支持返回 true
     */
    public boolean supportsType(Class<?> type) {
        return findSerializerForType(type).isPresent();
    }
    
    /**
     * 清除所有注册的序列化器
     */
    public void clear() {
        serializersByFormat.clear();
        serializersByType.clear();
        defaultSerializer = null;
    }
    
    /**
     * 序列化对象
     * 
     * @param data 要序列化的对象
     * @param format 目标格式
     * @return 序列化后的字节数组
     * @throws SerializationException 序列化失败时抛出
     */
    public byte[] serialize(Object data, SerializationFormat format) throws SerializationException {
        return getSerializer(format).serialize(data);
    }
    
    /**
     * 反序列化字节数组
     * 
     * @param data 要反序列化的字节数组
     * @param format 源格式
     * @param targetType 目标类型
     * @param <T> 目标类型参数
     * @return 反序列化后的对象
     * @throws SerializationException 反序列化失败时抛出
     */
    public <T> T deserialize(byte[] data, SerializationFormat format, Class<T> targetType) 
            throws SerializationException {
        return getSerializer(format).deserialize(data, targetType);
    }
}
