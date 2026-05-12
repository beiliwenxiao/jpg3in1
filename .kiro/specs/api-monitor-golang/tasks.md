# 实现计划：API Monitor Golang 完整版

## 概述

基于需求文档（14 个需求）和设计文档（8 个组件、17 个正确性属性），将 Golang 版 API 性能监控系统分解为可增量执行的编码任务。实现顺序：基础设施 → 数据模型 → 存储层 → 客户端 → 服务端接收 → 日志持久化 → HTTP API → 启动入口 → 集成脚本。

所有代码位于 `examples/api-monitor/golang/` 目录下。

## Tasks

- [x] 1. 项目基础设施搭建
  - [x] 1.1 创建 go.mod 和项目目录结构
    - 创建 `examples/api-monitor/golang/go.mod`，module 名称为 `api-monitor-golang`
    - 依赖 `github.com/gogf/gf/v2 v2.6.0`、`github.com/leanovate/gopter v0.2.9`
    - 通过 `replace` 指令引用本地 `golang-sdk`（参考 `examples/hello-world/golang/go.mod`）
    - 创建目录结构：`cmd/server/`、`cmd/simulator/`、`internal/client/`、`internal/server/`、`public/`
    - _Requirements: 6.1, 6.2, 6.5, 6.6, 6.7_

  - [x] 1.2 创建 config.yaml 配置文件
    - 在 `examples/api-monitor/golang/config.yaml` 中定义 `monitor` 和 `server` 两个顶层键
    - 客户端配置：enabled、udp_host、udp_port、project、sample_rate、buffer_size、exclude
    - 服务端配置：http_port、udp_port、storage_driver、password、log.enable、log.interval
    - 提供设计文档中指定的默认值
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7_

  - [x] 1.3 创建数据模型和存储接口定义
    - 在 `internal/server/storage.go` 中定义所有数据模型结构体：MonitorRecord、AggregationStats、DashboardData、DetailData、SlowRankingItem、CountRankingItem、TimeSeriesItem、RecordItem、SearchItem
    - 定义 `StorageInterface` 接口，包含 Record、GetTree、GetDashboard、GetDetail、GetSlowRanking、GetCountRanking、GetRealtime、Search、GetDayTrend、HasData、GetRecords 共 11 个方法
    - 所有 JSON tag 与 PHP 版保持一致
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7, 9.8, 9.9, 9.10, 9.11_

- [x] 2. MemoryStorage 内存存储引擎
  - [x] 2.1 实现 MemoryStorage 核心存储逻辑
    - 在 `internal/server/memory_storage.go` 中实现 `MemoryStorage` 结构体
    - 实现 Record 方法：按分钟/小时/天三级聚合，维护 count、success、fail、total_time、max_time、min_time
    - 实现 aggregate 辅助方法，HTTP 状态码 200~399 为成功，400+ 为失败
    - 维护 tree 结构（project → class → method → uri）
    - 保存访问明细，每个 key+minute 最多 200 条
    - 自动清理超过 2 小时的明细记录
    - 使用 `sync.RWMutex` 保护所有共享数据结构
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.7, 10.8, 10.9_

  - [x] 2.2 实现 MemoryStorage 查询方法
    - 实现 GetTree、GetDashboard、GetDetail、GetSlowRanking、GetCountRanking 方法
    - 实现 GetRealtime（最近 10 分钟，按时间正序）、GetDayTrend（完整 1440 个数据点）
    - 实现 Search（大小写不敏感匹配 project、class、method、uri）
    - 实现 HasData、GetRecords 方法
    - _Requirements: 10.10, 10.11, 10.12_

  - [ ]* 2.3 编写 MemoryStorage 属性测试
    - **Property 8: 三级聚合统计正确性** — 生成随机 MonitorRecord，验证天级聚合的 count、total_time、max_time、min_time、success、fail
    - **Validates: Requirements 10.2, 10.3, 10.4, 10.8**

  - [ ]* 2.4 编写 MemoryStorage 属性测试（续）
    - **Property 9: Tree 结构维护** — 生成随机记录，验证 GetTree 包含所有 project/class/method 组合
    - **Validates: Requirements 10.5**

  - [ ]* 2.5 编写 MemoryStorage 属性测试（续）
    - **Property 10: 访问明细数量上限** — 写入超过 200 条同 key 同分钟记录，验证 GetRecords 不超过 200
    - **Validates: Requirements 10.6**

  - [ ]* 2.6 编写 MemoryStorage 属性测试（续）
    - **Property 11: GetDayTrend 返回 1440 个数据点** — 随机日期，验证返回恰好 1440 个点且格式正确
    - **Validates: Requirements 10.10**

  - [ ]* 2.7 编写 MemoryStorage 属性测试（续）
    - **Property 12: Search 大小写不敏感** — 随机关键词，验证 Search(keyword) 和 Search(toUpper(keyword)) 结果一致
    - **Validates: Requirements 10.12**

- [x] 3. 检查点 - 确保存储层测试通过
  - 确保所有测试通过，如有问题请询问用户。

- [x] 4. MonitorClient UDP 发送核心
  - [x] 4.1 实现 MonitorClient
    - 在 `internal/client/monitor_client.go` 中实现 `MonitorClient` 结构体
    - 使用 golang-sdk 的 `serializer.JsonSerializer` 序列化 MonitorRecord 为 JSON
    - 实现 Report 方法：采样率过滤、构造 MonitorRecord、追加到 buffer
    - 实现 buffer 满时自动 Flush：用 `\n` 拼接多条 JSON 后通过 UDP 发送
    - 实现 Flush 方法：立即发送 buffer 中所有数据
    - 实现 Close 方法：自动 Flush 后关闭 UDP 连接
    - UDP 发送失败静默忽略
    - 使用 `sync.Mutex` 保护 buffer 并发安全
    - MonitorRecord 包含 project、class、method、uri、status、duration、timestamp 字段
    - params 非空时包含 params 字段，response 非空时包含 response 字段
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10, 1.11, 1.12, 1.13_

  - [ ]* 4.2 编写 MonitorClient 属性测试
    - **Property 1: MonitorRecord 序列化 round-trip** — 生成随机 MonitorRecord 列表，验证序列化/反序列化 round-trip
    - **Validates: Requirements 1.2, 1.5**

  - [ ]* 4.3 编写 MonitorClient 属性测试（续）
    - **Property 2: Buffer 行为不变量** — 生成随机 buffer_size 和 Report 次数（M < N），验证 buffer 状态
    - **Validates: Requirements 1.3, 1.7**

  - [ ]* 4.4 编写 MonitorClient 属性测试（续）
    - **Property 3: Buffer 自动刷新** — 生成随机 buffer_size N，连续调用 N 次 Report，验证 buffer 被清空
    - **Validates: Requirements 1.4**

  - [ ]* 4.5 编写 MonitorClient 属性测试（续）
    - **Property 4: MonitorRecord 字段完整性** — 生成随机 Report 参数，验证记录字段完整性和 params/response 条件包含
    - **Validates: Requirements 1.10, 1.11, 1.12**

- [x] 5. MonitorMiddleware GoFrame HTTP 中间件
  - [x] 5.1 实现 MonitorMiddleware
    - 在 `internal/client/middleware.go` 中实现 `NewMonitorMiddleware` 函数
    - 返回 `ghttp.HandlerFunc`，可通过 `s.Use()` 注册
    - 记录请求开始时间，`r.Middleware.Next()` 后计算耗时（毫秒）
    - 从 GoFrame 请求对象提取 URI、HTTP 状态码
    - 从路由信息解析 class/method，降级时从 URI 路径提取（首字母大写 + Controller 后缀，默认 index）
    - 匹配 exclude 列表时跳过采集
    - enabled 为 false 时直接放行
    - 提取 `parseAction` 为可导出的辅助函数（供属性测试使用）
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7_

  - [ ]* 5.2 编写 Middleware 属性测试
    - **Property 5: URI 路径解析** — 生成随机 URI 路径，验证解析出的 class 以 Controller 结尾且 method 非空
    - **Validates: Requirements 2.5**

  - [ ]* 5.3 编写 Middleware 属性测试（续）
    - **Property 6: Exclude 路径匹配** — 生成随机 URI 和 exclude 列表，验证前缀匹配逻辑
    - **Validates: Requirements 2.6**

- [x] 6. 检查点 - 确保客户端组件测试通过
  - 确保所有测试通过，如有问题请询问用户。

- [x] 7. UdpReceiver 数据接收器
  - [x] 7.1 实现 UdpReceiver
    - 在 `internal/server/udp_receiver.go` 中实现 `UdpReceiver` 结构体
    - 使用 Go 标准库 `net` 包监听 UDP 端口
    - 接收数据后按 `\n` 分隔逐行解析 JSON 为 MonitorRecord
    - 解析失败或缺少必要字段的行跳过，不中断处理
    - 每个 UDP 包在独立 goroutine 中处理
    - 启动时输出日志显示监听端口
    - 实现 Start（阻塞，在 goroutine 中调用）和 Stop 方法
    - 使用 golang-sdk 的 `observability.Logger` 记录日志
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7_

  - [ ]* 7.2 编写 UdpReceiver 属性测试
    - **Property 7: UDP 批量数据解析容错** — 生成混合有效/无效 JSON 行的数据包，验证写入 storage 的记录数等于有效行数
    - **Validates: Requirements 8.2, 8.3, 8.4**

- [x] 8. MonitorLogger 日志持久化
  - [x] 8.1 实现 MonitorLogger
    - 在 `internal/server/monitor_logger.go` 中实现 `MonitorLogger` 结构体
    - 启动时生成唯一 sessionId（格式：`s_{timestamp}_{random6}`）
    - 实现 Start 方法：按配置间隔定时调用 Export
    - 实现 Export 方法：导出天级汇总文件（`{date}.json`）和分钟级明细文件（`{date}_minute.json`）
    - 使用 sessionId 分槽存储，每次只更新当前 session 的槽位
    - 天级汇总包含 dashboard、slow_ranking、count_ranking、tree
    - 分钟级明细包含每个接口的 periods 和 records
    - 实现 Stop 方法：触发最后一次导出
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6, 11.8, 11.13_

  - [x] 8.2 实现 MonitorLogger 读取方法
    - 实现 ReadDayLog：读取天级汇总，合并所有历史 session（排除当前 session）
    - 实现 ReadMinuteLog：读取分钟级明细，合并所有历史 session
    - 实现 ReadRecordsFromLog：从日志读取指定接口指定分钟的访问明细
    - 实现 GetAvailableDates：扫描日志目录获取可用日期列表
    - 合并逻辑与 PHP 版 MonitorLogger 一致（排行榜累加、periods 累加）
    - _Requirements: 11.7, 11.9, 11.10, 11.11, 11.12_

  - [ ]* 8.3 编写 MonitorLogger 属性测试
    - **Property 13: SessionId 唯一性** — 创建多个 MonitorLogger 实例，验证 sessionId 互不相同
    - **Validates: Requirements 11.1**

  - [ ]* 8.4 编写 MonitorLogger 属性测试（续）
    - **Property 14: Session 分槽存储隔离** — 两个不同 sessionId 导出后，验证日志文件包含两个独立槽位，排除某 session 后不含其数据
    - **Validates: Requirements 11.4, 11.7**

- [x] 9. 检查点 - 确保服务端核心组件测试通过
  - 确保所有测试通过，如有问题请询问用户。

- [x] 10. HttpController HTTP API 控制器
  - [x] 10.1 实现 HttpController 核心 API
    - 在 `internal/server/http_controller.go` 中实现 `HttpController` 结构体
    - 实现 RegisterRoutes 方法，注册所有路由到 GoFrame Server
    - 实现 dashboard 接口：合并内存实时数据与日志历史数据
    - 实现 tree 接口：合并内存 tree 与日志 tree
    - 实现 detail 接口：合并内存 periods 与日志 periods
    - 实现 rankingSlow 和 rankingCount 接口：合并排行榜，相同接口累加统计后重新排序
    - 实现 realtime、trend、search、dates、records 接口
    - 所有 API 返回统一 JSON 格式 `{"code": 0, "data": ...}`
    - dates 接口返回前后 7 天列表，标注是否有数据及来源
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 12.6, 12.7, 12.8, 12.9, 12.10, 12.11, 12.14, 12.15, 12.16_

  - [x] 10.2 实现登录验证和仪表盘页面
    - 实现 login 接口：验证密码，生成凭证 `md5(password + "_monitor_salt")`，通过 Set-Cookie 写入 monitor_token，有效期 30 天
    - 实现 check-login 接口：检查 cookie 中 monitor_token 是否匹配
    - 空密码时不进行验证，直接放行
    - 实现 index 路由：返回 `public/index.html` 内容，Content-Type 为 `text/html; charset=utf-8`
    - HTML 文件不存在时返回 404
    - _Requirements: 12.12, 12.13, 13.1, 13.2, 13.3, 13.4, 14.1, 14.2, 14.3, 14.4, 14.5, 14.6_

  - [ ]* 10.3 编写 HttpController 属性测试
    - **Property 15: 排行榜合并累加** — 生成随机排行榜列表，验证合并后相同接口 count 累加且按指定字段降序
    - **Validates: Requirements 12.15, 12.16**

  - [ ]* 10.4 编写 HttpController 属性测试（续）
    - **Property 16: 登录凭证生成与验证 round-trip** — 随机密码，验证 md5 凭证生成和验证一致性
    - **Validates: Requirements 14.3, 14.4, 14.5**

- [x] 11. 服务端 main 入口
  - [x] 11.1 实现服务端 main.go
    - 在 `cmd/server/main.go` 中实现服务端启动逻辑
    - 读取 config.yaml 配置（使用 golang-sdk 的 config 包或直接 YAML 解析）
    - 初始化 MemoryStorage、MonitorLogger、UdpReceiver、HttpController
    - 启动 UDP 接收器（goroutine）
    - 启动 MonitorLogger 定时导出（如果配置开启）
    - 启动 GoFrame HTTP 服务器
    - 监听 SIGINT/SIGTERM 信号，优雅退出：停止 UDP → 最后一次日志导出 → 关闭 HTTP
    - 复制 PHP 版 `public/index.html` 到 `examples/api-monitor/golang/public/index.html`
    - _Requirements: 6.7, 8.7, 12.1, 13.2_

- [x] 12. Simulator 模拟数据发送器
  - [x] 12.1 实现 Simulator main.go
    - 在 `cmd/simulator/main.go` 中实现模拟客户端
    - 预定义与 PHP 版 simulate.php 一致的 15 个模拟 API（class、method、uri、耗时范围）
    - 每轮随机选 1~5 个 API 模拟调用
    - 按加权概率生成状态码（200:85%, 201:5%, 400:3%, 401:2%, 404:2%, 500:3%）
    - 在耗时范围内随机生成 duration，附带随机 params 和 response
    - 每轮发送后随机等待 100ms~1000ms
    - 终端彩色输出数据摘要（成功绿色、失败红色）
    - 从 config.yaml 读取 UDP 目标地址、端口、项目名
    - 监听 SIGINT/SIGTERM 信号，优雅退出（Flush + Close）
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9_

- [x] 13. 检查点 - 确保所有组件编译通过
  - 确保所有测试通过，如有问题请询问用户。

- [x] 14. 跨平台启动脚本和文档
  - [x] 14.1 创建跨平台启动脚本
    - 创建 `examples/api-monitor/golang/run-all.sh`（Linux/macOS）
    - 创建 `examples/api-monitor/golang/run-all.bat`（Windows）
    - 先编译服务端和 Simulator 为可执行文件，再运行
    - 按顺序：编译并启动服务端 → 等待 3 秒 → 编译并启动 Simulator
    - run-all.sh 监听 SIGINT/SIGTERM 停止所有进程
    - run-all.bat 使用 `start` 命令在新窗口启动
    - 启动完成后输出仪表盘访问地址 http://localhost:8095
    - 不依赖 PHP 运行环境
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7_

  - [x] 14.2 创建 README.md 文档
    - 在 `examples/api-monitor/golang/README.md` 中用中文编写
    - 包含功能简介、架构图、目录结构、快速开始、配置说明
    - 说明与 PHP 版功能对标且 API 兼容
    - 提供 GoFrame 应用接入 MonitorMiddleware 的代码示例
    - 说明一键脚本和手动启动方式
    - 包含服务端配置说明（存储驱动、日志持久化、端口、密码）
    - 包含完整 API 接口列表及参数说明
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6_

- [x] 15. 配置属性测试
  - [ ]* 15.1 编写配置 round-trip 属性测试
    - **Property 17: 配置 round-trip** — 生成随机配置值，验证 YAML 序列化/反序列化 round-trip
    - **Validates: Requirements 4.2, 4.3, 4.4**

- [x] 16. 最终检查点 - 确保所有测试通过
  - 确保所有测试通过，如有问题请询问用户。

## Notes

- 标记 `*` 的子任务为可选任务，可跳过以加速 MVP 开发
- 每个任务引用了具体的需求编号，确保需求可追溯
- 检查点任务用于增量验证，确保每个阶段的代码质量
- 属性测试验证设计文档中定义的 17 个正确性属性
- 单元测试验证具体的边界条件和错误处理
- 由于 Kiro 终端无法直接运行 go 命令，编译和测试需要在虚拟机中执行
