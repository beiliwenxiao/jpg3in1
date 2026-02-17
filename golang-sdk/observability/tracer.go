package observability

import (
	"context"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"go.opentelemetry.io/otel/codes"
	"go.opentelemetry.io/otel/trace"
)

// Tracer 分布式追踪器
type Tracer struct {
	tracer      trace.Tracer
	serviceName string
}

// NewTracer 创建新的追踪器
func NewTracer(serviceName string) *Tracer {
	return &Tracer{
		tracer:      otel.Tracer(serviceName),
		serviceName: serviceName,
	}
}

// StartSpan 开始一个新的 span
func (t *Tracer) StartSpan(ctx context.Context, spanName string, attrs ...attribute.KeyValue) (context.Context, trace.Span) {
	return t.tracer.Start(ctx, spanName, trace.WithAttributes(attrs...))
}

// EndSpan 结束 span
func (t *Tracer) EndSpan(span trace.Span, err error) {
	if err != nil {
		span.RecordError(err)
		span.SetStatus(codes.Error, err.Error())
	} else {
		span.SetStatus(codes.Ok, "")
	}
	span.End()
}

// ExtractTraceID 从上下文提取 trace ID
func (t *Tracer) ExtractTraceID(ctx context.Context) string {
	spanCtx := trace.SpanContextFromContext(ctx)
	if spanCtx.HasTraceID() {
		return spanCtx.TraceID().String()
	}
	return ""
}

// ExtractSpanID 从上下文提取 span ID
func (t *Tracer) ExtractSpanID(ctx context.Context) string {
	spanCtx := trace.SpanContextFromContext(ctx)
	if spanCtx.HasSpanID() {
		return spanCtx.SpanID().String()
	}
	return ""
}

// AddEvent 向当前 span 添加事件
func (t *Tracer) AddEvent(ctx context.Context, name string, attrs ...attribute.KeyValue) {
	span := trace.SpanFromContext(ctx)
	span.AddEvent(name, trace.WithAttributes(attrs...))
}

// SetAttributes 设置 span 属性
func (t *Tracer) SetAttributes(ctx context.Context, attrs ...attribute.KeyValue) {
	span := trace.SpanFromContext(ctx)
	span.SetAttributes(attrs...)
}
