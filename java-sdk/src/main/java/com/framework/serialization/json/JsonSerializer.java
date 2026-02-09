package com.framework.serialization.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.framework.serialization.SerializationException;
import com.framework.serialization.SerializationFormat;
import com.framework.serialization.Serializer;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Set;

/**
 * JSON 序列化器实现
 * 
 * 基于 Jackson 库实现 JSON 格式的序列化和反序列化
 * 支持基本数据类型和复合类型
 * 
 * 需求: 6.1, 6.2, 6.3, 6.4
 */
public class JsonSerializer implements Serializer {
    
    private final ObjectMapper objectMapper;
    
    /**
     * 使用默认配置创建 JSON 序列化器
     */
    public JsonSerializer() {
        this(createDefaultObjectMapper());
    }
    
    /**
     * 使用自定义 ObjectMapper 创建 JSON 序列化器
     * 
     * @param objectMapper 自定义的 ObjectMapper
     */
    public JsonSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * 创建默认配置的 ObjectMapper
     */
    private static ObjectMapper createDefaultObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // 序列化配置
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        
        // 反序列化配置
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        
        return mapper;
    }
    
    @Override
    public byte[] serialize(Object data) throws SerializationException {
        if (data == null) {
            return "null".getBytes();
        }
        
        try {
            return objectMapper.writeValueAsBytes(data);
        } catch (JsonProcessingException e) {
            throw new SerializationException(
                "Failed to serialize object to JSON: " + e.getMessage(),
                SerializationFormat.JSON,
                data.getClass(),
                e
            );
        }
    }
    
    @Override
    public <T> T deserialize(byte[] data, Class<T> targetType) throws SerializationException {
        if (data == null || data.length == 0) {
            return null;
        }
        
        try {
            return objectMapper.readValue(data, targetType);
        } catch (IOException e) {
            throw new SerializationException(
                "Failed to deserialize JSON to " + targetType.getName() + ": " + e.getMessage(),
                SerializationFormat.JSON,
                targetType,
                e
            );
        }
    }
    
    @Override
    public <T> T deserialize(byte[] data, Type targetType) throws SerializationException {
        if (data == null || data.length == 0) {
            return null;
        }
        
        try {
            JavaType javaType = objectMapper.getTypeFactory().constructType(targetType);
            return objectMapper.readValue(data, javaType);
        } catch (IOException e) {
            throw new SerializationException(
                "Failed to deserialize JSON to " + targetType.getTypeName() + ": " + e.getMessage(),
                SerializationFormat.JSON,
                null,
                e
            );
        }
    }
    
    @Override
    public SerializationFormat getFormat() {
        return SerializationFormat.JSON;
    }
    
    @Override
    public Set<SerializationFormat> getSupportedFormats() {
        return Collections.singleton(SerializationFormat.JSON);
    }
    
    @Override
    public boolean supportsType(Class<?> type) {
        // JSON 序列化器支持所有类型
        return true;
    }
    
    /**
     * 获取内部使用的 ObjectMapper
     * 
     * @return ObjectMapper 实例
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
    
    /**
     * 将对象序列化为 JSON 字符串
     * 
     * @param data 要序列化的对象
     * @return JSON 字符串
     * @throws SerializationException 序列化失败时抛出
     */
    public String serializeToString(Object data) throws SerializationException {
        if (data == null) {
            return "null";
        }
        
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new SerializationException(
                "Failed to serialize object to JSON string: " + e.getMessage(),
                SerializationFormat.JSON,
                data.getClass(),
                e
            );
        }
    }
    
    /**
     * 从 JSON 字符串反序列化对象
     * 
     * @param json JSON 字符串
     * @param targetType 目标类型
     * @param <T> 目标类型参数
     * @return 反序列化后的对象
     * @throws SerializationException 反序列化失败时抛出
     */
    public <T> T deserializeFromString(String json, Class<T> targetType) throws SerializationException {
        if (json == null || json.isEmpty()) {
            return null;
        }
        
        try {
            return objectMapper.readValue(json, targetType);
        } catch (JsonProcessingException e) {
            throw new SerializationException(
                "Failed to deserialize JSON string to " + targetType.getName() + ": " + e.getMessage(),
                SerializationFormat.JSON,
                targetType,
                e
            );
        }
    }
    
    /**
     * 将对象序列化为格式化的 JSON 字符串（便于阅读）
     * 
     * @param data 要序列化的对象
     * @return 格式化的 JSON 字符串
     * @throws SerializationException 序列化失败时抛出
     */
    public String serializeToPrettyString(Object data) throws SerializationException {
        if (data == null) {
            return "null";
        }
        
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new SerializationException(
                "Failed to serialize object to pretty JSON string: " + e.getMessage(),
                SerializationFormat.JSON,
                data.getClass(),
                e
            );
        }
    }
}
