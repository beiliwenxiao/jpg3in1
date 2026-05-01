package server

import (
	"fmt"
	"math"
	"sort"
	"strings"
	"sync"
	"time"
)

// ============================================================================
// MemoryStorage 内存存储引擎
// ============================================================================

// MemoryStorage 内存存储引擎
// 使用进程内存存储监控数据，适合单实例模式或开发测试。
// 优点：无外部依赖，开箱即用。
// 缺点：进程重启数据丢失。
type MemoryStorage struct {
	mu                  sync.RWMutex
	minuteStats         map[string]map[string]*AggregationStats // key -> minute -> stats
	hourStats           map[string]map[string]*AggregationStats // key -> hour -> stats
	dayStats            map[string]map[string]*AggregationStats // key -> day -> stats
	tree                map[string]map[string]map[string]string // project -> class -> method -> uri
	records             map[string]map[string][]RecordItem      // key -> minute -> records
	maxRecordsPerMinute int
}

// NewMemoryStorage 创建内存存储引擎实例
func NewMemoryStorage() *MemoryStorage {
	return &MemoryStorage{
		minuteStats:         make(map[string]map[string]*AggregationStats),
		hourStats:           make(map[string]map[string]*AggregationStats),
		dayStats:            make(map[string]map[string]*AggregationStats),
		tree:                make(map[string]map[string]map[string]string),
		records:             make(map[string]map[string][]RecordItem),
		maxRecordsPerMinute: 200,
	}
}

// Record 写入一条监控记录
// 将 MonitorRecord 写入存储，自动按分钟/小时/天三级聚合统计
func (m *MemoryStorage) Record(data MonitorRecord) {
	m.mu.Lock()
	defer m.mu.Unlock()

	project := data.Project
	if project == "" {
		project = "default"
	}
	class := data.Class
	if class == "" {
		class = "Unknown"
	}
	method := data.Method
	if method == "" {
		method = "unknown"
	}
	uri := data.URI
	if uri == "" {
		uri = "/"
	}
	duration := data.Duration
	status := data.Status
	if status == 0 {
		status = 200
	}
	timestamp := data.Timestamp
	if timestamp == 0 {
		timestamp = time.Now().Unix()
	}

	// 判断请求是否成功：HTTP 状态码 200~399 为成功，400+ 为失败
	success := status >= 200 && status < 400

	// 构建复合键：{project}|{class}|{method}|{uri}
	key := fmt.Sprintf("%s|%s|%s|%s", project, class, method, uri)

	// 更新树形结构：project → class → method → uri
	if m.tree[project] == nil {
		m.tree[project] = make(map[string]map[string]string)
	}
	if m.tree[project][class] == nil {
		m.tree[project][class] = make(map[string]string)
	}
	m.tree[project][class][method] = uri

	// 计算时间键
	t := time.Unix(timestamp, 0)
	minuteKey := t.Format("2006-01-02 15:04")
	hourKey := t.Format("2006-01-02 15")
	dayKey := t.Format("2006-01-02")

	// 按分钟/小时/天三级聚合统计
	m.aggregate(m.minuteStats, key, minuteKey, duration, success)
	m.aggregate(m.hourStats, key, hourKey, duration, success)
	m.aggregate(m.dayStats, key, dayKey, duration, success)

	// 保存访问明细
	if m.records[key] == nil {
		m.records[key] = make(map[string][]RecordItem)
	}
	if len(m.records[key][minuteKey]) < m.maxRecordsPerMinute {
		m.records[key][minuteKey] = append(m.records[key][minuteKey], RecordItem{
			Time:     t.Format("2006-01-02 15:04:05"),
			Duration: duration,
			Status:   status,
			Params:   data.Params,
			Response: data.Response,
		})
	}

	// 清理超过 2 小时的明细记录
	cutoff := time.Unix(timestamp-7200, 0).Format("2006-01-02 15:04")
	for k, minutes := range m.records {
		for minute := range minutes {
			if minute < cutoff {
				delete(minutes, minute)
			}
		}
		if len(m.records[k]) == 0 {
			delete(m.records, k)
		}
	}
}

// aggregate 聚合统计辅助方法
// 更新指定存储层（分钟/小时/天）中指定 key 和时间段的统计数据
func (m *MemoryStorage) aggregate(store map[string]map[string]*AggregationStats, key, period string, duration float64, success bool) {
	if store[key] == nil {
		store[key] = make(map[string]*AggregationStats)
	}
	if store[key][period] == nil {
		store[key][period] = &AggregationStats{MinTime: math.MaxFloat64}
	}
	s := store[key][period]
	s.Count++
	s.TotalTime += duration
	if duration > s.MaxTime {
		s.MaxTime = duration
	}
	if duration < s.MinTime {
		s.MinTime = duration
	}
	if success {
		s.Success++
	} else {
		s.Fail++
	}
}

// ============================================================================
// 查询方法
// ============================================================================

// GetTree 获取树形菜单结构
// 返回深拷贝以避免数据竞争
func (m *MemoryStorage) GetTree() map[string]map[string]map[string]string {
	m.mu.RLock()
	defer m.mu.RUnlock()

	result := make(map[string]map[string]map[string]string, len(m.tree))
	for p, classes := range m.tree {
		result[p] = make(map[string]map[string]string, len(classes))
		for c, methods := range classes {
			result[p][c] = make(map[string]string, len(methods))
			for method, uri := range methods {
				result[p][c][method] = uri
			}
		}
	}
	return result
}

// GetDashboard 获取仪表盘概览数据
// 汇总指定日期所有接口的 count/success/fail/total_time，计算 success_rate 和 avg_time
func (m *MemoryStorage) GetDashboard(date string) DashboardData {
	m.mu.RLock()
	defer m.mu.RUnlock()

	if date == "" {
		date = time.Now().Format("2006-01-02")
	}

	totalCount := 0
	totalSuccess := 0
	totalFail := 0
	totalTime := 0.0

	for _, periods := range m.dayStats {
		if s, ok := periods[date]; ok {
			totalCount += s.Count
			totalSuccess += s.Success
			totalFail += s.Fail
			totalTime += s.TotalTime
		}
	}

	successRate := 0.0
	avgTime := 0.0
	if totalCount > 0 {
		successRate = math.Round(float64(totalSuccess)/float64(totalCount)*10000) / 100
		avgTime = math.Round(totalTime/float64(totalCount)*100) / 100
	}

	return DashboardData{
		Date:        date,
		TotalCount:  totalCount,
		Success:     totalSuccess,
		Fail:        totalFail,
		SuccessRate: successRate,
		AvgTime:     avgTime,
	}
}

// GetDetail 获取接口详情数据
// 遍历 tree 找到匹配的接口，根据 granularity 选择对应的统计存储，过滤指定日期的数据
func (m *MemoryStorage) GetDetail(project, class, method, date, granularity string) []DetailData {
	m.mu.RLock()
	defer m.mu.RUnlock()

	if date == "" {
		date = time.Now().Format("2006-01-02")
	}

	var result []DetailData

	for p, classes := range m.tree {
		if project != "" && p != project {
			continue
		}
		for c, methods := range classes {
			if class != "" && c != class {
				continue
			}
			for mth, uri := range methods {
				if method != "" && mth != method {
					continue
				}
				key := fmt.Sprintf("%s|%s|%s|%s", p, c, mth, uri)

				// 根据粒度选择统计存储
				var store map[string]map[string]*AggregationStats
				switch granularity {
				case "hour":
					store = m.hourStats
				case "day":
					store = m.dayStats
				default:
					store = m.minuteStats
				}

				periods := store[key]
				filtered := make(map[string]*AggregationStats)
				if periods != nil {
					// 收集匹配日期的 period key 并排序
					var keys []string
					for period := range periods {
						if strings.HasPrefix(period, date) {
							keys = append(keys, period)
						}
					}
					sort.Strings(keys)

					for _, period := range keys {
						s := periods[period]
						// 创建副本，添加 avg_time 和 success_rate
						copy := &AggregationStats{
							Count:     s.Count,
							Success:   s.Success,
							Fail:      s.Fail,
							TotalTime: s.TotalTime,
							MaxTime:   s.MaxTime,
							MinTime:   s.MinTime,
						}
						if copy.MinTime == math.MaxFloat64 {
							copy.MinTime = 0
						}
						filtered[period] = copy
					}
				}

				result = append(result, DetailData{
					Project: p,
					Class:   c,
					Method:  mth,
					URI:     uri,
					Periods: filtered,
				})
			}
		}
	}
	return result
}

// GetSlowRanking 获取慢速接口排行榜
// 遍历 dayStats，计算 avg_time，按 avg_time 降序排列，返回前 limit 条
func (m *MemoryStorage) GetSlowRanking(date string, limit int) []SlowRankingItem {
	m.mu.RLock()
	defer m.mu.RUnlock()

	if date == "" {
		date = time.Now().Format("2006-01-02")
	}

	var ranking []SlowRankingItem
	for key, periods := range m.dayStats {
		s, ok := periods[date]
		if !ok {
			continue
		}
		parts := strings.SplitN(key, "|", 4)
		avgTime := 0.0
		if s.Count > 0 {
			avgTime = math.Round(s.TotalTime/float64(s.Count)*100) / 100
		}
		ranking = append(ranking, SlowRankingItem{
			Project: parts[0],
			Class:   parts[1],
			Method:  parts[2],
			URI:     parts[3],
			AvgTime: avgTime,
			MaxTime: math.Round(s.MaxTime*100) / 100,
			Count:   s.Count,
		})
	}

	sort.Slice(ranking, func(i, j int) bool {
		return ranking[i].AvgTime > ranking[j].AvgTime
	})

	if limit > 0 && len(ranking) > limit {
		ranking = ranking[:limit]
	}
	return ranking
}

// GetCountRanking 获取访问次数排行榜
// 遍历 dayStats，计算 success_rate，按 count 降序排列，返回前 limit 条
func (m *MemoryStorage) GetCountRanking(date string, limit int) []CountRankingItem {
	m.mu.RLock()
	defer m.mu.RUnlock()

	if date == "" {
		date = time.Now().Format("2006-01-02")
	}

	var ranking []CountRankingItem
	for key, periods := range m.dayStats {
		s, ok := periods[date]
		if !ok {
			continue
		}
		parts := strings.SplitN(key, "|", 4)
		successRate := 0.0
		if s.Count > 0 {
			successRate = math.Round(float64(s.Success)/float64(s.Count)*10000) / 100
		}
		ranking = append(ranking, CountRankingItem{
			Project:     parts[0],
			Class:       parts[1],
			Method:      parts[2],
			URI:         parts[3],
			Count:       s.Count,
			Success:     s.Success,
			Fail:        s.Fail,
			SuccessRate: successRate,
		})
	}

	sort.Slice(ranking, func(i, j int) bool {
		return ranking[i].Count > ranking[j].Count
	})

	if limit > 0 && len(ranking) > limit {
		ranking = ranking[:limit]
	}
	return ranking
}

// GetRealtime 获取实时访问量数据
// 获取当前时间，向前回溯 10 分钟，汇总每分钟所有接口的访问量
func (m *MemoryStorage) GetRealtime() []TimeSeriesItem {
	m.mu.RLock()
	defer m.mu.RUnlock()

	now := time.Now()
	result := make([]TimeSeriesItem, 0, 10)

	for i := 9; i >= 0; i-- {
		minute := now.Add(-time.Duration(i) * time.Minute).Format("2006-01-02 15:04")
		count := 0
		success := 0
		fail := 0
		for _, periods := range m.minuteStats {
			if s, ok := periods[minute]; ok {
				count += s.Count
				success += s.Success
				fail += s.Fail
			}
		}
		result = append(result, TimeSeriesItem{
			Time:    minute,
			Count:   count,
			Success: success,
			Fail:    fail,
		})
	}
	return result
}

// Search 搜索接口
// 对 key（project|class|method|uri）进行大小写不敏感的关键词匹配
func (m *MemoryStorage) Search(keyword, date string) []SearchItem {
	m.mu.RLock()
	defer m.mu.RUnlock()

	if date == "" {
		date = time.Now().Format("2006-01-02")
	}

	lowerKeyword := strings.ToLower(keyword)
	var results []SearchItem

	for key, periods := range m.dayStats {
		if !strings.Contains(strings.ToLower(key), lowerKeyword) {
			continue
		}
		s, ok := periods[date]
		if !ok {
			continue
		}
		parts := strings.SplitN(key, "|", 4)
		successRate := 0.0
		avgTime := 0.0
		if s.Count > 0 {
			successRate = math.Round(float64(s.Success)/float64(s.Count)*10000) / 100
			avgTime = math.Round(s.TotalTime/float64(s.Count)*100) / 100
		}
		results = append(results, SearchItem{
			Project:     parts[0],
			Class:       parts[1],
			Method:      parts[2],
			URI:         parts[3],
			Count:       s.Count,
			SuccessRate: successRate,
			AvgTime:     avgTime,
		})
	}
	return results
}

// GetDayTrend 获取全天分钟级访问趋势
// 生成完整的 1440 个分钟数据点（24*60），无数据的分钟填充零值
func (m *MemoryStorage) GetDayTrend(date string) []TimeSeriesItem {
	m.mu.RLock()
	defer m.mu.RUnlock()

	if date == "" {
		date = time.Now().Format("2006-01-02")
	}

	result := make([]TimeSeriesItem, 0, 1440)
	for h := 0; h < 24; h++ {
		for min := 0; min < 60; min++ {
			minute := fmt.Sprintf("%s %02d:%02d", date, h, min)
			count := 0
			success := 0
			fail := 0
			for _, periods := range m.minuteStats {
				if s, ok := periods[minute]; ok {
					count += s.Count
					success += s.Success
					fail += s.Fail
				}
			}
			result = append(result, TimeSeriesItem{
				Time:    minute,
				Count:   count,
				Success: success,
				Fail:    fail,
			})
		}
	}
	return result
}

// HasData 判断指定日期是否有监控数据
func (m *MemoryStorage) HasData(date string) bool {
	m.mu.RLock()
	defer m.mu.RUnlock()

	for _, periods := range m.dayStats {
		if _, ok := periods[date]; ok {
			return true
		}
	}
	return false
}

// GetRecords 获取访问明细列表
// 通过 tree 查找 uri，构建 key，返回指定分钟的明细记录（限制数量）
func (m *MemoryStorage) GetRecords(project, class, method, minute string, limit int) []RecordItem {
	m.mu.RLock()
	defer m.mu.RUnlock()

	// 从 tree 中查找 uri
	classes, ok := m.tree[project]
	if !ok {
		return nil
	}
	methods, ok := classes[class]
	if !ok {
		return nil
	}
	uri, ok := methods[method]
	if !ok {
		return nil
	}

	key := fmt.Sprintf("%s|%s|%s|%s", project, class, method, uri)
	minutes, ok := m.records[key]
	if !ok {
		return nil
	}
	records, ok := minutes[minute]
	if !ok {
		return nil
	}

	if limit > 0 && len(records) > limit {
		records = records[:limit]
	}

	// 返回副本以避免数据竞争
	result := make([]RecordItem, len(records))
	copy(result, records)
	return result
}
