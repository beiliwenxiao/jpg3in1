# Protocol Buffers 代码生成状态

## 当前状态

### ✅ 已完成
1. **Protocol Buffers 定义文件** - 已完成
   - `common.proto` - 通用数据结构和类型
   - `service.proto` - gRPC 服务接口
   - `jsonrpc.proto` - JSON-RPC 2.0 协议格式
   - `custom_protocol.proto` - 自定义二进制协议格式

2. **代码生成脚本** - 已完成
   - `scripts/generate-proto.ps1` - Windows PowerShell 脚本
   - `scripts/generate-proto.sh` - Linux/macOS Bash 脚本

### ⚠️ 待完成
**生成各语言的代码** - 需要安装 Protocol Buffers 编译器（protoc）

## 代码生成步骤

### 前置条件

#### 1. 安装 Protocol Buffers 编译器（protoc）

**Windows:**
1. 访问 https://github.com/protocolbuffers/protobuf/releases
2. 下载最新版本的 `protoc-{version}-win64.zip`（建议 3.20.0 或更高）
3. 解压到任意目录（例如：`C:\protoc`）
4. 将 `bin` 目录添加到系统 PATH 环境变量
5. 验证安装：打开命令行运行 `protoc --version`

**Linux:**
```bash
# Ubuntu/Debian
sudo apt-get update
sudo apt-get install -y protobuf-compiler

# 或从源码安装
wget https://github.com/protocolbuffers/protobuf/releases/download/v21.12/protoc-21.12-linux-x86_64.zip
unzip protoc-21.12-linux-x86_64.zip -d $HOME/.local
export PATH="$PATH:$HOME/.local/bin"
```

**macOS:**
```bash
brew install protobuf
```

#### 2. 安装各语言的 protoc 插件

**Java:**
- 已在 `java-sdk/pom.xml` 中配置依赖
- Maven 会自动下载 `protobuf-java` 和 `grpc-java`

**Golang:**
```bash
go install google.golang.org/protobuf/cmd/protoc-gen-go@latest
go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest
```

**PHP:**
```bash
# 安装 grpc_php_plugin
# 参考: https://grpc.io/docs/languages/php/quickstart/
pecl install grpc
pecl install protobuf
```

### 执行代码生成

#### Windows (PowerShell)
```powershell
cd scripts
.\generate-proto.ps1
```

#### Linux/macOS (Bash)
```bash
cd scripts
chmod +x generate-proto.sh
./generate-proto.sh
```

### 生成的代码位置

执行成功后，代码将生成到以下位置：

- **Java**: `java-sdk/src/main/java/com/framework/proto/`
  - `CommonProto.java` - 通用数据结构
  - `ServiceProto.java` - gRPC 服务接口
  - `JsonRpcProto.java` - JSON-RPC 协议
  - `CustomProtocolProto.java` - 自定义协议
  - `FrameworkServiceGrpc.java` - gRPC 服务存根

- **Golang**: `golang-sdk/proto/`
  - `common.pb.go` - 通用数据结构
  - `service.pb.go` - gRPC 服务接口
  - `service_grpc.pb.go` - gRPC 服务存根
  - `jsonrpc.pb.go` - JSON-RPC 协议
  - `custom_protocol.pb.go` - 自定义协议

- **PHP**: `php-sdk/src/Proto/`
  - `Framework/Common/` - 通用数据结构
  - `Framework/Service/` - gRPC 服务接口
  - `Framework/Jsonrpc/` - JSON-RPC 协议
  - `Framework/Custom/` - 自定义协议

## 验证代码生成

### Java
```bash
cd java-sdk
mvn clean compile
```

### Golang
```bash
cd golang-sdk
go build ./...
```

### PHP
```bash
cd php-sdk
composer install
```

## 当前问题

1. **protoc 未安装** - 需要在开发环境中安装 Protocol Buffers 编译器
2. **代码未生成** - Java SDK 中的代码引用了 `com.framework.proto` 包，但文件尚未生成
3. **编译错误** - 由于缺少生成的代码，当前 Java SDK 无法编译

## 解决方案

### 方案 1：安装 protoc 并生成代码（推荐）
1. 按照上述步骤安装 protoc
2. 运行代码生成脚本
3. 验证生成的代码

### 方案 2：使用 Maven 插件自动生成（Java）
在 `java-sdk/pom.xml` 中添加 protobuf-maven-plugin：

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.xolstice.maven.plugins</groupId>
            <artifactId>protobuf-maven-plugin</artifactId>
            <version>0.6.1</version>
            <configuration>
                <protocArtifact>com.google.protobuf:protoc:3.21.12:exe:${os.detected.classifier}</protocArtifact>
                <pluginId>grpc-java</pluginId>
                <pluginArtifact>io.grpc:protoc-gen-grpc-java:1.50.0:exe:${os.detected.classifier}</pluginArtifact>
                <protoSourceRoot>${project.basedir}/../proto</protoSourceRoot>
            </configuration>
            <executions>
                <execution>
                    <goals>
                        <goal>compile</goal>
                        <goal>compile-custom</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

然后运行：
```bash
mvn clean compile
```

### 方案 3：使用 Gradle 插件自动生成（Java）
如果使用 Gradle，可以添加 protobuf-gradle-plugin。

## 下一步

1. **立即执行**：安装 protoc 并运行代码生成脚本
2. **验证**：检查生成的代码是否正确
3. **编译**：确保各语言 SDK 可以成功编译
4. **测试**：运行单元测试验证协议定义的正确性

## 相关文档

- [Protocol Buffers 官方文档](https://developers.google.com/protocol-buffers)
- [gRPC 官方文档](https://grpc.io/docs/)
- [JSON-RPC 2.0 规范](https://www.jsonrpc.org/specification)
- [proto/README.md](./README.md) - 协议规范详细说明

## 维护记录

- 2024-01-XX: 创建 Protocol Buffers 定义文件
- 2024-01-XX: 创建代码生成脚本
- 2024-01-XX: 记录代码生成状态和步骤
