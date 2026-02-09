package com.framework.serialization;

import com.framework.serialization.json.JsonSerializer;
import com.framework.serialization.custom.CustomTypeSerializer;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 序列化属性测试
 * 
 * Feature: multi-language-communication-framework
 * 验证需求: 3.4, 3.5, 6.3, 6.4, 6.6
 */
class SerializationPropertyTest {
    
    private final JsonSerializer jsonSerializer = new JsonSerializer();
    
    // ==================== 属性 7: 序列化往返一致性 ====================
    
    /**
     * 属性 7: 序列化往返一致性
     * 
     * 对于任意数据对象和序列化格式（JSON、Protobuf、自定义），
     * 序列化后反序列化应该得到等价的对象
     * 
     * 验证需求: 3.4, 3.5, 6.6
     */
    @Property
    @Label("Feature: multi-language-communication-framework, Property 7: 序列化往返一致性 - 字符串")
    void serializationRoundTripString(@ForAll String data) throws SerializationException {
        byte[] serialized = jsonSerializer.serialize(data);
        String deserialized = jsonSerializer.deserialize(serialized, String.class);
        assertEquals(data, deserialized, "字符串序列化往返应该保持一致");
    }
    
    @Property
    @Label("Feature: multi-language-communication-framework, Property 7: 序列化往返一致性 - 整数")
    void serializationRoundTripInteger(@ForAll Integer data) throws SerializationException {
        byte[] serialized = jsonSerializer.serialize(data);
        Integer deserialized = jsonSerializer.deserialize(serialized, Integer.class);
        assertEquals(data, deserialized, "整数序列化往返应该保持一致");
    }
    
    @Property
    @Label("Feature: multi-language-communication-framework, Property 7: 序列化往返一致性 - 长整数")
    void serializationRoundTripLong(@ForAll Long data) throws SerializationException {
        byte[] serialized = jsonSerializer.serialize(data);
        Long deserialized = jsonSerializer.deserialize(serialized, Long.class);
        assertEquals(data, deserialized, "长整数序列化往返应该保持一致");
    }
    
    @Property
    @Label("Feature: multi-language-communication-framework, Property 7: 序列化往返一致性 - 浮点数")
    void serializationRoundTripDouble(@ForAll @DoubleRange(min = -1e10, max = 1e10) Double data) 
            throws SerializationException {
        // 排除特殊值 NaN 和 Infinity
        Assume.that(!data.isNaN() && !data.isInfinite());
        
        byte[] serialized = jsonSerializer.serialize(data);
        Double deserialized = jsonSerializer.deserialize(serialized, Double.class);
        assertEquals(data, deserialized, 0.0001, "浮点数序列化往返应该保持一致");
    }
    
    @Property
    @Label("Feature: multi-language-communication-framework, Property 7: 序列化往返一致性 - 布尔值")
    void serializationRoundTripBoolean(@ForAll Boolean data) throws SerializationException {
        byte[] serialized = jsonSerializer.serialize(data);
        Boolean deserialized = jsonSerializer.deserialize(serialized, Boolean.class);
        assertEquals(data, deserialized, "布尔值序列化往返应该保持一致");
    }
    
    @Property
    @Label("Feature: multi-language-communication-framework, Property 7: 序列化往返一致性 - 复合对象")
    void serializationRoundTripComplexObject(
            @ForAll @NotBlank String name,
            @ForAll @IntRange(min = 0, max = 150) int age,
            @ForAll boolean active) throws SerializationException {
        
        TestPerson person = new TestPerson(name, age, active);
        byte[] serialized = jsonSerializer.serialize(person);
        TestPerson deserialized = jsonSerializer.deserialize(serialized, TestPerson.class);
        
        assertEquals(person.getName(), deserialized.getName(), "名称应该一致");
        assertEquals(person.getAge(), deserialized.getAge(), "年龄应该一致");
        assertEquals(person.isActive(), deserialized.isActive(), "活跃状态应该一致");
    }
    
    // ==================== 属性 19: 基本数据类型序列化支持 ====================
    
    /**
     * 属性 19: 基本数据类型序列化支持
     * 
     * 对于任意基本数据类型（整数、浮点数、字符串、布尔值），
     * 应该能够正确序列化和反序列化
     * 
     * 验证需求: 6.3
     */
    @Property
    @Label("Feature: multi-language-communication-framework, Property 19: 基本数据类型序列化支持 - 整数类型")
    void basicTypeSerializationInteger(@ForAll Integer value) throws SerializationException {
        // 测试 JSON 序列化
        byte[] jsonBytes = jsonSerializer.serialize(value);
        assertNotNull(jsonBytes, "序列化结果不应为空");
        assertTrue(jsonBytes.length > 0, "序列化结果应该有内容");
        
        Integer result = jsonSerializer.deserialize(jsonBytes, Integer.class);
        assertEquals(value, result, "反序列化结果应该与原值相等");
    }
    
    @Property
    @Label("Feature: multi-language-communication-framework, Property 19: 基本数据类型序列化支持 - 浮点类型")
    void basicTypeSerializationFloat(@ForAll @FloatRange(min = -1e6f, max = 1e6f) Float value) 
            throws SerializationException {
        Assume.that(!value.isNaN() && !value.isInfinite());
        
        byte[] jsonBytes = jsonSerializer.serialize(value);
        assertNotNull(jsonBytes, "序列化结果不应为空");
        
        Float result = jsonSerializer.deserialize(jsonBytes, Float.class);
        assertEquals(value, result, 0.0001f, "反序列化结果应该与原值相等");
    }
    
    @Property
    @Label("Feature: multi-language-communication-framework, Property 19: 基本数据类型序列化支持 - 字符串类型")
    void basicTypeSerializationString(@ForAll String value) throws SerializationException {
        byte[] jsonBytes = jsonSerializer.serialize(value);
        assertNotNull(jsonBytes, "序列化结果不应为空");
        
        String result = jsonSerializer.deserialize(jsonBytes, String.class);
        assertEquals(value, result, "反序列化结果应该与原值相等");
    }
    
    @Property
    @Label("Feature: multi-language-communication-framework, Property 19: 基本数据类型序列化支持 - 布尔类型")
    void basicTypeSerializationBoolean(@ForAll Boolean value) throws SerializationException {
        byte[] jsonBytes = jsonSerializer.serialize(value);
        assertNotNull(jsonBytes, "序列化结果不应为空");
        
        Boolean result = jsonSerializer.deserialize(jsonBytes, Boolean.class);
        assertEquals(value, result, "反序列化结果应该与原值相等");
    }
    
    // ==================== 属性 20: 复合数据类型序列化支持 ====================
    
    /**
     * 属性 20: 复合数据类型序列化支持
     * 
     * 对于任意复合数据类型（数组、对象、映射），
     * 应该能够正确序列化和反序列化
     * 
     * 验证需求: 6.4
     */
    @Property
    @Label("Feature: multi-language-communication-framework, Property 20: 复合数据类型序列化支持 - 数组")
    void compositeTypeSerializationArray(@ForAll @Size(max = 100) List<Integer> values) 
            throws SerializationException {
        Integer[] array = values.toArray(new Integer[0]);
        
        byte[] jsonBytes = jsonSerializer.serialize(array);
        assertNotNull(jsonBytes, "序列化结果不应为空");
        
        Integer[] result = jsonSerializer.deserialize(jsonBytes, Integer[].class);
        assertArrayEquals(array, result, "数组反序列化结果应该与原值相等");
    }
    
    @Property
    @Label("Feature: multi-language-communication-framework, Property 20: 复合数据类型序列化支持 - 列表")
    void compositeTypeSerializationList(@ForAll @Size(max = 100) List<String> values) 
            throws SerializationException {
        byte[] jsonBytes = jsonSerializer.serialize(values);
        assertNotNull(jsonBytes, "序列化结果不应为空");
        
        @SuppressWarnings("unchecked")
        List<String> result = jsonSerializer.deserialize(jsonBytes, List.class);
        assertEquals(values.size(), result.size(), "列表大小应该一致");
    }
    
    @Property
    @Label("Feature: multi-language-communication-framework, Property 20: 复合数据类型序列化支持 - 映射")
    void compositeTypeSerializationMap(
            @ForAll @Size(max = 50) Map<@AlphaChars @StringLength(min = 1, max = 20) String, Integer> values) 
            throws SerializationException {
        byte[] jsonBytes = jsonSerializer.serialize(values);
        assertNotNull(jsonBytes, "序列化结果不应为空");
        
        @SuppressWarnings("unchecked")
        Map<String, Integer> result = jsonSerializer.deserialize(jsonBytes, Map.class);
        assertEquals(values.size(), result.size(), "映射大小应该一致");
    }
    
    @Property
    @Label("Feature: multi-language-communication-framework, Property 20: 复合数据类型序列化支持 - 嵌套对象")
    void compositeTypeSerializationNestedObject(
            @ForAll @NotBlank String name,
            @ForAll @IntRange(min = 0, max = 150) int age,
            @ForAll @Size(max = 10) List<@NotBlank String> tags) throws SerializationException {
        
        TestPersonWithTags person = new TestPersonWithTags(name, age, tags);
        byte[] jsonBytes = jsonSerializer.serialize(person);
        assertNotNull(jsonBytes, "序列化结果不应为空");
        
        TestPersonWithTags result = jsonSerializer.deserialize(jsonBytes, TestPersonWithTags.class);
        assertEquals(person.getName(), result.getName(), "名称应该一致");
        assertEquals(person.getAge(), result.getAge(), "年龄应该一致");
        assertEquals(person.getTags().size(), result.getTags().size(), "标签数量应该一致");
    }
    
    // ==================== 自定义序列化器测试 ====================
    
    @Property
    @Label("自定义序列化器 - 整数往返一致性")
    void customSerializerIntegerRoundTrip(@ForAll Integer value) throws SerializationException {
        CustomTypeSerializer<Integer> serializer = CustomTypeSerializer.forInteger();
        
        byte[] serialized = serializer.serialize(value);
        Integer deserialized = serializer.deserialize(serialized, Integer.class);
        
        assertEquals(value, deserialized, "自定义整数序列化器往返应该一致");
    }
    
    @Property
    @Label("自定义序列化器 - 长整数往返一致性")
    void customSerializerLongRoundTrip(@ForAll Long value) throws SerializationException {
        CustomTypeSerializer<Long> serializer = CustomTypeSerializer.forLong();
        
        byte[] serialized = serializer.serialize(value);
        Long deserialized = serializer.deserialize(serialized, Long.class);
        
        assertEquals(value, deserialized, "自定义长整数序列化器往返应该一致");
    }
    
    @Property
    @Label("自定义序列化器 - 双精度浮点数往返一致性")
    void customSerializerDoubleRoundTrip(@ForAll @DoubleRange(min = -1e10, max = 1e10) Double value) 
            throws SerializationException {
        Assume.that(!value.isNaN() && !value.isInfinite());
        
        CustomTypeSerializer<Double> serializer = CustomTypeSerializer.forDouble();
        
        byte[] serialized = serializer.serialize(value);
        Double deserialized = serializer.deserialize(serialized, Double.class);
        
        assertEquals(value, deserialized, 0.0001, "自定义双精度浮点数序列化器往返应该一致");
    }
    
    @Property
    @Label("自定义序列化器 - 布尔值往返一致性")
    void customSerializerBooleanRoundTrip(@ForAll Boolean value) throws SerializationException {
        CustomTypeSerializer<Boolean> serializer = CustomTypeSerializer.forBoolean();
        
        byte[] serialized = serializer.serialize(value);
        Boolean deserialized = serializer.deserialize(serialized, Boolean.class);
        
        assertEquals(value, deserialized, "自定义布尔值序列化器往返应该一致");
    }
    
    @Property
    @Label("自定义序列化器 - 字符串往返一致性")
    void customSerializerStringRoundTrip(@ForAll String value) throws SerializationException {
        CustomTypeSerializer<String> serializer = CustomTypeSerializer.forString();
        
        byte[] serialized = serializer.serialize(value);
        String deserialized = serializer.deserialize(serialized, String.class);
        
        assertEquals(value, deserialized, "自定义字符串序列化器往返应该一致");
    }
    
    // ==================== 序列化器注册表测试 ====================
    
    @Property
    @Label("序列化器注册表 - 格式查找一致性")
    void serializerRegistryFormatLookup(@ForAll @From("serializationFormats") SerializationFormat format) {
        SerializerRegistry registry = SerializerRegistry.createNew();
        
        // JSON 和 PROTOBUF 应该默认注册
        if (format == SerializationFormat.JSON || format == SerializationFormat.PROTOBUF) {
            assertTrue(registry.supportsFormat(format), 
                "注册表应该支持格式: " + format);
            
            Serializer serializer = registry.getSerializer(format);
            assertNotNull(serializer, "应该能获取到序列化器");
            assertEquals(format, serializer.getFormat(), "序列化器格式应该匹配");
        }
    }
    
    @Provide
    Arbitrary<SerializationFormat> serializationFormats() {
        return Arbitraries.of(SerializationFormat.JSON, SerializationFormat.PROTOBUF);
    }
    
    // ==================== 测试辅助类 ====================
    
    /**
     * 测试用的简单 POJO 类
     */
    public static class TestPerson {
        private String name;
        private int age;
        private boolean active;
        
        public TestPerson() {}
        
        public TestPerson(String name, int age, boolean active) {
            this.name = name;
            this.age = age;
            this.active = active;
        }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }
    
    /**
     * 测试用的带标签的 POJO 类
     */
    public static class TestPersonWithTags {
        private String name;
        private int age;
        private List<String> tags;
        
        public TestPersonWithTags() {
            this.tags = new ArrayList<>();
        }
        
        public TestPersonWithTags(String name, int age, List<String> tags) {
            this.name = name;
            this.age = age;
            this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
        }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
    }
}
