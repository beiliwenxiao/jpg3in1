package com.framework.serialization;

/**
 * 序列化格式枚举
 * 
 * 定义框架支持的序列化格式类型
 */
public enum SerializationFormat {
    /**
     * JSON 格式 - 基于 Jackson 实现
     */
    JSON("json", "application/json"),
    
    /**
     * Protocol Buffers 格式
     */
    PROTOBUF("protobuf", "application/x-protobuf"),
    
    /**
     * MessagePack 格式（可选高性能序列化）
     */
    MSGPACK("msgpack", "application/x-msgpack"),
    
    /**
     * 自定义格式
     */
    CUSTOM("custom", "application/octet-stream");
    
    private final String name;
    private final String contentType;
    
    SerializationFormat(String name, String contentType) {
        this.name = name;
        this.contentType = contentType;
    }
    
    public String getName() {
        return name;
    }
    
    public String getContentType() {
        return contentType;
    }
    
    /**
     * 根据名称获取序列化格式
     */
    public static SerializationFormat fromName(String name) {
        for (SerializationFormat format : values()) {
            if (format.name.equalsIgnoreCase(name)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unknown serialization format: " + name);
    }
    
    /**
     * 根据 Content-Type 获取序列化格式
     */
    public static SerializationFormat fromContentType(String contentType) {
        for (SerializationFormat format : values()) {
            if (format.contentType.equalsIgnoreCase(contentType)) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unknown content type: " + contentType);
    }
}
