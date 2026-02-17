# 安全模块安装指南

## 依赖安装

安全模块依赖 `github.com/golang-jwt/jwt/v5` 库。

### 方法1：使用Go代理（推荐）

如果遇到网络问题，可以配置Go代理：

**Windows (PowerShell):**
```powershell
$env:GOPROXY = "https://goproxy.cn,direct"
$env:GOSUMDB = "sum.golang.google.cn"
go mod download
```

**Linux/Mac (Bash):**
```bash
export GOPROXY=https://goproxy.cn,direct
export GOSUMDB=sum.golang.google.cn
go mod download
```

### 方法2：使用阿里云代理

**Windows (PowerShell):**
```powershell
$env:GOPROXY = "https://mirrors.aliyun.com/goproxy/,direct"
go mod download
```

**Linux/Mac (Bash):**
```bash
export GOPROXY=https://mirrors.aliyun.com/goproxy/,direct
go mod download
```

### 方法3：永久配置

**Windows:**
```powershell
go env -w GOPROXY=https://goproxy.cn,direct
go env -w GOSUMDB=sum.golang.google.cn
```

**Linux/Mac:**
```bash
go env -w GOPROXY=https://goproxy.cn,direct
go env -w GOSUMDB=sum.golang.google.cn
```

## 运行测试

配置好代理后，运行测试：

**Windows (PowerShell):**
```powershell
cd golang-sdk
go test ./security/ -v
```

**Linux/Mac (Bash):**
```bash
cd golang-sdk
go test ./security/ -v
```

## 运行示例

```bash
cd golang-sdk
go run examples/security_example.go
```

## Docker环境

如果使用Docker，可以在Dockerfile中配置代理：

```dockerfile
ENV GOPROXY=https://goproxy.cn,direct
ENV GOSUMDB=sum.golang.google.cn
```

## 故障排除

### 问题1：missing go.sum entry

**解决方案：**
```bash
go mod tidy
go mod download
```

### 问题2：网络超时

**解决方案：**
1. 配置Go代理（见上文）
2. 检查防火墙设置
3. 使用VPN或代理

### 问题3：证书错误

**解决方案：**
```bash
go env -w GOINSECURE=github.com/golang-jwt
```

## 依赖列表

- `github.com/golang-jwt/jwt/v5` v5.2.0 - JWT认证库

## 兼容性

- Go 1.21+
- Windows 10+
- Linux (所有主流发行版)
- macOS 10.15+
