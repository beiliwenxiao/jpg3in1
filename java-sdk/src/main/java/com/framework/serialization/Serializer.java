package com.framework.serialization;

import java.lang.reflect.Type;
import java.util.Set;

/**
 * 序列化器接口
 * 
 * 定义序列化和反序列化的统一接口，支持多种序列化格式
 * 
 * 需求: 6.1, 6.2, 6.3, 6.4, 6.5
 */
public interface Serializer {
    
    /**
     * 序列化对象为字节数组
     * 
     * @param data 要序列化的对象
     * @return 序列化后的字节数组
     * @throws SerializationException 序列化失败时抛出
     */
    byte[] serialize(Object data) throws SerializationException;
    
    /**
     * 反序列化字节数组为对象
     * 
     * @param data 要反序列化的字节数组
     * @param targetType 目标类型
     * @param <T> 目标类型参数
     * @return 反序列化后的对象
     * @throws SerializationException 反序列化失败时抛出
     */
    <T> T deserialize(byte[] data, Class<T> targetType) throws SerializationException;
    
    /**
     * 反序列化字节数组为对象（支持泛型类型）
     * 
     * @param data 要反序列化的字节数组
     * @param targetType 目标类型（支持泛型）
     * @param <T> 目标类型参数
     * @return 反序列化后的对象
     * @throws SerializationException 反序列化失败时抛出
     */
    <T> T deserialize(byte[] data, Type targetType) throws SerializationException;
    
    /**
     * 获取此序列化器支持的格式
     * 
     * @return 支持的序列化格式
     */
    SerializationFormat getFormat();
    
    /**
     * 获取此序列化器支持的所有格式
     * 
     * @return 支持的序列化格式集合
     */
    Set<SerializationFormat> getSupportedFormats();
    
    /**
     * 检查是否支持指定的格式
     * 
     * @param format 要检查的格式
     * @return 如果支持返回 true
     */
    default boolean supports(SerializationFormat format) {
        return getSupportedFormats().contains(format);
    }
    
    /**
     * 检查是否支持指定的类型
     * 
     * @param type 要检查的类型
     * @return 如果支持返回 true
     */
    boolean supportsType(Class<?> type);
}
