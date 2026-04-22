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
- 右上角：接口与日期查询，支持前后 7 天快捷切换
- 历史数据查看：Redis/内存数据过期后自动从日志文件读取
- 日志持久化：定时将统计数据导出为 JSON 文件长期保存

## 存储配置

支持两种存储驱动，通过 `server/config/monitor.php` 配置：

```php
return [
    // 存储驱动：'memory' 或 'redis'
    'driver' => 'memory',

    // 日志持久化配置
    'log' => [
        'enable'   => false,          // 是否启用日志持久化
        'interval' => 300,            // 导出间隔（秒），默认 5 分钟
    ],
];
```

### 内存模式（默认）

- 无外部依赖，开箱即用
- 自动降为单 worker 进程（多进程间内存不共享）
- 进程重启后数据丢失（建议开启日志持久化）

### Redis 模式

- 需要安装 PHP redis 扩展（`php -m | grep redis`）
- 支持多 worker 进程，数据共享
- 数据自动过期：分钟级 1 天、小时级 3 天、天级 30 天
- 如果 redis 扩展未安装或连接失败，自动回退到内存模式

Redis 连接配置在 `server/config/redis.php`：

```php
return [
    'host'     => '127.0.0.1',
    'port'     => 6379,
    'password' => '',
    'db'       => 0,
];
```

### 日志持久化

开启后，定时将统计数据导出为 JSON 文件，保存在 `server/runtime/logs/monitor/` 目录：

```
runtime/logs/monitor/
  2026-04-22.json          # 天级汇总（仪表盘、排行榜、树形结构）
  2026-04-22_hour.json     # 小时级明细
```

查看历史数据时，如果 Redis/内存中无数据，会自动从日志文件读取。

## 快速开始

### 前置条件

- PHP >= 8.1，需要以下扩展：
  - `pcntl`、`posix`、`sockets`（Workerman 必需）
  - `openssl`、`mbstring`、`curl`（常用）
  - `redis`（仅 Redis 模式需要）
- 已执行 `composer install`（在 `php-sdk/` 目录下）

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

## 目录结构

```
examples/api-monitor/
├── server/
│   ├── app/
│   │   ├── controller/
│   │   │   └── MonitorController.php    # HTTP 控制器
│   │   └── service/
│   │       ├── StorageInterface.php     # 存储接口
│   │       ├── MemoryStorage.php        # 内存存储实现
│   │       ├── RedisStorage.php         # Redis 存储实现
│   │       ├── MonitorStorage.php       # 存储代理（自动选择后端）
│   │       ├── MonitorLogger.php        # 日志持久化
│   │       └── UdpReceiver.php          # UDP 数据接收器
│   ├── config/
│   │   ├── monitor.php                  # 存储驱动 + 日志持久化配置
│   │   ├── redis.php                    # Redis 连接配置
│   │   ├── server.php                   # Workerman 服务配置
│   │   ├── route.php                    # 路由配置
│   │   └── ...
│   ├── public/
│   │   └── index.html                   # Web 仪表盘前端页面
│   ├── support/
│   │   ├── bootstrap.php                # Webman 启动引导
│   │   ├── SimpleContainer.php          # PSR-11 容器
│   │   └── Request.php
│   ├── start.php                        # Linux 启动入口
│   └── windows.php                      # Windows 启动入口
├── client/
│   ├── src/
│   │   ├── ApiMonitor.php               # ThinkPHP 中间件
│   │   └── MonitorClient.php            # UDP 客户端
│   ├── config/
│   │   └── monitor.php                  # 客户端配置示例
│   └── simulate.php                     # 模拟数据客户端
└── README.md
```

## API 接口

| 接口 | 说明 |
|------|------|
| `GET /` | Web 仪表盘页面 |
| `GET /api/dashboard?date=` | 仪表盘概览 |
| `GET /api/tree?date=` | 树形菜单 |
| `GET /api/detail?project=&class=&method=&date=&granularity=` | 接口详情 |
| `GET /api/ranking/slow?date=&limit=` | 慢速排行 |
| `GET /api/ranking/count?date=&limit=` | 访问量排行 |
| `GET /api/realtime` | 实时访问量（最近 10 分钟） |
| `GET /api/search?keyword=&date=` | 搜索接口 |
| `GET /api/dates` | 可用日期列表（前后 7 天） |

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
