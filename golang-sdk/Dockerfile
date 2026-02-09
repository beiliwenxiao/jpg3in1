FROM golang:1.21-alpine AS builder

WORKDIR /app

# 安装依赖
RUN apk add --no-cache git protobuf-dev

# 复制 go.mod 和 go.sum
COPY go.mod go.sum ./
RUN go mod download

# 复制源代码
COPY . .

# 构建应用
RUN CGO_ENABLED=0 GOOS=linux go build -o /framework-service ./cmd/service

FROM alpine:latest

WORKDIR /app

# 复制二进制文件
COPY --from=builder /framework-service .

# 暴露端口
EXPOSE 8081 9001

# 启动应用
ENTRYPOINT ["./framework-service"]
