# Hello World gRPC - 跨语言 gRPC 通信示例

Java 和 Go 通过 gRPC 协议互相调用 `Greeter.SayHello`，PHP 作为 HTTP 代理客户端展示结果。

## 端口分配

| 语言 | gRPC 端口 | HTTP 端口 | 角色 |
|------|-----------|-----------|------|
| Java | 9091 | 8091 | gRPC 服务端 + 客户端 |
| Go   | 9093 | 8093 | gRPC 服务端 + 客户端 |
| PHP  | — | 8092 | HTTP 代理客户端 |

## 运行方式

### 一键启动

**Windows:**
```bat
examples\hello-world-grpc\run-all.bat
```

**Linux:**
```bash
chmod +x examples/hello-world-grpc/run-all.sh
examples/hello-world-grpc/run-all.sh
```

### 分别启动

```bash
# Java（gRPC:9091 + HTTP:8091）
mvn package -f examples/hello-world-grpc/java/pom.xml
java -jar examples/hello-world-grpc/java/target/hello-world-grpc.jar

# Go（gRPC:9093 + HTTP:8093）
cd examples/hello-world-grpc/golang && go build -o hello-world-grpc . && ./hello-world-grpc

# PHP（HTTP:8092）
php examples/hello-world-grpc/php/hello-grpc.php
```

## 浏览器访问

- http://localhost:8091 （Java）
- http://localhost:8092 （PHP）
- http://localhost:8093 （Go）

## Proto 定义

```protobuf
service Greeter {
  rpc SayHello (HelloRequest) returns (HelloReply);
}
```

## 与 hello-world 示例的区别

| | hello-world | hello-world-grpc |
|---|---|---|
| 内部通信 | JSON-RPC 2.0 over HTTP | gRPC (Protobuf) |
| 序列化 | JSON | Protobuf 二进制 |
| 接口定义 | 约定式 | Proto 文件强类型 |
| 传输层 | HTTP/1.1 | HTTP/2 |
