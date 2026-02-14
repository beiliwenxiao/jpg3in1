package serializer

import (
	"encoding/json"
	"fmt"
)

// SerializationFormat 序列化格式
type SerializationFormat string

const (
	JSON     SerializationFormat = "json"
	PROTOBUF SerializationFormat = "protobuf"
	MSGPACK  SerializationFormat = "msgpack"
	CUSTOM   SerializationFormat = "custom"
)

// Serializer 序列化器接口
type Serializer interface {
	// Serialize 序列化数据
	Serialize(data interface{}) ([]byte, error)
	
	// Deserialize 反序列化数据
	Deserialize(data []byte, target interface{}) error
	
	// GetFormat 获取序列化格式
	GetFormat() SerializationFormat
}

// SerializerRegistry 序列化器注册表
type SerializerRegistry struct {
	serializers map[SerializationFormat]Serializer
}

// NewSerializerRegistry 创建序列化器注册表
func NewSerializerRegistry() *SerializerRegistry {
	registry := &SerializerRegistry{
		serializers: make(map[SerializationFormat]Serializer),
	}
	
	// 注册默认序列化器
	registry.Register(NewJsonSerializer())
	
	return registry
}

// Register 注册序列化器
func (r *SerializerRegistry) Register(serializer Serializer) {
	r.serializers[serializer.GetFormat()] = serializer
}

// Get 获取序列化器
func (r *SerializerRegistry) Get(format SerializationFormat) (Serializer, error) {
	serializer, exists := r.serializers[format]
	if !exists {
		return nil, fmt.Errorf("serializer not found for format: %s", format)
	}
	return serializer, nil
}

// GetSupportedFormats 获取支持的格式
func (r *SerializerRegistry) GetSupportedFormats() []SerializationFormat {
	formats := make([]SerializationFormat, 0, len(r.serializers))
	for format := range r.serializers {
		formats = append(formats, format)
	}
	return formats
}

// JsonSerializer JSON 序列化器
type JsonSerializer struct{}

// NewJsonSerializer 创建 JSON 序列化器
func NewJsonSerializer() *JsonSerializer {
	return &JsonSerializer{}
}

// Serialize 序列化数据
func (s *JsonSerializer) Serialize(data interface{}) ([]byte, error) {
	return json.Marshal(data)
}

// Deserialize 反序列化数据
func (s *JsonSerializer) Deserialize(data []byte, target interface{}) error {
	return json.Unmarshal(data, target)
}

// GetFormat 获取序列化格式
func (s *JsonSerializer) GetFormat() SerializationFormat {
	return JSON
}
