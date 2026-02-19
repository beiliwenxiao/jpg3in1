package serializer

import "testing"

// BenchmarkJsonSerialize 序列化性能基准测试
func BenchmarkJsonSerialize(b *testing.B) {
	s := NewJsonSerializer()
	data := map[string]interface{}{
		"key": "value", "num": 42, "arr": []int{1, 2, 3},
	}
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		_, _ = s.Serialize(data)
	}
}

// BenchmarkJsonDeserialize 反序列化性能基准测试
func BenchmarkJsonDeserialize(b *testing.B) {
	s := NewJsonSerializer()
	data := map[string]interface{}{
		"key": "value", "num": 42, "arr": []int{1, 2, 3},
	}
	raw, _ := s.Serialize(data)
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		var result map[string]interface{}
		_ = s.Deserialize(raw, &result)
	}
}

// BenchmarkJsonRoundTrip 序列化往返性能基准测试
func BenchmarkJsonRoundTrip(b *testing.B) {
	s := NewJsonSerializer()
	data := map[string]interface{}{
		"key": "value", "num": 42, "arr": []int{1, 2, 3},
	}
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		raw, _ := s.Serialize(data)
		var result map[string]interface{}
		_ = s.Deserialize(raw, &result)
	}
}
