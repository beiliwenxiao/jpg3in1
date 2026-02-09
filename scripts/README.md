# 脚本使用说明

本目录包含用于管理 etcd 服务注册中心和生成 Protocol Buffers 代码的脚本，支持 Linux/macOS (Bash) 和 Windows (PowerShell) 环境。

## 前置要求

### Linux/macOS
- 已安装 `etcdctl` 命令行工具
- Bash shell

### Windows
- 已安装 `etcdctl.exe` 并添加到 PATH 环境变量
- PowerShell 5.1 或更高版本

### 安装 etcdctl

**Linux:**
```bash
# 下载 etcd
ETCD_VER=v3.5.11
wget https://github.com/etcd-io/etcd/releases/download/${ETCD_VER}/etcd-${ETCD_VER}-linux-amd64.tar.gz
tar xzvf etcd-${ETCD_VER}-linux-amd64.tar.gz
sudo mv etcd-${ETCD_VER}-linux-amd64/etcdctl /usr/local/bin/
```

**Windows:**
1. 从 https://github.com/etcd-io/etcd/releases 下载 Windows 版本
2. 解压并将 `etcdctl.exe` 添加到 PATH 环境变量

## 脚本列表

### Protocol Buffers 代码生成

#### generate-proto (生成 Protocol Buffers 代码)

从 `.proto` 定义文件生成 Java、Golang、PHP 三种语言的代码。

**前置要求:**
- 已安装 Protocol Buffers 编译器 (protoc)
- 已安装各语言的 protoc 插件（详见 [proto/README.md](../proto/README.md)）

**Linux/macOS:**
```bash
# 进入 scripts 目录
cd scripts

# 添加执行权限
chmod +x generate-proto.sh

# 运行脚本
./generate-proto.sh
```

**Windows:**
```powershell
# 进入 scripts 目录
cd scripts

# 运行脚本
.\generate-proto.ps1
```

生成的代码位置：
- Java: `java-sdk/src/main/java/com/framework/proto/`
- Golang: `golang-sdk/proto/`
- PHP: `php-sdk/src/Proto/`

详细说明请参考 [proto/README.md](../proto/README.md)。

---

### etcd 服务注册中心管理

### 1. etcd-health-check (健康检查)

检查 etcd 集群的健康状态，包括端点健康、集群成员、读写操作等。

**Linux/macOS:**
```bash
# 使用默认配置
./scripts/etcd-health-check.sh

# 自定义端点
ETCD_ENDPOINTS=http://localhost:2379 ./scripts/etcd-health-check.sh

# 自定义命名空间和超时
ETCD_ENDPOINTS=http://localhost:2379 ETCD_NAMESPACE=/framework TIMEOUT=10 ./scripts/etcd-health-check.sh
```

**Windows:**
```powershell
# 使用默认配置
.\scripts\etcd-health-check.ps1

# 自定义端点
.\scripts\etcd-health-check.ps1 -EtcdEndpoints "http://localhost:2379"

# 自定义命名空间和超时
.\scripts\etcd-health-check.ps1 -EtcdEndpoints "http://localhost:2379" -EtcdNamespace "/framework" -Timeout 10
```

### 2. etcd-init (初始化)

初始化 etcd 服务注册中心，创建命名空间和设置配置。

**Linux/macOS:**
```bash
# 使用默认配置
./scripts/etcd-init.sh

# 自定义端点
ETCD_ENDPOINTS=http://localhost:2379 ./scripts/etcd-init.sh
```

**Windows:**
```powershell
# 使用默认配置
.\scripts\etcd-init.ps1

# 自定义端点
.\scripts\etcd-init.ps1 -EtcdEndpoints "http://localhost:2379"
```

### 3. etcd-service-manager (服务管理)

管理服务的注册、注销、查询和监听。

**注意：** 此脚本目前仅提供 Bash 版本，Windows 用户可以使用 WSL 或 Git Bash 运行。

#### 注册服务

```bash
./scripts/etcd-service-manager.sh register \
  --name my-service \
  --id instance-1 \
  --language java \
  --address localhost \
  --port 8080 \
  --version 1.0.0 \
  --protocols grpc,rest \
  --ttl 30
```

#### 注销服务

```bash
# 注销指定实例
./scripts/etcd-service-manager.sh deregister --name my-service --id instance-1

# 注销所有实例
./scripts/etcd-service-manager.sh deregister --name my-service
```

#### 列出所有服务

```bash
./scripts/etcd-service-manager.sh list
```

#### 获取服务详情

```bash
# 获取所有实例
./scripts/etcd-service-manager.sh get --name my-service

# 获取指定实例
./scripts/etcd-service-manager.sh get --name my-service --id instance-1
```

#### 监听服务变化

```bash
# 监听所有服务
./scripts/etcd-service-manager.sh watch

# 监听指定服务
./scripts/etcd-service-manager.sh watch --name my-service
```

## 使用 Docker Compose

### 启动 etcd

```bash
# Linux/macOS
docker-compose up -d etcd

# Windows (PowerShell)
docker-compose up -d etcd
```

### 初始化 etcd

等待 etcd 启动后（约 10-15 秒），运行初始化脚本：

**Linux/macOS:**
```bash
./scripts/etcd-init.sh
```

**Windows:**
```powershell
.\scripts\etcd-init.ps1
```

### 健康检查

**Linux/macOS:**
```bash
./scripts/etcd-health-check.sh
```

**Windows:**
```powershell
.\scripts\etcd-health-check.ps1
```

## 命名空间结构

初始化后，etcd 中会创建以下命名空间：

```
/framework
├── .namespace                    # 命名空间标记
├── services/                     # 服务注册
│   ├── .namespace
│   ├── example-java-service/
│   │   └── instance-1
│   ├── example-golang-service/
│   │   └── instance-1
│   └── example-php-service/
│       └── instance-1
├── config/                       # 框架配置
│   ├── .namespace
│   ├── ttl/
│   │   ├── service_registration
│   │   ├── heartbeat_interval
│   │   ├── discovery_cache
│   │   ├── config_cache
│   │   └── lock
│   ├── service_registration/
│   │   ├── auto_register
│   │   ├── auto_deregister
│   │   ├── health_check_enabled
│   │   ├── health_check_interval
│   │   ├── health_check_timeout
│   │   └── health_check_failure_threshold
│   └── load_balancing/
│       ├── default_strategy
│       ├── health_check_filter
│       └── version_filter
├── locks/                        # 分布式锁
│   └── .namespace
└── health/                       # 健康检查
    └── .namespace
```

## 配置说明

### TTL 配置

- `service_registration`: 30秒 - 服务注册的租约时间
- `heartbeat_interval`: 10秒 - 心跳间隔（应小于 service_registration）
- `discovery_cache`: 60秒 - 服务发现缓存时间
- `config_cache`: 300秒 - 配置缓存时间
- `lock`: 60秒 - 分布式锁的租约时间

### 服务注册配置

- `auto_register`: true - 启用自动注册
- `auto_deregister`: true - 启用自动注销
- `health_check_enabled`: true - 启用健康检查
- `health_check_interval`: 10秒 - 健康检查间隔
- `health_check_timeout`: 5秒 - 健康检查超时
- `health_check_failure_threshold`: 3 - 健康检查失败阈值

### 负载均衡配置

- `default_strategy`: round_robin - 默认负载均衡策略
- `health_check_filter`: true - 启用健康检查过滤
- `version_filter`: false - 禁用版本过滤

## 故障排查

### etcdctl 命令未找到

确保已安装 etcd 客户端工具并添加到 PATH 环境变量。

### 连接超时

检查 etcd 是否正在运行：

```bash
docker ps | grep etcd
```

检查端口是否可访问：

```bash
# Linux/macOS
curl http://localhost:2379/health

# Windows (PowerShell)
Invoke-WebRequest -Uri http://localhost:2379/health
```

### 权限错误 (Linux/macOS)

为脚本添加执行权限：

```bash
chmod +x scripts/*.sh
```

## 相关文档

- [etcd 官方文档](https://etcd.io/docs/)
- [etcdctl 命令参考](https://etcd.io/docs/latest/dev-guide/interacting_v3/)
- [多语言通信框架设计文档](../.kiro/specs/multi-language-communication-framework/design.md)
