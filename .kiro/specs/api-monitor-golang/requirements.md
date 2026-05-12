# 需求文档：API Monitor Golang 完整版（客户端 + 服务端）

## 简介

为现有的 API 性能监控系统创建完整的 Golang 版本，包含客户端和服务端，功能对标 PHP 版本。系统由两部分组成：

- **客户端**：通过 UDP 协议向监控服务端发送 JSON 格式的接口性能数据，包含 UDP 发送核心、GoFrame HTTP 中间件、模拟数据发送器
- **服务端**：接收 UDP 性能数据并存储、聚合、展示，包含 UDP 数据接收器、内存存储引擎、日志持久化、HTTP API、Web 仪表盘

Golang 服务端的 API 接口和数据格式与 PHP 版完全兼容，前端仪表盘页面（index.html）直接复用 PHP 版。服务端使用 GoFrame 框架的 HTTP 服务器，复用 golang-sdk 中的序列化器（`serializer` 包）和日志（`observability` 包）等已有能力。

## 术语表

- **Monitor_Client**: Golang 版 UDP 监控客户端核心组件，负责将接口性能数据通过 UDP 发送到监控服务端
- **Monitor_Middleware**: GoFrame HTTP 中间件，自动采集每个 HTTP 请求的性能数据并通过 Monitor_Client 上报
- **Simulator**: 模拟数据发送器，生成随机的 API 调用数据用于测试
- **Monitor_Server**: Golang 版监控服务端，监听 UDP 端口接收数据，HTTP 端口提供 Web 仪表盘和 API 接口
- **UDP_Receiver**: 服务端 UDP 数据接收器，监听 UDP 端口接收客户端发送的 JSON 性能数据
- **Storage_Interface**: 监控数据存储接口，定义所有数据读写操作的抽象层
- **Memory_Storage**: 内存存储引擎，在进程内存中按分钟/小时/天三级聚合统计数据
- **Monitor_Logger**: 日志持久化组件，定时将内存中的统计数据导出为 JSON 文件，支持历史数据查询
- **Session_Id**: 每次进程启动生成的唯一标识，用于日志分槽存储，避免重启后数据重复或丢失
- **HTTP_Controller**: HTTP API 控制器，提供与 PHP 版完全相同的 RESTful API 接口
- **Dashboard_Page**: Web 仪表盘页面，复用 PHP 版的 public/index.html 前端页面
- **Monitor_Record**: 单条监控数据记录，JSON 格式，包含 project、class、method、uri、status、duration、timestamp 等字段
- **Buffer**: 缓冲区，用于累积多条 Monitor_Record 后批量发送，减少 UDP 包数量
- **Sample_Rate**: 采样率，0.0~1.0 之间的浮点数，控制数据采集比例（1.0 表示 100% 采集）
- **Batch_Payload**: 批量发送的数据载荷，多条 JSON 记录用换行符（`\n`）分隔
- **Config_Manager**: golang-sdk 中的配置管理器（`config` 包），基于 GoFrame gcfg 实现 YAML 配置加载
- **Json_Serializer**: golang-sdk 中的 JSON 序列化器（`serializer` 包），用于将 Monitor_Record 序列化为 JSON
- **Framework_Logger**: golang-sdk 中的日志记录器（`observability` 包），基于 GoFrame glog 实现
- **Aggregation_Stats**: 聚合统计字段集合，包含 count、success、fail、total_time、max_time、min_time
- **Tree_Structure**: 项目→类→方法的树形菜单结构，用于前端左侧导航
- **Access_Password**: 访问密码，用于保护仪表盘的访问权限

## 需求

### 需求 1：Monitor_Client UDP 核心

**用户故事：** 作为开发者，我希望有一个 Golang 版的 UDP 监控客户端，以便在 Go 应用中将接口性能数据发送到监控服务端。

#### 验收标准

1. THE Monitor_Client SHALL 通过 UDP 协议向指定的 host 和 port 发送 Monitor_Record 数据
2. THE Monitor_Client SHALL 使用 golang-sdk 的 Json_Serializer 将 Monitor_Record 序列化为 JSON 字符串
3. WHEN Monitor_Client 的 Report 方法被调用时，THE Monitor_Client SHALL 将 Monitor_Record 追加到 Buffer 中
4. WHEN Buffer 中的记录数量达到配置的 buffer_size 时，THE Monitor_Client SHALL 自动将 Buffer 中所有记录组装为 Batch_Payload 并通过 UDP 发送
5. THE Monitor_Client SHALL 使用换行符（`\n`）分隔 Batch_Payload 中的多条 JSON 记录，与 Monitor_Server 的数据格式保持一致
6. WHEN Sample_Rate 小于 1.0 时，THE Monitor_Client SHALL 按照 Sample_Rate 的概率随机决定是否采集当前请求的数据
7. THE Monitor_Client SHALL 提供 Flush 方法，将 Buffer 中所有未发送的记录立即发送
8. WHEN Monitor_Client 被关闭（Close）时，THE Monitor_Client SHALL 自动调用 Flush 方法发送 Buffer 中剩余的数据
9. IF UDP 发送操作失败，THEN THE Monitor_Client SHALL 静默忽略错误，不影响业务逻辑的正常执行
10. THE Monitor_Client SHALL 生成的 Monitor_Record 包含以下字段：project（字符串）、class（字符串）、method（字符串）、uri（字符串）、status（整数）、duration（浮点数，毫秒）、timestamp（Unix 时间戳整数）
11. WHEN Report 方法传入了非空的 params 参数时，THE Monitor_Client SHALL 在 Monitor_Record 中包含 params 字段
12. WHEN Report 方法传入了非空的 response 参数时，THE Monitor_Client SHALL 在 Monitor_Record 中包含 response 字段
13. THE Monitor_Client SHALL 支持并发安全，允许多个 goroutine 同时调用 Report 方法

### 需求 2：Monitor_Middleware GoFrame HTTP 中间件

**用户故事：** 作为 GoFrame Web 应用开发者，我希望通过注册一个中间件即可自动采集每个 HTTP 请求的性能数据，无需手动在每个接口中添加监控代码。

#### 验收标准

1. THE Monitor_Middleware SHALL 实现 GoFrame 的 ghttp.MiddlewareFunc 接口，可通过 `s.Use(middleware)` 注册为全局中间件
2. WHEN 一个 HTTP 请求经过 Monitor_Middleware 时，THE Monitor_Middleware SHALL 记录请求开始时间，在请求处理完成后计算耗时（毫秒），并通过 Monitor_Client 上报数据
3. THE Monitor_Middleware SHALL 从 GoFrame 请求对象中提取 URI、HTTP 状态码作为 Monitor_Record 的 uri 和 status 字段
4. THE Monitor_Middleware SHALL 从请求的路由信息中解析控制器名和方法名作为 Monitor_Record 的 class 和 method 字段
5. IF 无法从路由信息中解析控制器名和方法名，THEN THE Monitor_Middleware SHALL 从 URI 路径中提取，使用第一段作为 class（首字母大写加 Controller 后缀），第二段作为 method（默认 index）
6. WHEN 请求的 URI 匹配配置中的 exclude 排除路径列表时，THE Monitor_Middleware SHALL 跳过该请求，不进行数据采集
7. WHEN 配置中 enabled 为 false 时，THE Monitor_Middleware SHALL 直接放行请求，不执行任何监控逻辑

### 需求 3：Simulator 模拟数据发送器

**用户故事：** 作为开发者，我希望有一个模拟客户端持续发送随机的 API 调用数据，以便在没有真实业务流量时测试监控系统的数据接收和展示功能。

#### 验收标准

1. THE Simulator SHALL 预定义一组模拟 API 列表，包含控制器名、方法名、URI 和耗时范围，与 PHP 版 simulate.php 的模拟数据保持一致
2. THE Simulator SHALL 在每轮循环中随机选择 1~5 个 API 进行模拟调用
3. THE Simulator SHALL 按照加权概率随机生成 HTTP 状态码（200 占 85%、201 占 5%、400 占 3%、401 占 2%、404 占 2%、500 占 3%）
4. THE Simulator SHALL 在每条模拟数据的耗时范围内随机生成 duration 值
5. THE Simulator SHALL 为每条模拟数据生成随机的 params（page、id）和 response（code、msg）字段
6. THE Simulator SHALL 在每轮发送后随机等待 100ms~1000ms，模拟真实的请求间隔
7. THE Simulator SHALL 在终端输出每条发送的数据摘要，包含时间、序号、状态码（成功绿色、失败红色）、控制器.方法、耗时
8. WHEN 用户按下 Ctrl+C 时，THE Simulator SHALL 优雅退出，调用 Monitor_Client 的 Flush 方法发送剩余数据后退出
9. THE Simulator SHALL 从 config.yaml 配置文件读取 UDP 目标地址、端口、项目名等配置

### 需求 4：YAML 配置文件

**用户故事：** 作为开发者，我希望通过 YAML 配置文件管理监控系统（客户端和服务端）的所有参数，与项目中其他 Golang 示例的配置风格保持一致。

#### 验收标准

1. THE Config_File SHALL 使用 YAML 格式，文件名为 config.yaml，放置在 Golang 项目根目录
2. THE Config_File SHALL 在 `monitor` 顶层键下包含客户端配置项：enabled（布尔）、udp_host（字符串）、udp_port（整数）、project（字符串）、sample_rate（浮点数）、buffer_size（整数）、exclude（字符串数组）
3. THE Config_File SHALL 在 `server` 顶层键下包含服务端配置项：http_port（整数）、udp_port（整数）、storage_driver（字符串）、password（字符串）
4. THE Config_File SHALL 在 `server.log` 子键下包含日志持久化配置：enable（布尔）、interval（整数，秒）
5. THE Config_File SHALL 提供合理的客户端默认值：enabled=true、udp_host=127.0.0.1、udp_port=9501、project=demo-project、sample_rate=1.0、buffer_size=10、exclude=[/health, /favicon.ico]
6. THE Config_File SHALL 提供合理的服务端默认值：http_port=8095、udp_port=9501、storage_driver=memory、password=888888、log.enable=true、log.interval=60
7. THE Config_File SHALL 遵循项目中已有 Golang 示例（hello-world/golang/config.yaml）的配置结构风格

### 需求 5：跨平台启动脚本

**用户故事：** 作为开发者，我希望有一键启动脚本同时启动 Golang 监控服务端和 Golang 模拟客户端，方便快速演示和测试完整的 Golang 版监控系统。

#### 验收标准

1. THE Run_Script SHALL 提供 run-all.sh（Linux/macOS）和 run-all.bat（Windows）两个版本
2. THE Run_Script SHALL 按顺序执行：先编译并启动 Golang Monitor_Server，等待 3 秒后编译并启动 Golang Simulator
3. THE run-all.sh SHALL 在收到 SIGINT/SIGTERM 信号时停止所有已启动的进程
4. THE run-all.bat SHALL 使用 `start` 命令在新窗口中启动各服务，关闭窗口即可停止
5. THE Run_Script SHALL 在启动完成后输出仪表盘访问地址（http://localhost:8095）
6. THE Run_Script SHALL 先编译 Golang 服务端和 Simulator 为可执行文件，再运行编译后的二进制文件
7. THE Run_Script SHALL 不依赖 PHP 运行环境，完全使用 Golang 版服务端替代 PHP 版

### 需求 6：Go Module 与项目结构

**用户故事：** 作为开发者，我希望 Golang 项目结构清晰，依赖管理规范，能够复用 golang-sdk 中的已有能力。

#### 验收标准

1. THE Go_Module SHALL 使用独立的 go.mod 文件，module 名称遵循项目命名规范
2. THE Go_Module SHALL 通过 replace 指令引用本地的 golang-sdk，与 hello-world/golang 示例保持一致的依赖引用方式
3. THE Go_Module SHALL 复用 golang-sdk 的 serializer 包进行 JSON 序列化
4. THE Go_Module SHALL 复用 golang-sdk 的 observability 包进行日志记录
5. THE Project_Structure SHALL 将 Monitor_Client 和 Monitor_Middleware 的源码放在独立的客户端子包中
6. THE Project_Structure SHALL 将 Storage_Interface、Memory_Storage、UDP_Receiver、Monitor_Logger、HTTP_Controller 的源码放在独立的服务端子包中
7. THE Project_Structure SHALL 提供 Simulator 的 main 入口和 Monitor_Server 的 main 入口作为两个独立的可执行程序

### 需求 7：README 文档

**用户故事：** 作为开发者，我希望有清晰的 README 文档说明 Golang 版 API 监控系统的完整功能、使用方式和项目结构。

#### 验收标准

1. THE README SHALL 使用中文编写，包含功能简介、架构图、目录结构、快速开始、配置说明等章节
2. THE README SHALL 说明 Golang 版是一个完整的监控系统（客户端+服务端），与 PHP 版功能对标且 API 兼容
3. THE README SHALL 提供在 GoFrame 应用中接入 Monitor_Middleware 的代码示例
4. THE README SHALL 说明如何通过一键脚本和手动方式分别启动系统
5. THE README SHALL 包含服务端配置说明，涵盖存储驱动、日志持久化、HTTP 端口、UDP 端口、访问密码等配置项
6. THE README SHALL 包含完整的 API 接口列表及参数说明

### 需求 8：UDP_Receiver 数据接收器

**用户故事：** 作为系统运维人员，我希望 Golang 服务端能够通过 UDP 端口接收客户端发送的性能数据，以便实时采集监控信息。

#### 验收标准

1. THE UDP_Receiver SHALL 监听配置指定的 UDP 端口（默认 9501）接收客户端发送的 JSON 性能数据
2. THE UDP_Receiver SHALL 支持批量数据解析，使用换行符（`\n`）分隔同一个 UDP 包中的多条 JSON 记录
3. WHEN UDP_Receiver 接收到数据时，THE UDP_Receiver SHALL 逐行解析 JSON，将每条有效的 Monitor_Record 写入 Storage_Interface
4. IF 某行 JSON 解析失败，THEN THE UDP_Receiver SHALL 跳过该行并继续处理后续行，不中断整个数据包的处理
5. IF 某行 JSON 解析后不是有效的 Monitor_Record（缺少必要字段），THEN THE UDP_Receiver SHALL 跳过该记录
6. THE UDP_Receiver SHALL 支持并发处理多个客户端同时发送的数据
7. THE UDP_Receiver SHALL 在启动时输出日志，显示监听的 UDP 端口号

### 需求 9：Storage_Interface 存储接口

**用户故事：** 作为开发者，我希望存储层有清晰的接口定义，以便未来扩展不同的存储后端（如 Redis）。

#### 验收标准

1. THE Storage_Interface SHALL 定义 Record(data) 方法，用于写入一条 Monitor_Record
2. THE Storage_Interface SHALL 定义 GetTree() 方法，返回项目→类→方法的 Tree_Structure
3. THE Storage_Interface SHALL 定义 GetDashboard(date) 方法，返回指定日期的仪表盘概览数据，包含 date、total_count、success、fail、success_rate、avg_time 字段
4. THE Storage_Interface SHALL 定义 GetDetail(project, class, method, date, granularity) 方法，返回指定接口在指定日期按指定粒度（minute/hour/day）的 Aggregation_Stats 时序数据
5. THE Storage_Interface SHALL 定义 GetSlowRanking(date, limit) 方法，返回指定日期按平均耗时降序排列的接口排行榜
6. THE Storage_Interface SHALL 定义 GetCountRanking(date, limit) 方法，返回指定日期按访问次数降序排列的接口排行榜
7. THE Storage_Interface SHALL 定义 GetRealtime() 方法，返回最近 10 分钟每分钟的访问量统计
8. THE Storage_Interface SHALL 定义 Search(keyword, date) 方法，返回接口名称中包含关键词的接口列表
9. THE Storage_Interface SHALL 定义 GetDayTrend(date) 方法，返回指定日期全天 1440 个分钟级数据点的访问趋势
10. THE Storage_Interface SHALL 定义 HasData(date) 方法，判断指定日期是否有监控数据
11. THE Storage_Interface SHALL 定义 GetRecords(project, class, method, minute, limit) 方法，返回指定接口在指定分钟的访问明细列表

### 需求 10：Memory_Storage 内存存储引擎

**用户故事：** 作为系统运维人员，我希望服务端开箱即用，无需外部依赖即可存储和查询监控数据。

#### 验收标准

1. THE Memory_Storage SHALL 实现 Storage_Interface 的所有方法
2. THE Memory_Storage SHALL 按分钟、小时、天三级粒度聚合统计数据
3. THE Memory_Storage SHALL 为每条记录维护 Aggregation_Stats，包含 count、success、fail、total_time、max_time、min_time 六个统计字段
4. WHEN Memory_Storage 的 Record 方法被调用时，THE Memory_Storage SHALL 同时更新分钟级、小时级、天级三个维度的 Aggregation_Stats
5. THE Memory_Storage SHALL 维护 Tree_Structure，记录每个 project 下的 class 和 method 及其对应的 URI
6. THE Memory_Storage SHALL 保存访问明细记录，每个接口（key）每分钟最多保留 200 条明细
7. THE Memory_Storage SHALL 自动清理超过 2 小时的访问明细记录，释放内存
8. WHEN 判断请求是否成功时，THE Memory_Storage SHALL 将 HTTP 状态码 200~399 视为成功，400 及以上视为失败
9. THE Memory_Storage SHALL 支持并发安全，使用读写锁保护共享数据结构
10. THE Memory_Storage SHALL 在 GetDayTrend 方法中返回完整的 1440 个数据点，无数据的分钟填充零值
11. THE Memory_Storage SHALL 在 GetRealtime 方法中返回最近 10 分钟的数据，按时间正序排列
12. THE Memory_Storage SHALL 在 Search 方法中对接口的 project、class、method、uri 进行大小写不敏感的关键词匹配

### 需求 11：Monitor_Logger 日志持久化

**用户故事：** 作为系统运维人员，我希望监控数据能够定时持久化到文件，以便进程重启后不丢失历史数据。

#### 验收标准

1. THE Monitor_Logger SHALL 在进程启动时生成唯一的 Session_Id（格式：`s_{timestamp}_{random6}`），用于标识当前运行周期
2. THE Monitor_Logger SHALL 按配置的时间间隔（默认 60 秒）定时将内存中的统计数据导出为 JSON 文件
3. THE Monitor_Logger SHALL 导出两类文件：天级汇总文件（`{date}.json`）和分钟级明细文件（`{date}_minute.json`）
4. THE Monitor_Logger SHALL 使用 Session_Id 分槽存储，每次导出只更新当前 Session_Id 对应的槽位，不影响其他 Session 的数据
5. THE Monitor_Logger SHALL 在天级汇总文件中包含 dashboard（仪表盘概览）、slow_ranking（慢速排行）、count_ranking（访问量排行）、tree（树形结构）数据
6. THE Monitor_Logger SHALL 在分钟级明细文件中包含每个接口的分钟级 Aggregation_Stats 和访问明细记录
7. WHEN 读取历史数据时，THE Monitor_Logger SHALL 合并所有 Session 的数据（排除当前 Session_Id），将各 Session 的统计数据累加返回
8. WHEN 进程收到退出信号时，THE Monitor_Logger SHALL 触发最后一次导出，确保内存中的最新数据不丢失
9. THE Monitor_Logger SHALL 支持从日志文件读取指定日期的天级汇总数据
10. THE Monitor_Logger SHALL 支持从日志文件读取指定日期的分钟级明细数据
11. THE Monitor_Logger SHALL 支持从日志文件读取指定接口在指定分钟的访问明细记录
12. THE Monitor_Logger SHALL 提供获取所有可用日期列表的方法，通过扫描日志目录中的文件名获取
13. THE Monitor_Logger SHALL 在日志持久化配置关闭时不启动定时导出任务

### 需求 12：HTTP_Controller API 控制器

**用户故事：** 作为前端仪表盘开发者，我希望 Golang 服务端提供与 PHP 版完全相同的 API 接口，以便前端页面无需修改即可对接。

#### 验收标准

1. THE HTTP_Controller SHALL 使用 GoFrame 的 ghttp.Server 提供 HTTP 服务，监听配置指定的端口（默认 8095）
2. THE HTTP_Controller SHALL 提供 `GET /api/dashboard` 接口，接受 date 查询参数，返回仪表盘概览数据
3. THE HTTP_Controller SHALL 提供 `GET /api/tree` 接口，接受 date 查询参数，返回 Tree_Structure 数据
4. THE HTTP_Controller SHALL 提供 `GET /api/detail` 接口，接受 project、class、method、date、granularity 查询参数，返回接口详情数据
5. THE HTTP_Controller SHALL 提供 `GET /api/ranking/slow` 接口，接受 date、limit 查询参数，返回慢速接口排行榜
6. THE HTTP_Controller SHALL 提供 `GET /api/ranking/count` 接口，接受 date、limit 查询参数，返回访问次数排行榜
7. THE HTTP_Controller SHALL 提供 `GET /api/realtime` 接口，返回最近 10 分钟的实时访问量数据
8. THE HTTP_Controller SHALL 提供 `GET /api/trend` 接口，接受 date 查询参数，返回全天 1440 个分钟级数据点的访问趋势
9. THE HTTP_Controller SHALL 提供 `GET /api/search` 接口，接受 keyword、date 查询参数，返回匹配的接口列表
10. THE HTTP_Controller SHALL 提供 `GET /api/dates` 接口，返回可用日期列表（前后 7 天，标注是否有数据及数据来源）
11. THE HTTP_Controller SHALL 提供 `GET /api/records` 接口，接受 project、class、method、minute、limit 查询参数，返回访问明细
12. THE HTTP_Controller SHALL 提供 `POST /api/login` 接口，接受 password 参数，验证 Access_Password 并通过 cookie 返回登录凭证
13. THE HTTP_Controller SHALL 提供 `GET /api/check-login` 接口，检查当前请求的 cookie 中是否包含有效的登录凭证
14. THE HTTP_Controller SHALL 所有 API 接口返回统一的 JSON 格式：`{"code": 0, "data": ...}`，与 PHP 版保持一致
15. THE HTTP_Controller SHALL 在查询数据时合并内存实时数据与日志历史数据返回，确保数据完整性
16. THE HTTP_Controller SHALL 在合并排行榜数据时，对相同接口的统计数据进行累加，并按指定字段重新排序

### 需求 13：Dashboard_Page Web 仪表盘

**用户故事：** 作为系统运维人员，我希望通过浏览器访问监控仪表盘，查看 API 性能数据的可视化展示。

#### 验收标准

1. THE HTTP_Controller SHALL 提供 `GET /` 路由，返回 Dashboard_Page 的 HTML 内容
2. THE Dashboard_Page SHALL 复用 PHP 版的 `public/index.html` 前端页面文件，将该文件复制到 Golang 服务端的对应目录
3. THE HTTP_Controller SHALL 以 `text/html; charset=utf-8` 的 Content-Type 返回 Dashboard_Page
4. IF Dashboard_Page 的 HTML 文件不存在，THEN THE HTTP_Controller SHALL 返回 HTTP 404 状态码和提示信息

### 需求 14：Access_Password 访问密码

**用户故事：** 作为系统管理员，我希望能够为监控仪表盘设置访问密码，防止未授权的人员查看监控数据。

#### 验收标准

1. THE HTTP_Controller SHALL 从配置文件读取 Access_Password 配置项
2. WHEN Access_Password 配置为空字符串时，THE HTTP_Controller SHALL 不进行任何密码验证，所有请求直接放行
3. WHEN Access_Password 配置为非空字符串时，THE HTTP_Controller SHALL 在 login 接口中验证用户提交的密码是否与 Access_Password 一致
4. WHEN 密码验证通过时，THE HTTP_Controller SHALL 生成登录凭证（使用 `md5(password + "_monitor_salt")` 算法），通过 Set-Cookie 响应头写入名为 `monitor_token` 的 cookie，有效期 30 天
5. WHEN check-login 接口被调用时，THE HTTP_Controller SHALL 检查请求中 `monitor_token` cookie 的值是否与预期凭证匹配，返回 `{"need_login": true/false}`
6. THE HTTP_Controller SHALL 使用与 PHP 版相同的凭证生成算法（`md5(password + "_monitor_salt")`），确保 cookie 兼容

