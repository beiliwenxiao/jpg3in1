package observability

import (
	"context"
	"errors"
	"testing"

	"go.opentelemetry.io/otel/attribute"
)

func TestTracer(t *testing.T) {
	tracer := NewTracer("test-service")
	ctx := context.Background()

	// 测试开始和结束 span
	ctx, span := tracer.StartSpan(ctx, "test-operation")
	if span == nil {
		t.Fatal("Expected span to be created")
	}
	tracer.EndSpan(span, nil)
}

func TestTracerWithError(t *testing.T) {
	tracer := NewTracer("test-service")
	ctx := context.Background()

	// 测试带错误的 span
	ctx, span := tracer.StartSpan(ctx, "test-operation-with-error")
	err := errors.New("test error")
	tracer.EndSpan(span, err)
}

func TestTracerWithAttributes(t *testing.T) {
	tracer := NewTracer("test-service")
	ctx := context.Background()

	// 测试带属性的 span
	ctx, span := tracer.StartSpan(ctx, "test-operation",
		attribute.String("key1", "value1"),
		attribute.Int("key2", 123),
	)
	tracer.EndSpan(span, nil)
}

func TestTracerExtractIDs(t *testing.T) {
	tracer := NewTracer("test-service")
	ctx := context.Background()

	// 测试提取 trace ID 和 span ID
	ctx, span := tracer.StartSpan(ctx, "test-operation")
	defer tracer.EndSpan(span, nil)

	traceID := tracer.ExtractTraceID(ctx)
	spanID := tracer.ExtractSpanID(ctx)

	if traceID == "" {
		t.Error("Expected trace ID to be non-empty")
	}
	if spanID == "" {
		t.Error("Expected span ID to be non-empty")
	}
}

func TestTracerAddEvent(t *testing.T) {
	tracer := NewTracer("test-service")
	ctx := context.Background()

	ctx, span := tracer.StartSpan(ctx, "test-operation")
	defer tracer.EndSpan(span, nil)

	// 测试添加事件
	tracer.AddEvent(ctx, "event1", attribute.String("detail", "event detail"))
	tracer.AddEvent(ctx, "event2")
}

func TestTracerSetAttributes(t *testing.T) {
	tracer := NewTracer("test-service")
	ctx := context.Background()

	ctx, span := tracer.StartSpan(ctx, "test-operation")
	defer tracer.EndSpan(span, nil)

	// 测试设置属性
	tracer.SetAttributes(ctx,
		attribute.String("attr1", "value1"),
		attribute.Int("attr2", 456),
		attribute.Bool("attr3", true),
	)
}

func TestTracerNestedSpans(t *testing.T) {
	tracer := NewTracer("test-service")
	ctx := context.Background()

	// 测试嵌套 span
	ctx, parentSpan := tracer.StartSpan(ctx, "parent-operation")
	defer tracer.EndSpan(parentSpan, nil)

	ctx, childSpan := tracer.StartSpan(ctx, "child-operation")
	defer tracer.EndSpan(childSpan, nil)

	// 验证 trace ID 相同
	traceID1 := tracer.ExtractTraceID(ctx)

	ctx2, span2 := tracer.StartSpan(ctx, "another-child")
	defer tracer.EndSpan(span2, nil)

	traceID2 := tracer.ExtractTraceID(ctx2)

	if traceID1 != traceID2 {
		t.Error("Expected same trace ID for nested spans")
	}
}
