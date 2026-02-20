# Hello World - 三语言互调示例

三个语言（PHP / Java / Go）各自启动一个 JSON-RPC HTTP 服务，并互相调用对方的 `hello.sayHello` 方法。

- Java 使用内置 HTTP 服务器
- PHP 使用 Webman 处理 HTTP 路由，Workerman 管理 Worker 进程
- Go 使用 GoFrame ghttp.Server 处理 HTTP

## 端口分配

| 语言 | 端口 | 框架 | 入口文件 |
|------|------|------|----------|
| Java | 8092 | 内置 HTTP | `java/src/main/java/HelloWorld.java` |
| PHP  | 8091 | Webman + Workerman | `php/hello.php` |
| Go   | 8093 | GoFrame ghttp | `golang/main.go` |

## 运行方式

### 方式一：一键启动（推荐）

**Windows:**
```bat
examples\hello-world\run-all.bat
```

**Linux:**
```bash
chmod +x examples/hello-world/run-all.sh
examples/hello-world/run-all.sh
```

### 方式二：分别启动

**构建 Java jar（只需一次）**
```bash
mvn package -f examples/hello-world/java/pom.xml
```

**分别在三个终端中启动：**

```bash
# 终端 1 - Go（先编译）
cd examples/hello-world/golang && go build -o hello-world . && ./hello-world

# 终端 2 - Java
java -jar examples/hello-world/java/target/hello-world.jar

# 终端 3 - PHP (Windows)
php examples/hello-world/php/windows.php

# 终端 3 - PHP (Linux)
php examples/hello-world/php/start.php start
```

## 浏览器访问

三个服务都提供了浏览器页面，会调用其他两个语言并展示结果：

- http://localhost:8091 （PHP）
- http://localhost:8092 （Java）
- http://localhost:8093 （Go）

## API 接口

每个服务提供：
- `POST /jsonrpc` — JSON-RPC 2.0 接口，供其他语言调用
- `GET /hello` — 调用其他两个语言，返回 JSON
- `GET /` — 浏览器首页

```json
// 请求
{"jsonrpc": "2.0", "method": "hello.sayHello", "id": 1}

// 响应
{"jsonrpc": "2.0", "result": "Hello world, I am PHP", "id": 1}
```
