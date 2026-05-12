# API 性能监控系统 - Java 版

基于 UDP 传输的 API 性能监控系统 Java 完整实现，包含**客户端**（UDP 发送核心 + 可选的 HttpServer Filter）和**服务端**（UDP 接收 + 内存存储 + 日志持久化 + HTTP API + Web 仪表盘）。

与 PHP 版（`examples/api-monitor/php/`）、Golang 版（`examples/api-monitor/golang/`）功能完全对齐，API 接口、UDP 协议、日志文件格式、登录 token 全部兼容，前端仪表盘页面直接复用。使用 JDK 17 内置 `com.sun.net.httpserver.HttpServer`，零框架、零容器，依赖仅 Jackson + SnakeYAML。

## 功能特性

- 单个接口单次访问耗时记录
- 单个接口每分钟/每小时/每天访问次数统计
- 成功次数、失败次数、成功率
- 所有接口慢速排行、访问次数排行
- 实时接口访问量（最近 10 分钟）
- 全天访问趋势（1440 个分钟级数据点）
- 左侧菜单：按项目 → 类 → 方法分类
- 日期查询，前后 7 天快捷切换
- 日志持久化：定时导出为 JSON 文件，支持 session 分槽避免重启覆盖
- 历史数据查看：进程重启后自动从日志文件读取历史数据
- 访问密码保护：支持仪表盘登录验证（与 PHP/Go 版 token 兼容）

## 端口分配

| 服务 | 端口 | 协议 | 说明 |
|------|------|------|------|
| 监控服务端 UDP | 9501 | UDP | 接收客户端发送的性能数据 |
| 监控服务端 HTTP | 8095 | HTTP | Web 仪表盘 + API 接口 |

## 目录结构

```
examples/api-monitor/java/
├── src/main/java/com/example/monitor/
│   ├── config/
│   │   └── AppConfig.java              # YAML 配置加载
│   ├── client/
│   │   ├── MonitorClient.java          # UDP 发送核心（线程安全）
│   │   ├── MonitorFilter.java          # JDK HttpServer Filter（接入示例）
│   │   └── SimulatorMain.java          # 模拟客户端 main 入口
│   └── server/
│       ├── ServerMain.java             # 服务端 main 入口
│       ├── model/
│       │   ├── MonitorRecord.java      # UDP 接收的数据模型
│       │   ├── AggregationStats.java   # 聚合统计结构
│       │   └── RecordItem.java         # 访问明细记录
│       ├── storage/
│       │   ├── StorageInterface.java   # 存储接口
│       │   └── MemoryStorage.java      # 内存存储实现
│       ├── udp/
│       │   └── UdpReceiver.java        # UDP 数据接收器
│       ├── logger/
│       │   └── MonitorLogger.java      # 日志持久化（session 分槽）
│       └── http/
│           └── HttpController.java     # HTTP API 控制器
├── public/
│   └── index.html                      # Web 仪表盘前端（与 PHP 版完全一致）
├── config.yaml                         # 配置文件
├── pom.xml                             # Maven 构建
├── run-all.sh                          # Linux 一键启动脚本
├── run-all.bat                         # Windows 一键启动脚本
└── README.md
```

## 快速开始

### 前置条件

- JDK 17+
- Maven 3.6+

### 方式一：一键启动

**Linux / macOS：**

```bash
chmod +x examples/api-monitor/java/run-all.sh
examples/api-monitor/java/run-all.sh
```

**Windows：**

```bat
examples\api-monitor\java\run-all.bat
```

脚本执行流程：
1. 如果 `target/` 下没有 jar，自动执行 `mvn clean package`
2. 启动服务端（`api-monitor-server.jar`）
3. 等待 3 秒
4. 启动模拟客户端（`api-monitor-simulator.jar`）
5. 输出仪表盘访问地址：http://localhost:8095

Linux 按 `Ctrl+C` 停止所有服务；Windows 关闭对应窗口即可。

### 方式二：手动启动

```bash
cd examples/api-monitor/java

# 编译（产出 target/api-monitor-server.jar 和 target/api-monitor-simulator.jar）
mvn clean package

# 启动服务端
java -jar target/api-monitor-server.jar config.yaml

# 另开终端：启动模拟客户端
java -jar target/api-monitor-simulator.jar config.yaml
```

浏览器打开 http://localhost:8095

默认密码：`888888`（可在 `config.yaml` 中修改或设为空以禁用密码）。

## 在 Java 业务应用中接入

### 场景 1：JDK 内置 HttpServer

```java
import com.example.monitor.client.MonitorClient;
import com.example.monitor.client.MonitorFilter;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.util.List;

public class MyApp {
    public static void main(String[] args) throws Exception {
        MonitorClient mc = new MonitorClient("127.0.0.1", 9501, "my-project", 1.0, 10);
        Runtime.getRuntime().addShutdownHook(new Thread(mc::close));

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        // 注册业务 handler
        server.createContext("/api/users", exchange -> { /* ... */ })
              .getFilters().add(new MonitorFilter(mc, List.of("/health"), true));
        server.start();
    }
}
```

### 场景 2：Spring Boot / Servlet 容器

可以参考 `MonitorFilter` 的实现，自己封装为 `javax.servlet.Filter` 或 Spring `HandlerInterceptor`。`MonitorClient` 本身与框架无关，可以直接复用。

### 场景 3：手动上报

```java
MonitorClient mc = new MonitorClient("127.0.0.1", 9501, "my-project", 1.0, 10);

long start = System.nanoTime();
// ... 业务代码 ...
double durationMs = (System.nanoTime() - start) / 1_000_000.0;

mc.report("UserController", "getList", "/api/users", 200, durationMs,
          Map.of("page", 1), Map.of("code", 0));
mc.flush();
```

## 配置说明

配置文件 `config.yaml`（与 Golang 版格式完全一致）：

```yaml
monitor:
  enabled: true                 # 是否启用采集
  udp_host: "127.0.0.1"
  udp_port: 9501
  project: "demo-project"
  sample_rate: 1.0              # 采样率 0.0~1.0
  buffer_size: 10               # 批量发送缓冲区大小
  exclude:                      # 排除的路径（前缀匹配）
    - "/health"
    - "/favicon.ico"

server:
  http_port: 8095               # HTTP 服务端口
  udp_port: 9501                # UDP 监听端口
  storage_driver: "memory"      # 目前仅支持 memory
  password: "888888"            # 仪表盘密码（空字符串表示不需要密码）
  log:
    enable: true                # 是否启用日志持久化
    interval: 60                # 导出间隔（秒）
```

## 日志持久化

开启后，定时将统计数据导出为 JSON 文件到 `runtime/logs/monitor/` 目录：

```
runtime/logs/monitor/
  2026-05-13.json          # 天级汇总（dashboard、排行榜、树形结构）
  2026-05-13_minute.json   # 分钟级明细（periods + records）
```

使用 sessionId 分槽存储机制，每次进程启动生成唯一 sessionId，确保：
- 重启后历史数据不丢失
- 多次运行的数据互不干扰
- 查询时自动合并内存实时数据（当前 session）与日志历史数据（所有其他 session）

文件结构与 PHP 版、Golang 版完全一致，三种语言可以交替运行写入同一个日志目录。

## API 接口列表

所有接口返回统一 JSON 格式：`{"code": 0, "data": ...}`。

| 接口 | 方法 | 参数 | 说明 |
|------|------|------|------|
| `/` | GET | - | Web 仪表盘页面 |
| `/api/dashboard` | GET | `date` | 仪表盘概览 |
| `/api/tree` | GET | `date` | 树形菜单 |
| `/api/detail` | GET | `project`, `class`, `method`, `date`, `granularity` | 接口详情（minute/hour/day） |
| `/api/ranking/slow` | GET | `date`, `limit` | 慢速排行 |
| `/api/ranking/count` | GET | `date`, `limit` | 访问量排行 |
| `/api/realtime` | GET | - | 实时访问量（最近 10 分钟） |
| `/api/trend` | GET | `date` | 全天分钟级访问趋势（1440 个点） |
| `/api/search` | GET | `keyword`, `date` | 搜索接口 |
| `/api/dates` | GET | - | 可用日期列表（前后 7 天） |
| `/api/records` | GET | `project`, `class`, `method`, `minute`, `limit` | 访问明细 |
| `/api/login` | POST/GET | `password` | 登录（cookie 30 天） |
| `/api/check-login` | GET | - | 检查登录状态 |

## 与 PHP、Golang 版的关系

Java 版是 PHP / Golang 版的等价重写，**完全兼容**：

- **UDP 数据格式**：JSON 字段、类型、字段顺序一致，三种语言的 Client 可以向任意 Server 发送数据
- **HTTP API 接口**：13 个接口的路径、参数、返回结构完全一致
- **前端页面复用**：`public/index.html` 与 PHP、Golang 版完全相同
- **登录 token 兼容**：同样使用 `md5(password + "_monitor_salt")`，cookie 互通
- **日志文件格式兼容**：session 分槽结构相同，三种语言可以交替运行写入同一个 `runtime/logs/monitor/` 目录

| 对比项 | PHP 版 | Golang 版 | Java 版 |
|--------|--------|-----------|---------|
| HTTP 框架 | Webman/Workerman | GoFrame v2 | JDK 内置 HttpServer |
| 存储驱动 | 内存 / Redis | 内存 | 内存 |
| 客户端中间件 | ThinkPHP 中间件 | GoFrame 中间件 | JDK HttpServer Filter |
| 运行产物 | PHP 源码 | 单二进制 | 两个 fat jar |
| 外部依赖 | Composer | 无 | Maven（运行时仅 Jackson + SnakeYAML） |

## 运行环境

- Windows 10（IDE）/ Ubuntu 20.04（编译运行）均支持
- JDK 17 / Maven 已安装在虚拟机中，通过 `run-all.sh` 或手动 `mvn package` 执行

## 备注

- 使用 JDK 内置 HttpServer，避免引入 Spring Boot / Netty 等重量级依赖，打包后的 jar 体积较小
- UDP 接收和 HTTP 服务独立线程，互不阻塞
- `ShutdownHook` 保证优雅退出时完成最后一次日志导出
