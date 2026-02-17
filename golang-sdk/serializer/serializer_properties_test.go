package serializer

import (
	"reflect"
	"testing"

	"github.com/leanovate/gopter"
	"github.com/leanovate/gopter/gen"
	"github.com/leanovate/gopter/prop"
)

// Feature: multi-language-communication-framework, Property 7: 序列化往返一致性
// 验证需求: 6.6
func TestSerializationRoundTripConsistency(t *testing.T) {
	properties := gopter.NewProperties(nil)

	// 测试基本数据类型的序列化往返一致性
	properties.Property("Basic types serialization round trip", prop.ForAll(
		func(intVal int, floatVal float64, strVal string, boolVal bool) bool {
			serializer := NewJsonSerializer()

			// 测试整数
			intData, err := serializer.Serialize(intVal)
			if err != nil {
				return false
			}
			var intResult int
			if err := serializer.Deserialize(intData, &intResult); err != nil {
				return false
			}
			if intResult != intVal {
				return false
			}

			// 测试浮点数
			floatData, err := serializer.Serialize(floatVal)
			if err != nil {
				return false
			}
			var floatResult float64
			if err := serializer.Deserialize(floatData, &floatResult); err != nil {
				return false
			}
			if floatResult != floatVal {
				return false
			}

			// 测试字符串
			strData, err := serializer.Serialize(strVal)
			if err != nil {
				return false
			}
			var strResult string
			if err := serializer.Deserialize(strData, &strResult); err != nil {
				return false
			}
			if strResult != strVal {
				return false
			}

			// 测试布尔值
			boolData, err := serializer.Serialize(boolVal)
			if err != nil {
				return false
			}
			var boolResult bool
			if err := serializer.Deserialize(boolData, &boolResult); err != nil {
				return false
			}
			if boolResult != boolVal {
				return false
			}

			return true
		},
		gen.Int(),
		gen.Float64(),
		gen.Identifier(),
		gen.Bool(),
	))

	// 测试复合数据类型的序列化往返一致性
	properties.Property("Composite types serialization round trip", prop.ForAll(
		func(intSlice []int, strMap map[string]string) bool {
			serializer := NewJsonSerializer()

			// 测试数组
			sliceData, err := serializer.Serialize(intSlice)
			if err != nil {
				return false
			}
			var sliceResult []int
			if err := serializer.Deserialize(sliceData, &sliceResult); err != nil {
				return false
			}
			if !reflect.DeepEqual(sliceResult, intSlice) {
				return false
			}

			// 测试映射
			mapData, err := serializer.Serialize(strMap)
			if err != nil {
				return false
			}
			var mapResult map[string]string
			if err := serializer.Deserialize(mapData, &mapResult); err != nil {
				return false
			}
			if !reflect.DeepEqual(mapResult, strMap) {
				return false
			}

			return true
		},
		gen.SliceOf(gen.Int()),
		gen.MapOf(gen.Identifier(), gen.Identifier()),
	))

	// 测试结构体的序列化往返一致性
	properties.Property("Struct serialization round trip", prop.ForAll(
		func(name string, age int, active bool) bool {
			serializer := NewJsonSerializer()

			type TestStruct struct {
				Name   string `json:"name"`
				Age    int    `json:"age"`
				Active bool   `json:"active"`
			}

			original := TestStruct{
				Name:   name,
				Age:    age,
				Active: active,
			}

			// 序列化
			data, err := serializer.Serialize(original)
			if err != nil {
				return false
			}

			// 反序列化
			var result TestStruct
			if err := serializer.Deserialize(data, &result); err != nil {
				return false
			}

			// 验证等价性
			return reflect.DeepEqual(original, result)
		},
		gen.Identifier(),
		gen.IntRange(0, 150),
		gen.Bool(),
	))

	// 测试嵌套结构体的序列化往返一致性
	properties.Property("Nested struct serialization round trip", prop.ForAll(
		func(userName string, userAge int, addrCity string, addrZip string) bool {
			serializer := NewJsonSerializer()

			type Address struct {
				City string `json:"city"`
				Zip  string `json:"zip"`
			}

			type User struct {
				Name    string  `json:"name"`
				Age     int     `json:"age"`
				Address Address `json:"address"`
			}

			original := User{
				Name: userName,
				Age:  userAge,
				Address: Address{
					City: addrCity,
					Zip:  addrZip,
				},
			}

			// 序列化
			data, err := serializer.Serialize(original)
			if err != nil {
				return false
			}

			// 反序列化
			var result User
			if err := serializer.Deserialize(data, &result); err != nil {
				return false
			}

			// 验证等价性
			return reflect.DeepEqual(original, result)
		},
		gen.Identifier(),
		gen.IntRange(0, 150),
		gen.Identifier(),
		gen.RegexMatch("[0-9]{5}"),
	))

	// 测试空值和边界情况
	properties.Property("Empty and boundary cases serialization round trip", prop.ForAll(
		func() bool {
			serializer := NewJsonSerializer()

			// 测试空切片
			emptySlice := []int{}
			sliceData, err := serializer.Serialize(emptySlice)
			if err != nil {
				return false
			}
			var sliceResult []int
			if err := serializer.Deserialize(sliceData, &sliceResult); err != nil {
				return false
			}
			if !reflect.DeepEqual(sliceResult, emptySlice) {
				return false
			}

			// 测试空映射
			emptyMap := map[string]string{}
			mapData, err := serializer.Serialize(emptyMap)
			if err != nil {
				return false
			}
			var mapResult map[string]string
			if err := serializer.Deserialize(mapData, &mapResult); err != nil {
				return false
			}
			if !reflect.DeepEqual(mapResult, emptyMap) {
				return false
			}

			// 测试空字符串
			emptyStr := ""
			strData, err := serializer.Serialize(emptyStr)
			if err != nil {
				return false
			}
			var strResult string
			if err := serializer.Deserialize(strData, &strResult); err != nil {
				return false
			}
			if strResult != emptyStr {
				return false
			}

			// 测试零值
			zeroInt := 0
			intData, err := serializer.Serialize(zeroInt)
			if err != nil {
				return false
			}
			var intResult int
			if err := serializer.Deserialize(intData, &intResult); err != nil {
				return false
			}
			if intResult != zeroInt {
				return false
			}

			return true
		},
	))

	properties.TestingRun(t, gopter.ConsoleReporter(false))
}

// 测试序列化器注册表
func TestSerializerRegistry(t *testing.T) {
	properties := gopter.NewProperties(nil)

	properties.Property("Serializer registry manages serializers correctly", prop.ForAll(
		func(data string) bool {
			registry := NewSerializerRegistry()

			// 验证默认 JSON 序列化器已注册
			jsonSerializer, err := registry.Get(JSON)
			if err != nil {
				return false
			}
			if jsonSerializer.GetFormat() != JSON {
				return false
			}

			// 测试序列化往返
			serialized, err := jsonSerializer.Serialize(data)
			if err != nil {
				return false
			}

			var result string
			if err := jsonSerializer.Deserialize(serialized, &result); err != nil {
				return false
			}

			return result == data
		},
		gen.Identifier(),
	))

	properties.Property("Registry returns error for unsupported format", prop.ForAll(
		func() bool {
			registry := NewSerializerRegistry()

			// 尝试获取未注册的序列化器
			_, err := registry.Get(PROTOBUF)
			return err != nil
		},
	))

	properties.Property("Custom serializer can be registered", prop.ForAll(
		func(data string) bool {
			registry := NewSerializerRegistry()

			// 注册自定义序列化器（这里使用 JSON 作为示例）
			customSerializer := NewJsonSerializer()
			registry.Register(customSerializer)

			// 验证可以获取并使用
			serializer, err := registry.Get(JSON)
			if err != nil {
				return false
			}

			serialized, err := serializer.Serialize(data)
			if err != nil {
				return false
			}

			var result string
			if err := serializer.Deserialize(serialized, &result); err != nil {
				return false
			}

			return result == data
		},
		gen.Identifier(),
	))

	properties.TestingRun(t, gopter.ConsoleReporter(false))
}
