package com.framework.serialization.custom;

import com.framework.serialization.AbstractSerializer;
import com.framework.serialization.SerializationException;
import com.framework.serialization.SerializationFormat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/**
 * 自定义类型序列化器
 * 
 * 提供一个通用的自定义序列化器实现，允许用户为特定类型定义序列化和反序列化逻辑
 * 
 * 需求: 6.5
 * 
 * @param <T> 要序列化的类型
 */
public class CustomTypeSerializer<T> extends AbstractSerializer {
    
    private final Class<T> targetType;
    private final Function<T, byte[]> serializeFunction;
    private final Function<byte[], T> deserializeFunction;
    
    /**
     * 创建自定义类型序列化器
     * 
     * @param targetType 目标类型
     * @param serializeFunction 序列化函数
     * @param deserializeFunction 反序列化函数
     */
    public CustomTypeSerializer(Class<T> targetType,
                                 Function<T, byte[]> serializeFunction,
                                 Function<byte[], T> deserializeFunction) {
        super(SerializationFormat.CUSTOM);
        this.targetType = targetType;
        this.serializeFunction = serializeFunction;
        this.deserializeFunction = deserializeFunction;
        addSupportedType(targetType);
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public byte[] serialize(Object data) throws SerializationException {
        if (data == null) {
            return null;
        }
        
        if (!targetType.isInstance(data)) {
            throw new SerializationException(
                "Expected type " + targetType.getName() + " but got " + data.getClass().getName(),
                SerializationFormat.CUSTOM,
                data.getClass()
            );
        }
        
        try {
            return serializeFunction.apply((T) data);
        } catch (Exception e) {
            throw new SerializationException(
                "Failed to serialize " + targetType.getName() + ": " + e.getMessage(),
                SerializationFormat.CUSTOM,
                targetType,
                e
            );
        }
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public <R> R deserialize(byte[] data, Class<R> type) throws SerializationException {
        if (data == null) {
            return null;
        }
        
        if (!targetType.equals(type) && !targetType.isAssignableFrom(type)) {
            throw new SerializationException(
                "This serializer only supports " + targetType.getName() + ", not " + type.getName(),
                SerializationFormat.CUSTOM,
                type
            );
        }
        
        try {
            return (R) deserializeFunction.apply(data);
        } catch (Exception e) {
            throw new SerializationException(
                "Failed to deserialize to " + type.getName() + ": " + e.getMessage(),
                SerializationFormat.CUSTOM,
                type,
                e
            );
        }
    }
    
    /**
     * 获取目标类型
     * 
     * @return 目标类型
     */
    public Class<T> getTargetType() {
        return targetType;
    }
    
    // ==================== 工厂方法 ====================
    
    /**
     * 创建字符串序列化器
     * 
     * @return 字符串序列化器
     */
    public static CustomTypeSerializer<String> forString() {
        return new CustomTypeSerializer<>(
            String.class,
            s -> s.getBytes(StandardCharsets.UTF_8),
            bytes -> new String(bytes, StandardCharsets.UTF_8)
        );
    }
    
    /**
     * 创建整数序列化器
     * 
     * @return 整数序列化器
     */
    public static CustomTypeSerializer<Integer> forInteger() {
        return new CustomTypeSerializer<>(
            Integer.class,
            i -> ByteBuffer.allocate(4).putInt(i).array(),
            bytes -> ByteBuffer.wrap(bytes).getInt()
        );
    }
    
    /**
     * 创建长整数序列化器
     * 
     * @return 长整数序列化器
     */
    public static CustomTypeSerializer<Long> forLong() {
        return new CustomTypeSerializer<>(
            Long.class,
            l -> ByteBuffer.allocate(8).putLong(l).array(),
            bytes -> ByteBuffer.wrap(bytes).getLong()
        );
    }
    
    /**
     * 创建双精度浮点数序列化器
     * 
     * @return 双精度浮点数序列化器
     */
    public static CustomTypeSerializer<Double> forDouble() {
        return new CustomTypeSerializer<>(
            Double.class,
            d -> ByteBuffer.allocate(8).putDouble(d).array(),
            bytes -> ByteBuffer.wrap(bytes).getDouble()
        );
    }
    
    /**
     * 创建布尔值序列化器
     * 
     * @return 布尔值序列化器
     */
    public static CustomTypeSerializer<Boolean> forBoolean() {
        return new CustomTypeSerializer<>(
            Boolean.class,
            b -> new byte[] { (byte) (b ? 1 : 0) },
            bytes -> bytes[0] != 0
        );
    }
    
    /**
     * 创建字节数组序列化器（直接传递）
     * 
     * @return 字节数组序列化器
     */
    public static CustomTypeSerializer<byte[]> forByteArray() {
        return new CustomTypeSerializer<>(
            byte[].class,
            bytes -> bytes,
            bytes -> bytes
        );
    }
}
