# API 性能监控系统

基于 UDP 传输的 API 性能监控系统，包含监控服务端和 PHP 接入 SDK。

## 架构

```
┌─────────────────┐    UDP     ┌──────────────────┐    HTTP    ┌──────────────┐
│  ThinkPHP6 应用  │ ────────→ │  监控服务端(PHP)   │ ←──────── │  Web 仪表盘   │
│  (接入中间件)     │  :9501    │  Workerman        │   :8095   │  浏览器       │
└─────────────────┘           └──────────────────┘           └──────────────┘
```

## 端口分配

| 服务 | 端口 | 协议 | 说明 |
|------|------|------|------|
| 监控服务端 UDP | 9501 | UDP | 接收性能数据 |
| 监控服务端 HTTP | 8095 | HTTP | Web 仪表盘 + API |

## 功能

- 单个接口单次访问耗时记录
- 单个接口每分钟/每小时/每天访问次数统计
- 成功次数、失败次数、成功率
- 所有接口慢速排行
- 所有接口访问次数排行
- 实时接口访问量
- 左侧菜单：按项目 → 类 → 方法分类
- 右上角：接口与日期查询

## 快速开始

### 方式一：一键启动

**Windows:**
```bat
examples\api-monitor\run-all.bat
```

**Linux:**
```bash
chmod +x examples/api-monitor/run-all.sh
examples/api-monitor/run-all.sh
```

### 方式二：分别启动

**1. 启动监控服务端**
```bash
# Windows
php examples/api-monitor/server/windows.php

# Linux
php examples/api-monitor/server/start.php start
```

**2. 启动模拟客户端（测试用）**
```bash
php examples/api-monitor/client/simulate.php
```

**3. 访问仪表盘**

浏览器打开 http://localhost:8095

## ThinkPHP6 接入

### 1. 复制接入文件

将 `client/src/` 目录下的文件复制到你的 ThinkPHP6 项目：

```
app/middleware/ApiMonitor.php
app/common/MonitorClient.php
```

### 2. 注册全局中间件

编辑 `app/middleware.php`：
```php
return [
    \app\middleware\ApiMonitor::class,
];
```

### 3. 配置

编辑 `config/monitor.php`：
```php
return [
    'enabled'     => true,
    'udp_host'    => '127.0.0.1',
    'udp_port'    => 9501,
    'project'     => 'my-project',
    'sample_rate' => 1.0,  // 采样率 0.0~1.0
];
```
