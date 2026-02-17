# etcd 服务注册中心部署脚本

本目录包含用于部署和管理 etcd 服务注册中心的脚本,支持 Windows 和 Linux 环境,支持 Docker 和原生安装两种模式。

## 脚本列表

| 脚本 | 说明 | 支持平台 |
|------|------|----------|
| `start-etcd.ps1` | 启动 etcd (PowerShell) | Windows |
| `start-etcd.sh` | 启动 etcd (Bash) | Linux/macOS |
| `stop-etcd.ps1` | 停止 etcd (PowerShell) | Windows |
| `stop-etcd.sh` | 停止 etcd (Bash) | Linux/macOS |
| `health-check-etcd.ps1` | 健康检查 (PowerShell) | Windows |
| `health-check-etcd.sh` | 健康检查 (Bash) | Linux/macOS |

## 快速开始

### Windows 环境

#### 使用 Docker 模式 (推荐用于开发)

```powershell
# 前台运行
.\scripts\start-etcd.ps1

# 后台运行
.\scripts\start-etcd.ps1 -Detach

# 健康检查
.\scripts\health-check-etcd.ps1

# 停止服务
.\scripts\stop-etcd.ps1
```

#### 使用原生模式 (推荐用于生产)

```powershell
# 启动 etcd (首次运行会自动下载安装)
.\scripts\start-etcd.ps1 -Mode native

# 健康检查
.\scripts\health-check-etcd.ps1

# 停止服务
.\scripts\stop-etcd.ps1
```

### Linux/macOS 环境

#### 使用 Docker 模式 (推荐用于开发)

```bash
# 添加执行权限
chmod +x scripts/*.sh

# 前台运行
./scripts/start-etcd.sh

# 后台运行
./scripts/start-etcd.sh -d

# 健康检查
./scripts/health-check-etcd.sh

# 停止服务
./scripts/stop-etcd.sh
```

#### 使用原生模式 (推荐用于生产)

```bash
# 启动 etcd (首次运行会自动下载安装)
./scripts/start-etcd.sh -m native

# 健康检查
./scripts/health-check-etcd.sh

# 停止服务
./scripts/stop-etcd.sh
```

## 配置说明

### 环境变量

可以通过环境变量自定义配置:

```powershell
# Windows PowerShell
$env:ETCD_VERSION = "v3.5.11"
$env:ETCD_DATA_DIR = ".\data\etcd"
$env:ETCD_PORT = "2379"
$env:ETCD_PEER_PORT = "2380"
$env:FRAMEWORK_NAMESPACE = "/framework"
$env:SERVICE_TTL = "30"
```

```bash
# Linux/macOS
export ETCD_VERSION="v3.5.11"
export ETCD_DATA_DIR="./data/etcd"
export ETCD_PORT="2379"
export ETCD_PEER_PORT="2380"
export FRAMEWORK_NAMESPACE="/framework"
export SERVICE_TTL="30"
```

### 命名空间 (Namespace)

命名空间用于隔离不同应用的服务注册数据:

- **默认值**: `/framework`
- **用途**: 避免不同应用的服务注册冲突
- **结构**: `/framework/services/<service-name>/<instance-id>`

### TTL (Time To Live)

TTL 定义服务注册的有效期,服务需要定期续约:

- **默认值**: 30 秒
- **用途**: 自动清理失效的服务实例
- **建议**: 心跳间隔设置为 TTL/3

## 部署模式对比

| 特性 | Docker 模式 | 原生模式 |
|------|------------|----------|
| 安装复杂度 | 需要 Docker | 自动下载安装 |
| 性能 | 有容器开销 | 无额外开销 |
| 资源占用 | 较高 | 较低 |
| 进程管理 | Docker 管理 | 系统进程 |
| 适用场景 | 开发/测试 | 生产环境 |
| 隔离性 | 容器隔离 | 进程隔离 |

## 健康检查

健康检查脚本会自动检测运行模式并执行以下检查:

1. **进程/容器状态**: 检查 etcd 是否运行
2. **端点健康**: 检查 etcd 端点是否响应
3. **集群状态**: 检查集群信息和版本
4. **命名空间**: 检查框架命名空间是否存在
5. **服务列表**: 列出已注册的服务
6. **性能指标**: 显示数据库大小和 Raft 索引

示例输出:

```
==========================================
etcd 服务注册中心健康检查
==========================================

检测到的运行模式: native

检查 etcd 进程状态... 运行中 (PID: 12345)
检查 etcd 端点健康状态... 健康
检查 etcd 集群状态... 正常
集群信息:
  - 端点: http://localhost:2379, Leader: 8e9e05c52164694d, 版本: 3.5.11
检查框架命名空间... 存在
  - 已注册服务数量: 3
  - 已注册服务列表:
    * user-service
    * order-service
    * payment-service

性能指标:
  - DB 大小: 20480 bytes, Raft 索引: 15

==========================================
健康检查完成！
==========================================
```

## 故障排查

### 端口被占用

如果启动失败提示端口被占用:

```powershell
# Windows - 查找占用端口的进程
netstat -ano | findstr "2379"

# Linux/macOS - 查找占用端口的进程
lsof -i :2379
```

### 数据目录权限问题

确保数据目录有写权限:

```bash
# 检查权限
ls -la ./data/etcd

# 修改权限 (如需要)
chmod -R 755 ./data/etcd
```

### 清理旧数据

如果需要重新开始 (会丢失所有数据):

```bash
# 停止 etcd
./scripts/stop-etcd.sh

# 删除数据目录
rm -rf ./data/etcd

# 重新启动
./scripts/start-etcd.sh -m native
```

## 原生安装说明

### Windows

脚本会自动执行以下操作:
1. 从 GitHub 下载 etcd Windows 版本
2. 解压到 `C:\Program Files\etcd`
3. 添加到系统 PATH 环境变量
4. 启动 etcd 服务

**注意**: 首次安装可能需要管理员权限来修改系统 PATH。

### Linux/macOS

脚本会自动执行以下操作:
1. 检测操作系统和架构
2. 从 GitHub 下载对应版本的 etcd
3. 解压并安装到 `/usr/local/bin`
4. 启动 etcd 服务

**注意**: 安装到 `/usr/local/bin` 需要 sudo 权限。

## 验证安装

安装完成后,可以使用以下命令验证:

```bash
# 检查 etcd 版本
etcd --version

# 检查 etcdctl 版本
etcdctl version

# 测试连接
etcdctl --endpoints=http://localhost:2379 endpoint health
```

## 与框架集成

etcd 部署完成后,可以在框架配置中使用:

```yaml
# application.yml
framework:
  registry:
    type: etcd
    endpoints:
      - http://localhost:2379
    namespace: /framework
    ttl: 30
```

详细配置说明请参考: [etcd 配置文档](../docs/etcd-configuration.md)

## 生产环境建议

1. **使用原生模式**: 更好的性能和稳定性
2. **部署集群**: 至少 3 个节点保证高可用
3. **配置监控**: 监控健康状态和性能指标
4. **定期备份**: 使用 `etcdctl snapshot save` 备份数据
5. **调整 TTL**: 根据网络状况调整合适的 TTL 值
6. **启用 TLS**: 生产环境启用 TLS 加密通信

## 参考资料

- [etcd 官方文档](https://etcd.io/docs/)
- [etcd 配置说明](../docs/etcd-configuration.md)
- [服务注册与发现设计](../.kiro/specs/multi-language-communication-framework/design.md)
