package server

import (
	"encoding/json"
	"fmt"
	"math"
	"math/rand"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"time"

	"github.com/framework/golang-sdk/observability"
)

// MonitorLogger 日志持久化组件
// 使用 sessionId 机制避免重启后数据重复或丢失：
//   - 每次进程启动生成唯一 sessionId
//   - 日志文件按 session 分槽存储，每个 session 的数据独立
//   - 导出时只更新当前 session 的槽位，不影响其他 session
//   - 查询时将所有 session 的数据累加返回
type MonitorLogger struct {
	storage   StorageInterface
	logDir    string
	interval  int
	sessionId string
	logger    observability.Logger
	ticker    *time.Ticker
	stopCh    chan struct{}
}

// NewMonitorLogger 创建日志持久化组件
func NewMonitorLogger(storage StorageInterface, logDir string, interval int, logger observability.Logger) *MonitorLogger {
	// 生成唯一 sessionId（格式：s_{timestamp}_{random6hex}）
	randomPart := fmt.Sprintf("%06x", rand.Intn(0xFFFFFF))
	sessionId := fmt.Sprintf("s_%d_%s", time.Now().Unix(), randomPart)

	return &MonitorLogger{
		storage:   storage,
		logDir:    logDir,
		interval:  interval,
		sessionId: sessionId,
		logger:    logger,
		stopCh:    make(chan struct{}),
	}
}

// GetSessionId 获取当前 sessionId
func (l *MonitorLogger) GetSessionId() string {
	return l.sessionId
}

// Start 启动定时导出
func (l *MonitorLogger) Start() {
	// 确保日志目录存在
	_ = os.MkdirAll(l.logDir, 0755)

	l.ticker = time.NewTicker(time.Duration(l.interval) * time.Second)
	fmt.Printf("[监控] 日志持久化启动 (session: %s)，间隔 %d 秒\n", l.sessionId, l.interval)

	go func() {
		for {
			select {
			case <-l.ticker.C:
				l.Export()
			case <-l.stopCh:
				return
			}
		}
	}()
}

// Stop 停止定时导出并执行最后一次导出
func (l *MonitorLogger) Stop() {
	if l.ticker != nil {
		l.ticker.Stop()
	}
	close(l.stopCh)
	l.Export() // 最后一次导出
}

// Export 执行一次数据导出
func (l *MonitorLogger) Export() {
	date := time.Now().Format("2006-01-02")
	l.exportDaySummary(date)
	l.exportMinuteDetail(date)
}

// exportDaySummary 导出天级汇总文件（{date}.json）
// 文件结构：{ "sessions": { "s_xxx": { exported_at, dashboard, slow_ranking, count_ranking, tree } } }
func (l *MonitorLogger) exportDaySummary(date string) {
	file := filepath.Join(l.logDir, date+".json")

	// 读取已有文件内容（保留其他 session 的数据）
	var logData map[string]interface{}
	if data, err := os.ReadFile(file); err == nil {
		_ = json.Unmarshal(data, &logData)
	}
	if logData == nil {
		logData = make(map[string]interface{})
	}

	// 确保 sessions 字段存在
	sessions, ok := logData["sessions"].(map[string]interface{})
	if !ok {
		sessions = make(map[string]interface{})
	}

	// 写入当前 session 的数据（只更新自己的槽位）
	sessions[l.sessionId] = map[string]interface{}{
		"exported_at":   time.Now().Format("2006-01-02 15:04:05"),
		"dashboard":     l.storage.GetDashboard(date),
		"slow_ranking":  l.storage.GetSlowRanking(date, 50),
		"count_ranking": l.storage.GetCountRanking(date, 50),
		"tree":          l.storage.GetTree(),
	}
	logData["sessions"] = sessions

	// 写入文件
	jsonData, err := json.MarshalIndent(logData, "", "  ")
	if err != nil {
		fmt.Printf("[监控日志] 序列化失败: %v\n", err)
		return
	}
	if err := os.WriteFile(file, jsonData, 0644); err != nil {
		fmt.Printf("[监控日志] 写入失败: %v\n", err)
	}
}

// exportMinuteDetail 导出分钟级明细文件（{date}_minute.json）
// 文件结构：{ "sessions": { "s_xxx": { exported_at, details: [...] } } }
func (l *MonitorLogger) exportMinuteDetail(date string) {
	file := filepath.Join(l.logDir, date+"_minute.json")

	// 读取已有文件内容（保留其他 session 的数据）
	var logData map[string]interface{}
	if data, err := os.ReadFile(file); err == nil {
		_ = json.Unmarshal(data, &logData)
	}
	if logData == nil {
		logData = make(map[string]interface{})
	}

	// 确保 sessions 字段存在
	sessions, ok := logData["sessions"].(map[string]interface{})
	if !ok {
		sessions = make(map[string]interface{})
	}

	// 收集所有接口的分钟级明细
	tree := l.storage.GetTree()
	var details []interface{}

	for project, classes := range tree {
		for class, methods := range classes {
			for method, uri := range methods {
				detail := l.storage.GetDetail(project, class, method, date, "minute")
				if len(detail) == 0 || len(detail[0].Periods) == 0 {
					continue
				}

				item := detail[0]
				records := make(map[string][]RecordItem)
				for minute := range item.Periods {
					recs := l.storage.GetRecords(project, class, method, minute, 200)
					if len(recs) > 0 {
						records[minute] = recs
					}
				}

				details = append(details, map[string]interface{}{
					"project": item.Project,
					"class":   item.Class,
					"method":  item.Method,
					"uri":     uri,
					"periods": item.Periods,
					"records": records,
				})
			}
		}
	}

	sessions[l.sessionId] = map[string]interface{}{
		"exported_at": time.Now().Format("2006-01-02 15:04:05"),
		"details":     details,
	}
	logData["sessions"] = sessions

	// 写入文件
	jsonData, err := json.MarshalIndent(logData, "", "  ")
	if err != nil {
		fmt.Printf("[监控日志] 分钟明细序列化失败: %v\n", err)
		return
	}
	if err := os.WriteFile(file, jsonData, 0644); err != nil {
		fmt.Printf("[监控日志] 分钟明细写入失败: %v\n", err)
	}
}

// ============================================================================
// 数据结构定义（读取合并后的数据）
// ============================================================================

// DayLogData 天级汇总日志数据（合并后）
type DayLogData struct {
	Dashboard    DashboardData                          `json:"dashboard"`
	SlowRanking  []SlowRankingItem                      `json:"slow_ranking"`
	CountRanking []CountRankingItem                     `json:"count_ranking"`
	Tree         map[string]map[string]map[string]string `json:"tree"`
}

// MinuteLogData 分钟级明细日志数据（合并后）
type MinuteLogData struct {
	Details []MinuteLogDetail `json:"details"`
}

// MinuteLogDetail 分钟级明细中的单个接口数据
type MinuteLogDetail struct {
	Project string                       `json:"project"`
	Class   string                       `json:"class"`
	Method  string                       `json:"method"`
	URI     string                       `json:"uri"`
	Periods map[string]*AggregationStats `json:"periods"`
	Records map[string][]RecordItem      `json:"records"`
}

// ============================================================================
// 读取方法（合并所有历史 session，排除当前 session）
// ============================================================================

// ReadDayLog 读取天级汇总日志（合并所有历史 session，排除当前 session）
func (l *MonitorLogger) ReadDayLog(date string) *DayLogData {
	file := filepath.Join(l.logDir, date+".json")
	data, err := os.ReadFile(file)
	if err != nil {
		return nil
	}

	var logData map[string]interface{}
	if err := json.Unmarshal(data, &logData); err != nil {
		return nil
	}

	// 旧格式兼容：如果直接有 dashboard 字段，说明是旧格式
	if _, ok := logData["dashboard"]; ok {
		return l.parseLegacyDayLog(logData)
	}

	// 新格式：合并所有 session（排除当前 sessionId）
	sessionsRaw, ok := logData["sessions"].(map[string]interface{})
	if !ok {
		return nil
	}

	// 排除当前 session
	sessions := make(map[string]interface{})
	for k, v := range sessionsRaw {
		if k != l.sessionId {
			sessions[k] = v
		}
	}

	if len(sessions) == 0 {
		return nil
	}

	return l.mergeDaySessions(sessions)
}

// parseLegacyDayLog 解析旧格式的天级日志
func (l *MonitorLogger) parseLegacyDayLog(logData map[string]interface{}) *DayLogData {
	result := &DayLogData{
		Tree: make(map[string]map[string]map[string]string),
	}
	result.Dashboard = parseDashboardData(logData["dashboard"])
	result.SlowRanking = parseSlowRankingList(logData["slow_ranking"])
	result.CountRanking = parseCountRankingList(logData["count_ranking"])
	result.Tree = parseTree(logData["tree"])
	return result
}

// mergeDaySessions 合并所有 session 的天级汇总数据
func (l *MonitorLogger) mergeDaySessions(sessions map[string]interface{}) *DayLogData {
	var totalCount, totalSuccess, totalFail int
	var totalTime float64
	tree := make(map[string]map[string]map[string]string)
	slowMap := make(map[string]*SlowRankingItem)
	countMap := make(map[string]*CountRankingItem)

	for _, sessionRaw := range sessions {
		session, ok := sessionRaw.(map[string]interface{})
		if !ok {
			continue
		}

		// 累加 dashboard
		d := parseDashboardData(session["dashboard"])
		c := d.TotalCount
		totalCount += c
		totalSuccess += d.Success
		totalFail += d.Fail
		totalTime += d.AvgTime * float64(c)

		// 合并 tree
		sessionTree := parseTree(session["tree"])
		for proj, classes := range sessionTree {
			if tree[proj] == nil {
				tree[proj] = make(map[string]map[string]string)
			}
			for cls, methods := range classes {
				if tree[proj][cls] == nil {
					tree[proj][cls] = make(map[string]string)
				}
				for mtd, uri := range methods {
					tree[proj][cls][mtd] = uri
				}
			}
		}

		// 合并排行榜
		accumulateSlowRanking(slowMap, parseSlowRankingList(session["slow_ranking"]))
		accumulateCountRanking(countMap, parseCountRankingList(session["count_ranking"]))
	}

	// 构建 slow_ranking 排序结果
	slowList := make([]SlowRankingItem, 0, len(slowMap))
	for _, item := range slowMap {
		slowList = append(slowList, *item)
	}
	sort.Slice(slowList, func(i, j int) bool {
		return slowList[i].AvgTime > slowList[j].AvgTime
	})
	if len(slowList) > 50 {
		slowList = slowList[:50]
	}

	// 构建 count_ranking 排序结果
	countList := make([]CountRankingItem, 0, len(countMap))
	for _, item := range countMap {
		countList = append(countList, *item)
	}
	sort.Slice(countList, func(i, j int) bool {
		return countList[i].Count > countList[j].Count
	})
	if len(countList) > 50 {
		countList = countList[:50]
	}

	// 计算合并后的 dashboard
	var successRate, avgTime float64
	if totalCount > 0 {
		successRate = math.Round(float64(totalSuccess)/float64(totalCount)*10000) / 100
		avgTime = math.Round(totalTime/float64(totalCount)*100) / 100
	}

	return &DayLogData{
		Dashboard: DashboardData{
			Date:        "",
			TotalCount:  totalCount,
			Success:     totalSuccess,
			Fail:        totalFail,
			SuccessRate: successRate,
			AvgTime:     avgTime,
		},
		SlowRanking:  slowList,
		CountRanking: countList,
		Tree:         tree,
	}
}

// accumulateSlowRanking 累加慢速排行榜数据
func accumulateSlowRanking(m map[string]*SlowRankingItem, ranking []SlowRankingItem) {
	for _, item := range ranking {
		key := item.Project + "|" + item.Class + "|" + item.Method
		if existing, ok := m[key]; ok {
			oldTotal := existing.AvgTime * float64(existing.Count)
			newTotal := item.AvgTime * float64(item.Count)
			tc := existing.Count + item.Count
			existing.Count = tc
			if tc > 0 {
				existing.AvgTime = math.Round((oldTotal+newTotal)/float64(tc)*100) / 100
			}
			existing.MaxTime = math.Max(existing.MaxTime, item.MaxTime)
		} else {
			copy := item
			m[key] = &copy
		}
	}
}

// accumulateCountRanking 累加访问量排行榜数据
func accumulateCountRanking(m map[string]*CountRankingItem, ranking []CountRankingItem) {
	for _, item := range ranking {
		key := item.Project + "|" + item.Class + "|" + item.Method
		if existing, ok := m[key]; ok {
			existing.Count += item.Count
			existing.Success += item.Success
			existing.Fail += item.Fail
			if existing.Count > 0 {
				existing.SuccessRate = math.Round(float64(existing.Success)/float64(existing.Count)*10000) / 100
			}
		} else {
			copy := item
			m[key] = &copy
		}
	}
}

// ReadMinuteLog 读取分钟级明细日志（合并所有历史 session，排除当前 session）
func (l *MonitorLogger) ReadMinuteLog(date string) *MinuteLogData {
	file := filepath.Join(l.logDir, date+"_minute.json")
	data, err := os.ReadFile(file)
	if err != nil {
		return nil
	}

	var logData map[string]interface{}
	if err := json.Unmarshal(data, &logData); err != nil {
		return nil
	}

	// 旧格式兼容：如果直接有 details 字段
	if _, ok := logData["details"]; ok {
		return l.parseLegacyMinuteLog(logData)
	}

	// 新格式：合并所有 session（排除当前 sessionId）
	sessionsRaw, ok := logData["sessions"].(map[string]interface{})
	if !ok {
		return nil
	}

	sessions := make(map[string]interface{})
	for k, v := range sessionsRaw {
		if k != l.sessionId {
			sessions[k] = v
		}
	}

	if len(sessions) == 0 {
		return nil
	}

	return l.mergeMinuteSessions(sessions)
}

// parseLegacyMinuteLog 解析旧格式的分钟级日志
func (l *MonitorLogger) parseLegacyMinuteLog(logData map[string]interface{}) *MinuteLogData {
	details := parseMinuteDetailList(logData["details"])
	return &MinuteLogData{Details: details}
}

// mergeMinuteSessions 合并所有 session 的分钟级明细数据
func (l *MonitorLogger) mergeMinuteSessions(sessions map[string]interface{}) *MinuteLogData {
	// key(project|class|method) => MinuteLogDetail
	detailMap := make(map[string]*MinuteLogDetail)

	for _, sessionRaw := range sessions {
		session, ok := sessionRaw.(map[string]interface{})
		if !ok {
			continue
		}

		details := parseMinuteDetailList(session["details"])
		for _, detail := range details {
			key := detail.Project + "|" + detail.Class + "|" + detail.Method

			if existing, ok := detailMap[key]; ok {
				// 合并 periods
				for minute, stats := range detail.Periods {
					if existingStats, ok := existing.Periods[minute]; ok {
						tc := existingStats.Count + stats.Count
						totalTime := existingStats.TotalTime + stats.TotalTime
						totalSuccess := existingStats.Success + stats.Success
						totalFail := existingStats.Fail + stats.Fail
						existingStats.Count = tc
						existingStats.Success = totalSuccess
						existingStats.Fail = totalFail
						existingStats.TotalTime = totalTime
						existingStats.MaxTime = math.Max(existingStats.MaxTime, stats.MaxTime)
						existingStats.MinTime = math.Min(existingStats.MinTime, stats.MinTime)
					} else {
						statsCopy := *stats
						existing.Periods[minute] = &statsCopy
					}
				}

				// 合并 records（按 time 去重）
				for minute, recs := range detail.Records {
					if existingRecs, ok := existing.Records[minute]; ok {
						existingTimes := make(map[string]bool)
						for _, r := range existingRecs {
							existingTimes[r.Time] = true
						}
						for _, rec := range recs {
							if !existingTimes[rec.Time] {
								existing.Records[minute] = append(existing.Records[minute], rec)
							}
						}
					} else {
						existing.Records[minute] = recs
					}
				}
			} else {
				detailCopy := detail
				// 深拷贝 periods
				detailCopy.Periods = make(map[string]*AggregationStats)
				for k, v := range detail.Periods {
					statsCopy := *v
					detailCopy.Periods[k] = &statsCopy
				}
				// 深拷贝 records
				detailCopy.Records = make(map[string][]RecordItem)
				for k, v := range detail.Records {
					recsCopy := make([]RecordItem, len(v))
					copy(recsCopy, v)
					detailCopy.Records[k] = recsCopy
				}
				detailMap[key] = &detailCopy
			}
		}
	}

	// 对 periods 按 key 排序
	for _, detail := range detailMap {
		_ = detail // periods 是 map，Go 中 map 无需显式排序，遍历时自然无序
		// 但为了与 PHP 版一致，这里不做额外处理，JSON 序列化时 map 的 key 会自动排序
	}

	// 转为列表
	result := make([]MinuteLogDetail, 0, len(detailMap))
	for _, detail := range detailMap {
		result = append(result, *detail)
	}

	return &MinuteLogData{Details: result}
}

// ReadRecordsFromLog 从日志读取指定接口指定分钟的访问明细
func (l *MonitorLogger) ReadRecordsFromLog(date, project, class, method, minute string) []RecordItem {
	minuteLog := l.ReadMinuteLog(date)
	if minuteLog == nil {
		return nil
	}

	for _, detail := range minuteLog.Details {
		if detail.Project == project && detail.Class == class && detail.Method == method {
			if recs, ok := detail.Records[minute]; ok {
				return recs
			}
			return nil
		}
	}
	return nil
}

// GetAvailableDates 获取日志目录中的可用日期列表
func (l *MonitorLogger) GetAvailableDates() []string {
	entries, err := os.ReadDir(l.logDir)
	if err != nil {
		return nil
	}

	datePattern := regexp.MustCompile(`^(\d{4}-\d{2}-\d{2})\.json$`)
	var dates []string

	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}
		matches := datePattern.FindStringSubmatch(entry.Name())
		if len(matches) == 2 {
			dates = append(dates, matches[1])
		}
	}

	sort.Strings(dates)
	return dates
}

// ============================================================================
// JSON 解析辅助函数（从 map[string]interface{} 中安全提取类型化数据）
// ============================================================================

// parseDashboardData 从 interface{} 解析 DashboardData
func parseDashboardData(raw interface{}) DashboardData {
	d := DashboardData{}
	m, ok := raw.(map[string]interface{})
	if !ok {
		return d
	}
	d.Date, _ = m["date"].(string)
	d.TotalCount = toInt(m["total_count"])
	d.Success = toInt(m["success"])
	d.Fail = toInt(m["fail"])
	d.SuccessRate = toFloat64(m["success_rate"])
	d.AvgTime = toFloat64(m["avg_time"])
	return d
}

// parseSlowRankingList 从 interface{} 解析 []SlowRankingItem
func parseSlowRankingList(raw interface{}) []SlowRankingItem {
	arr, ok := raw.([]interface{})
	if !ok {
		return nil
	}
	result := make([]SlowRankingItem, 0, len(arr))
	for _, item := range arr {
		m, ok := item.(map[string]interface{})
		if !ok {
			continue
		}
		result = append(result, SlowRankingItem{
			Project: toString(m["project"]),
			Class:   toString(m["class"]),
			Method:  toString(m["method"]),
			URI:     toString(m["uri"]),
			AvgTime: toFloat64(m["avg_time"]),
			MaxTime: toFloat64(m["max_time"]),
			Count:   toInt(m["count"]),
		})
	}
	return result
}

// parseCountRankingList 从 interface{} 解析 []CountRankingItem
func parseCountRankingList(raw interface{}) []CountRankingItem {
	arr, ok := raw.([]interface{})
	if !ok {
		return nil
	}
	result := make([]CountRankingItem, 0, len(arr))
	for _, item := range arr {
		m, ok := item.(map[string]interface{})
		if !ok {
			continue
		}
		result = append(result, CountRankingItem{
			Project:     toString(m["project"]),
			Class:       toString(m["class"]),
			Method:      toString(m["method"]),
			URI:         toString(m["uri"]),
			Count:       toInt(m["count"]),
			Success:     toInt(m["success"]),
			Fail:        toInt(m["fail"]),
			SuccessRate: toFloat64(m["success_rate"]),
		})
	}
	return result
}

// parseTree 从 interface{} 解析树形结构
func parseTree(raw interface{}) map[string]map[string]map[string]string {
	tree := make(map[string]map[string]map[string]string)
	projects, ok := raw.(map[string]interface{})
	if !ok {
		return tree
	}
	for proj, classesRaw := range projects {
		classes, ok := classesRaw.(map[string]interface{})
		if !ok {
			continue
		}
		tree[proj] = make(map[string]map[string]string)
		for cls, methodsRaw := range classes {
			methods, ok := methodsRaw.(map[string]interface{})
			if !ok {
				continue
			}
			tree[proj][cls] = make(map[string]string)
			for mtd, uriRaw := range methods {
				uri, _ := uriRaw.(string)
				tree[proj][cls][mtd] = uri
			}
		}
	}
	return tree
}

// parseMinuteDetailList 从 interface{} 解析 []MinuteLogDetail
func parseMinuteDetailList(raw interface{}) []MinuteLogDetail {
	arr, ok := raw.([]interface{})
	if !ok {
		return nil
	}
	result := make([]MinuteLogDetail, 0, len(arr))
	for _, item := range arr {
		m, ok := item.(map[string]interface{})
		if !ok {
			continue
		}
		detail := MinuteLogDetail{
			Project: toString(m["project"]),
			Class:   toString(m["class"]),
			Method:  toString(m["method"]),
			URI:     toString(m["uri"]),
			Periods: parsePeriodsMap(m["periods"]),
			Records: parseRecordsMap(m["records"]),
		}
		result = append(result, detail)
	}
	return result
}

// parsePeriodsMap 从 interface{} 解析 map[string]*AggregationStats
func parsePeriodsMap(raw interface{}) map[string]*AggregationStats {
	periods := make(map[string]*AggregationStats)
	m, ok := raw.(map[string]interface{})
	if !ok {
		return periods
	}
	for minute, statsRaw := range m {
		statsMap, ok := statsRaw.(map[string]interface{})
		if !ok {
			continue
		}
		periods[minute] = &AggregationStats{
			Count:     toInt(statsMap["count"]),
			Success:   toInt(statsMap["success"]),
			Fail:      toInt(statsMap["fail"]),
			TotalTime: toFloat64(statsMap["total_time"]),
			MaxTime:   toFloat64(statsMap["max_time"]),
			MinTime:   toFloat64(statsMap["min_time"]),
		}
	}
	return periods
}

// parseRecordsMap 从 interface{} 解析 map[string][]RecordItem
func parseRecordsMap(raw interface{}) map[string][]RecordItem {
	records := make(map[string][]RecordItem)
	m, ok := raw.(map[string]interface{})
	if !ok {
		return records
	}
	for minute, recsRaw := range m {
		recsArr, ok := recsRaw.([]interface{})
		if !ok {
			continue
		}
		recs := make([]RecordItem, 0, len(recsArr))
		for _, recRaw := range recsArr {
			recMap, ok := recRaw.(map[string]interface{})
			if !ok {
				continue
			}
			recs = append(recs, RecordItem{
				Time:     toString(recMap["time"]),
				Duration: toFloat64(recMap["duration"]),
				Status:   toInt(recMap["status"]),
				Params:   recMap["params"],
				Response: recMap["response"],
			})
		}
		records[minute] = recs
	}
	return records
}

// ============================================================================
// 类型转换辅助函数
// ============================================================================

// toInt 将 interface{} 安全转换为 int
func toInt(v interface{}) int {
	switch n := v.(type) {
	case float64:
		return int(n)
	case int:
		return n
	case int64:
		return int(n)
	default:
		return 0
	}
}

// toFloat64 将 interface{} 安全转换为 float64
func toFloat64(v interface{}) float64 {
	switch n := v.(type) {
	case float64:
		return n
	case int:
		return float64(n)
	case int64:
		return float64(n)
	default:
		return 0
	}
}

// toString 将 interface{} 安全转换为 string
func toString(v interface{}) string {
	s, _ := v.(string)
	return s
}
