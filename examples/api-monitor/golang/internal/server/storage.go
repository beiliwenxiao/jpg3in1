package server

// ============================================================================
// 数据模型定义
// ============================================================================

// MonitorRecord 单条监控数据记录
// 包含接口调用的完整信息：项目、控制器、方法、URI、状态码、耗时、时间戳等
type MonitorRecord struct {
	Project   string      `json:"project"`            // 项目名称
	Class     string      `json:"class"`              // 控制器/类名
	Method    string      `json:"method"`             // 方法名
	URI       string      `json:"uri"`                // 请求 URI
	Status    int         `json:"status"`             // HTTP 状态码
	Duration  float64     `json:"duration"`           // 请求耗时（毫秒）
	Timestamp int64       `json:"timestamp"`          // Unix 时间戳
	Params    interface{} `json:"params,omitempty"`    // 请求参数（可选）
	Response  interface{} `json:"response,omitempty"`  // 响应数据（可选）
}

// AggregationStats 聚合统计字段集合
// 用于分钟/小时/天三级粒度的统计数据
type AggregationStats struct {
	Count     int     `json:"count"`      // 请求总数
	Success   int     `json:"success"`    // 成功次数（状态码 200~399）
	Fail      int     `json:"fail"`       // 失败次数（状态码 >= 400）
	TotalTime float64 `json:"total_time"` // 总耗时（毫秒）
	MaxTime   float64 `json:"max_time"`   // 最大耗时（毫秒）
	MinTime   float64 `json:"min_time"`   // 最小耗时（毫秒）
}

// DashboardData 仪表盘概览数据
// 展示指定日期的整体访问统计
type DashboardData struct {
	Date        string  `json:"date"`         // 日期（格式：YYYY-MM-DD）
	TotalCount  int     `json:"total_count"`  // 请求总数
	Success     int     `json:"success"`      // 成功次数
	Fail        int     `json:"fail"`         // 失败次数
	SuccessRate float64 `json:"success_rate"` // 成功率（百分比）
	AvgTime     float64 `json:"avg_time"`     // 平均耗时（毫秒）
}

// DetailData 接口详情数据
// 包含指定接口在各时间段的聚合统计
type DetailData struct {
	Project string                       `json:"project"` // 项目名称
	Class   string                       `json:"class"`   // 控制器/类名
	Method  string                       `json:"method"`  // 方法名
	URI     string                       `json:"uri"`     // 请求 URI
	Periods map[string]*AggregationStats `json:"periods"` // 各时间段的聚合统计
}

// SlowRankingItem 慢速接口排行项
// 按平均耗时降序排列的接口统计
type SlowRankingItem struct {
	Project string  `json:"project"`  // 项目名称
	Class   string  `json:"class"`    // 控制器/类名
	Method  string  `json:"method"`   // 方法名
	URI     string  `json:"uri"`      // 请求 URI
	AvgTime float64 `json:"avg_time"` // 平均耗时（毫秒）
	MaxTime float64 `json:"max_time"` // 最大耗时（毫秒）
	Count   int     `json:"count"`    // 请求总数
}

// CountRankingItem 访问次数排行项
// 按访问次数降序排列的接口统计
type CountRankingItem struct {
	Project     string  `json:"project"`      // 项目名称
	Class       string  `json:"class"`        // 控制器/类名
	Method      string  `json:"method"`       // 方法名
	URI         string  `json:"uri"`          // 请求 URI
	Count       int     `json:"count"`        // 请求总数
	Success     int     `json:"success"`      // 成功次数
	Fail        int     `json:"fail"`         // 失败次数
	SuccessRate float64 `json:"success_rate"` // 成功率（百分比）
}

// TimeSeriesItem 时序数据点
// 用于实时访问量（最近 10 分钟）和全天趋势（1440 个数据点）
type TimeSeriesItem struct {
	Time    string `json:"time"`    // 时间（格式：YYYY-MM-DD HH:MM）
	Count   int    `json:"count"`   // 请求总数
	Success int    `json:"success"` // 成功次数
	Fail    int    `json:"fail"`    // 失败次数
}

// RecordItem 访问明细记录
// 单次请求的详细信息，用于查看某接口某分钟的具体调用
type RecordItem struct {
	Time     string      `json:"time"`     // 请求时间（格式：YYYY-MM-DD HH:MM:SS）
	Duration float64     `json:"duration"` // 请求耗时（毫秒）
	Status   int         `json:"status"`   // HTTP 状态码
	Params   interface{} `json:"params"`   // 请求参数
	Response interface{} `json:"response"` // 响应数据
}

// SearchItem 搜索结果项
// 接口搜索结果，包含基本统计信息
type SearchItem struct {
	Project     string  `json:"project"`      // 项目名称
	Class       string  `json:"class"`        // 控制器/类名
	Method      string  `json:"method"`       // 方法名
	URI         string  `json:"uri"`          // 请求 URI
	Count       int     `json:"count"`        // 请求总数
	SuccessRate float64 `json:"success_rate"` // 成功率（百分比）
	AvgTime     float64 `json:"avg_time"`     // 平均耗时（毫秒）
}

// ============================================================================
// 存储接口定义
// ============================================================================

// StorageInterface 监控数据存储接口
// 定义所有数据读写操作的抽象层，支持内存存储和未来扩展（如 Redis）
type StorageInterface interface {
	// Record 写入一条监控记录
	// 将 MonitorRecord 写入存储，自动按分钟/小时/天三级聚合统计
	Record(data MonitorRecord)

	// GetTree 获取树形菜单结构
	// 返回 project → class → method → uri 的三级树形结构，用于前端左侧导航
	GetTree() map[string]map[string]map[string]string

	// GetDashboard 获取仪表盘概览数据
	// 返回指定日期的整体访问统计，包含总数、成功/失败数、成功率、平均耗时
	GetDashboard(date string) DashboardData

	// GetDetail 获取接口详情数据
	// 返回指定接口在指定日期按指定粒度（minute/hour/day）的聚合统计时序数据
	GetDetail(project, class, method, date, granularity string) []DetailData

	// GetSlowRanking 获取慢速接口排行榜
	// 返回指定日期按平均耗时降序排列的接口列表，limit 限制返回数量
	GetSlowRanking(date string, limit int) []SlowRankingItem

	// GetCountRanking 获取访问次数排行榜
	// 返回指定日期按访问次数降序排列的接口列表，limit 限制返回数量
	GetCountRanking(date string, limit int) []CountRankingItem

	// GetRealtime 获取实时访问量数据
	// 返回最近 10 分钟每分钟的访问量统计，按时间正序排列
	GetRealtime() []TimeSeriesItem

	// Search 搜索接口
	// 对接口的 project、class、method、uri 进行大小写不敏感的关键词匹配
	Search(keyword, date string) []SearchItem

	// GetDayTrend 获取全天分钟级访问趋势
	// 返回指定日期完整的 1440 个分钟级数据点，无数据的分钟填充零值
	GetDayTrend(date string) []TimeSeriesItem

	// HasData 判断指定日期是否有监控数据
	HasData(date string) bool

	// GetRecords 获取访问明细列表
	// 返回指定接口在指定分钟的访问明细记录，limit 限制返回数量
	GetRecords(project, class, method, minute string, limit int) []RecordItem
}
