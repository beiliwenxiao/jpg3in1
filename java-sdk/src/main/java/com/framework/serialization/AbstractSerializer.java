package com.framework.serialization;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 抽象序列化器基类
 * 
 * 提供序列化器的基础实现，简化自定义序列化器的开发
 * 
 * 需求: 6.5
 */
public abstract class AbstractSerializer implements Serializer {
    
    private final SerializationFormat primaryFormat;
    private final Set<SerializationFormat> supportedFormats;
    private final Set<Class<?>> supportedTypes;
    
    /**
     * 创建支持单一格式的序列化器
     * 
     * @param format 支持的格式
     */
    protected AbstractSerializer(SerializationFormat format) {
        this.primaryFormat = format;
        this.supportedFormats = Collections.singleton(format);
        this.supportedTypes = new HashSet<>();
    }
    
    /**
     * 创建支持多种格式的序列化器
     * 
     * @param primaryFormat 主要格式
     * @param additionalFormats 额外支持的格式
     */
    protected AbstractSerializer(SerializationFormat primaryFormat, 
                                  SerializationFormat... additionalFormats) {
        this.primaryFormat = primaryFormat;
        Set<SerializationFormat> formats = new HashSet<>();
        formats.add(primaryFormat);
        Collections.addAll(formats, additionalFormats);
        this.supportedFormats = Collections.unmodifiableSet(formats);
        this.supportedTypes = new HashSet<>();
    }
    
    @Override
    public SerializationFormat getFormat() {
        return primaryFormat;
    }
    
    @Override
    public Set<SerializationFormat> getSupportedFormats() {
        return supportedFormats;
    }
    
    @Override
    public <T> T deserialize(byte[] data, Type targetType) throws SerializationException {
        if (targetType instanceof Class) {
            @SuppressWarnings("unchecked")
            Class<T> clazz = (Class<T>) targetType;
            return deserialize(data, clazz);
        }
        throw new SerializationException(
            "This serializer only supports Class types, not: " + targetType.getTypeName(),
            primaryFormat
        );
    }
    
    @Override
    public boolean supportsType(Class<?> type) {
        if (supportedTypes.isEmpty()) {
            // 如果没有指定支持的类型，默认支持所有类型
            return true;
        }
        
        // 检查是否直接支持该类型
        if (supportedTypes.contains(type)) {
            return true;
        }
        
        // 检查是否支持该类型的父类或接口
        for (Class<?> supportedType : supportedTypes) {
            if (supportedType.isAssignableFrom(type)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 添加支持的类型
     * 
     * @param type 要支持的类型
     */
    protected void addSupportedType(Class<?> type) {
        supportedTypes.add(type);
    }
    
    /**
     * 添加多个支持的类型
     * 
     * @param types 要支持的类型数组
     */
    protected void addSupportedTypes(Class<?>... types) {
        Collections.addAll(supportedTypes, types);
    }
    
    /**
     * 获取支持的类型集合
     * 
     * @return 支持的类型集合
     */
    protected Set<Class<?>> getSupportedTypes() {
        return Collections.unmodifiableSet(supportedTypes);
    }
    
    /**
     * 验证数据不为空
     * 
     * @param data 要验证的数据
     * @throws SerializationException 如果数据为空
     */
    protected void validateNotNull(Object data) throws SerializationException {
        if (data == null) {
            throw new SerializationException("Data cannot be null", primaryFormat);
        }
    }
    
    /**
     * 验证字节数组不为空
     * 
     * @param data 要验证的字节数组
     * @throws SerializationException 如果字节数组为空
     */
    protected void validateNotEmpty(byte[] data) throws SerializationException {
        if (data == null || data.length == 0) {
            throw new SerializationException("Data cannot be null or empty", primaryFormat);
        }
    }
    
    /**
     * 验证类型是否支持
     * 
     * @param type 要验证的类型
     * @throws SerializationException 如果类型不支持
     */
    protected void validateType(Class<?> type) throws SerializationException {
        if (!supportsType(type)) {
            throw new SerializationException(
                "Type not supported: " + type.getName(),
                primaryFormat,
                type
            );
        }
    }
}
