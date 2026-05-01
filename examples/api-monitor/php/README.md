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

支持两种存储驱动，通过 `config/monitor.php` 配置：

```php
return [
    // 存储驱动：'memory' 或 'redis'
    'driver' => 'memory',

    // 访问密码（为空则不需要密码）
    'password' => 'zda888',

    // 日志持久化配置
    'log' => [
        'enable'   => true,           // 是否启用日志持久化（建议开启）
        'interval' => 60,             // 导出间隔（秒），默认 1 分钟
    ],
];
```

### 访问密码

通过 `password` 配置项控制仪表盘的访问权限：

- 设置密码后，访问首页需要先输入密码，验证通过后通过 cookie 保持登录 30 天
- 设置为空字符串 `''` 则不需要密码，任何人都可以直接访问
- 登录后页面右上角有「退出」按钮，点击后清除登录状态

### 内存模式（默认）

- 无外部依赖，开箱即用
- 自动降为单 worker 进程（多进程间内存不共享）
- 进程重启后数据丢失（建议开启日志持久化）

### Redis 模式

- 需要安装 PHP redis 扩展（`php -m | grep redis`）
- 支持多 worker 进程，数据共享
- 数据自动过期：分钟级 1 天、小时级 3 天、天级 30 天
- 如果 redis 扩展未安装或连接失败，自动回退到内存模式

Redis 连接配置在 `config/redis.php`：

```php
return [
    'host'     => '127.0.0.1',
    'port'     => 6379,
    'password' => '',
    'db'       => 0,
];
```

### 日志持久化

开启后，定时将统计数据导出为 JSON 文件，保存在 `runtime/logs/monitor/` 目录：

```
runtime/logs/monitor/
  2026-04-22.json            # 天级汇总（仪表盘、排行榜、树形结构）
  2026-04-22_minute.json     # 分钟级明细（分时统计 + 访问明细记录）
```

#### 数据流与 sessionId 机制

为避免服务重启后数据重复或丢失，采用 sessionId 分槽存储机制：

```
进程启动 → 生成唯一 sessionId（如 "s_1713780000_a1b2c3"）
    │
    ▼
┌─── 数据写入 ──────────────────────────────────────────────┐
│  客户端 UDP 数据 → 写入内存/Redis（当前 session 的实时数据）│
└───────────────────────────────────────────────────────────┘
    │
    ▼
┌─── 定时导出（每 60 秒）──────────────────────────────────┐
│  读取内存数据，写入日志文件中当前 session 的槽位           │
│                                                          │
│  日志文件结构（按 session 分槽，互不干扰）：              │
│  {                                                       │
│    "sessions": {                                         │
│      "s_aaa": { 第 1 次运行的数据 },                     │
│      "s_bbb": { 第 2 次运行的数据 },                     │
│      "s_ccc": { 当前运行的数据 }  ← 只更新这个槽位       │
│    }                                                     │
│  }                                                       │
└──────────────────────────────────────────────────────────┘
    │
    ▼
┌─── API 查询 ─────────────────────────────────────────────┐
│  1. 读内存/Redis → 当前 session 的实时数据                │
│  2. 读日志文件   → 排除当前 session，合并所有历史 session  │
│  3. 两者累加返回                                          │
│                                                          │
│  ✓ 不重复：内存 = 当前 session                            │
│            日志 = 历史 session（已排除当前 session）       │
│  ✓ 不丢失：每个 session 的数据在独立槽位中，不会被覆盖    │
└──────────────────────────────────────────────────────────┘
```

重启后的行为：

- 生成新的 sessionId，内存为空
- 查询时：日志中所有历史 session 累加 + 内存（空）= 历史数据完整
- 新数据写入后：日志（历史 session）+ 内存（新 session）= 全部数据
- 导出时：只写新 session 槽位，历史槽位不受影响
- 进程退出时通过 `register_shutdown_function` 触发最后一次导出

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
examples\api-monitor\php\run-all.bat
```

**Linux:**
```bash
chmod +x examples/api-monitor/php/run-all.sh
examples/api-monitor/php/run-all.sh
```

### 方式二：分别启动

**1. 启动监控服务端**
```bash
# Windows
php examples/api-monitor/php/windows.php

# Linux
php examples/api-monitor/php/start.php start
```

**2. 启动模拟客户端（测试用）**
```bash
php examples/api-monitor/php/client/simulate.php
```

**3. 访问仪表盘**

浏览器打开 http://localhost:8095

## 目录结构

```
examples/api-monitor/php/
├── app/
│   ├── controller/
│   │   └── MonitorController.php    # HTTP 控制器
│   └── service/
│       ├── StorageInterface.php     # 存储接口
│       ├── MemoryStorage.php        # 内存存储实现
│       ├── RedisStorage.php         # Redis 存储实现
│       ├── MonitorStorage.php       # 存储代理（自动选择后端）
│       ├── MonitorLogger.php        # 日志持久化
│       └── UdpReceiver.php          # UDP 数据接收器
├── config/
│   ├── monitor.php                  # 存储驱动 + 日志持久化配置
│   ├── redis.php                    # Redis 连接配置
│   ├── server.php                   # Workerman 服务配置
│   ├── route.php                    # 路由配置
│   └── ...
├── public/
│   └── index.html                   # Web 仪表盘前端页面
├── support/
│   ├── bootstrap.php                # Webman 启动引导
│   ├── SimpleContainer.php          # PSR-11 容器
│   └── Request.php
├── client/
│   ├── src/
│   │   ├── ApiMonitor.php           # ThinkPHP 中间件
│   │   └── MonitorClient.php        # UDP 客户端
│   ├── config/
│   │   └── monitor.php              # 客户端配置示例
│   └── simulate.php                 # 模拟数据客户端
├── start.php                        # Linux 启动入口
├── windows.php                      # Windows 启动入口
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
| `GET /api/trend?date=` | 全天访问趋势（分钟级，1440 个点） |
| `GET /api/search?keyword=&date=` | 搜索接口 |
| `GET /api/dates` | 可用日期列表（前后 7 天） |
| `GET /api/records?project=&class=&method=&minute=` | 某接口某分钟的访问明细 |

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
