package com.framework.serialization;

import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;

/**
 * 序列化异常
 * 
 * 当序列化或反序列化操作失败时抛出
 * 
 * 需求: 8.2
 */
public class SerializationException extends FrameworkException {
    
    private final SerializationFormat format;
    private final Class<?> targetType;
    
    public SerializationException(String message) {
        super(ErrorCode.SERIALIZATION_ERROR, message);
        this.format = null;
        this.targetType = null;
    }
    
    public SerializationException(String message, Throwable cause) {
        super(ErrorCode.SERIALIZATION_ERROR, message, cause);
        this.format = null;
        this.targetType = null;
    }
    
    public SerializationException(String message, SerializationFormat format) {
        super(ErrorCode.SERIALIZATION_ERROR, message);
        this.format = format;
        this.targetType = null;
    }
    
    public SerializationException(String message, SerializationFormat format, Class<?> targetType) {
        super(ErrorCode.SERIALIZATION_ERROR, message);
        this.format = format;
        this.targetType = targetType;
    }
    
    public SerializationException(String message, SerializationFormat format, 
                                   Class<?> targetType, Throwable cause) {
        super(ErrorCode.SERIALIZATION_ERROR, message, cause);
        this.format = format;
        this.targetType = targetType;
    }
    
    public SerializationFormat getFormat() {
        return format;
    }
    
    public Class<?> getTargetType() {
        return targetType;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SerializationException{");
        sb.append("message='").append(getMessage()).append('\'');
        if (format != null) {
            sb.append(", format=").append(format);
        }
        if (targetType != null) {
            sb.append(", targetType=").append(targetType.getName());
        }
        sb.append('}');
        return sb.toString();
    }
}
