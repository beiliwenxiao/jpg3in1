package server

import (
	"crypto/md5"
	"fmt"
	"math"
	"os"
	"path/filepath"
	"sort"
	"time"

	"github.com/gogf/gf/v2/frame/g"
	"github.com/gogf/gf/v2/net/ghttp"
)

// ============================================================================
// HttpController HTTP API 控制器
// ============================================================================

// HttpController HTTP API 控制器
// 提供与 PHP 版 MonitorController 完全兼容的 RESTful API 接口。
// 所有查询自动合并内存实时数据（当前 session）与日志历史数据（排除当前 session）。
type HttpController struct {
	storage   StorageInterface
	logger    *MonitorLogger
	password  string
	publicDir string
}

// NewHttpController 创建 HTTP 控制器
func NewHttpController(storage StorageInterface, logger *MonitorLogger, password string, publicDir string) *HttpController {
	return &HttpController{
		storage:   storage,
		logger:    logger,
		password:  password,
		publicDir: publicDir,
	}
}

// RegisterRoutes 注册所有路由到 GoFrame Server
// 路由映射与 PHP 版 route.php 完全一致
func (c *HttpController) RegisterRoutes(s *ghttp.Server) {
	// API 接口
	s.BindHandler("GET:/api/dashboard", c.dashboard)
	s.BindHandler("GET:/api/tree", c.tree)
	s.BindHandler("GET:/api/detail", c.detail)
	s.BindHandler("GET:/api/ranking/slow", c.rankingSlow)
	s.BindHandler("GET:/api/ranking/count", c.rankingCount)
	s.BindHandler("GET:/api/realtime", c.realtime)
	s.BindHandler("GET:/api/trend", c.trend)
	s.BindHandler("GET:/api/search", c.search)
	s.BindHandler("GET:/api/dates", c.dates)
	s.BindHandler("GET:/api/records", c.records)
	s.BindHandler("POST:/api/login", c.login)
	s.BindHandler("GET:/api/check-login", c.checkLogin)

	// 仪表盘页面
	s.BindHandler("GET:/", c.index)
}

// jsonResponse 统一 JSON 响应格式：{"code": 0, "data": ...}
func (c *HttpController) jsonResponse(r *ghttp.Request, data interface{}) {
	r.Response.WriteJsonExit(g.Map{"code": 0, "data": data})
}

// ============================================================================
// API 接口实现
// ============================================================================

// dashboard 仪表盘概览
// 合并内存实时数据（当前 session）与日志历史数据（排除当前 session）
// 逻辑与 PHP MonitorController::dashboard 完全一致
func (c *HttpController) dashboard(r *ghttp.Request) {
	date := r.GetQuery("date", time.Now().Format("2006-01-02")).String()

	live := c.storage.GetDashboard(date)
	logData := c.getDashboardFromLog(date)

	// 日志中是所有历史 session 的累加，内存中是当前 session，直接累加
	if logData.TotalCount > 0 && live.TotalCount > 0 {
		totalCount := logData.TotalCount + live.TotalCount
		totalSuccess := logData.Success + live.Success
		totalFail := logData.Fail + live.Fail
		logTime := logData.AvgTime * float64(logData.TotalCount)
		liveTime := live.AvgTime * float64(live.TotalCount)

		successRate := 0.0
		avgTime := 0.0
		if totalCount > 0 {
			successRate = math.Round(float64(totalSuccess)/float64(totalCount)*10000) / 100
			avgTime = math.Round((logTime+liveTime)/float64(totalCount)*100) / 100
		}

		c.jsonResponse(r, g.Map{
			"date":         date,
			"total_count":  totalCount,
			"success":      totalSuccess,
			"fail":         totalFail,
			"success_rate": successRate,
			"avg_time":     avgTime,
		})
	} else if logData.TotalCount > 0 {
		c.jsonResponse(r, logData)
	} else {
		c.jsonResponse(r, live)
	}
}

// getDashboardFromLog 从日志读取仪表盘数据
func (c *HttpController) getDashboardFromLog(date string) DashboardData {
	log := c.logger.ReadDayLog(date)
	if log == nil {
		return DashboardData{
			Date:        date,
			TotalCount:  0,
			Success:     0,
			Fail:        0,
			SuccessRate: 0,
			AvgTime:     0,
		}
	}
	d := log.Dashboard
	if d.Date == "" {
		d.Date = date
	}
	return d
}

// tree 树形菜单
// 合并内存 tree 与日志 tree（union）
func (c *HttpController) tree(r *ghttp.Request) {
	date := r.GetQuery("date", "").String()
	tree := c.storage.GetTree()

	// 合并日志中的树
	if date != "" {
		logTree := c.getTreeFromLog(date)
		for proj, classes := range logTree {
			if tree[proj] == nil {
				tree[proj] = make(map[string]map[string]string)
			}
			for cls, methods := range classes {
				if tree[proj][cls] == nil {
					tree[proj][cls] = make(map[string]string)
				}
				for mtd, uri := range methods {
					if _, exists := tree[proj][cls][mtd]; !exists {
						tree[proj][cls][mtd] = uri
					}
				}
			}
		}
	}

	c.jsonResponse(r, tree)
}

// getTreeFromLog 从日志读取树形结构
func (c *HttpController) getTreeFromLog(date string) map[string]map[string]map[string]string {
	log := c.logger.ReadDayLog(date)
	if log == nil {
		return make(map[string]map[string]map[string]string)
	}
	return log.Tree
}

// detail 接口详情
// 合并内存 periods 与日志 periods
// 逻辑与 PHP MonitorController::detail 完全一致
func (c *HttpController) detail(r *ghttp.Request) {
	project := r.GetQuery("project", "").String()
	class := r.GetQuery("class", "").String()
	method := r.GetQuery("method", "").String()
	date := r.GetQuery("date", time.Now().Format("2006-01-02")).String()
	granularity := r.GetQuery("granularity", "minute").String()

	live := c.storage.GetDetail(project, class, method, date, granularity)
	logDetails := c.getDetailFromLog(project, class, method, date)

	// 提取 periods
	var livePeriods map[string]*AggregationStats
	if len(live) > 0 && live[0].Periods != nil {
		livePeriods = live[0].Periods
	}
	var logPeriods map[string]*AggregationStats
	if len(logDetails) > 0 && logDetails[0].Periods != nil {
		logPeriods = logDetails[0].Periods
	}

	if len(livePeriods) == 0 && len(logPeriods) == 0 {
		c.jsonResponse(r, []interface{}{})
		return
	}

	// 合并 periods：日志是历史 session 累加，内存是当前 session，直接累加
	merged := make(map[string]map[string]interface{})

	// 先放入日志数据
	for period, stats := range logPeriods {
		merged[period] = map[string]interface{}{
			"count":        stats.Count,
			"success":      stats.Success,
			"fail":         stats.Fail,
			"total_time":   stats.TotalTime,
			"max_time":     stats.MaxTime,
			"min_time":     stats.MinTime,
			"avg_time":     0.0,
			"success_rate": 0.0,
		}
		if stats.Count > 0 {
			merged[period]["avg_time"] = math.Round(stats.TotalTime/float64(stats.Count)*100) / 100
			merged[period]["success_rate"] = math.Round(float64(stats.Success)/float64(stats.Count)*10000) / 100
		}
	}

	// 合并内存数据
	for period, stats := range livePeriods {
		if existing, ok := merged[period]; ok {
			oldCount := existing["count"].(int)
			totalCount := oldCount + stats.Count
			totalSuccess := existing["success"].(int) + stats.Success
			totalFail := existing["fail"].(int) + stats.Fail
			totalTime := existing["total_time"].(float64) + stats.TotalTime
			maxTime := math.Max(existing["max_time"].(float64), stats.MaxTime)

			oldMinTime := existing["min_time"].(float64)
			newMinTime := stats.MinTime
			if newMinTime == math.MaxFloat64 {
				newMinTime = oldMinTime
			}
			if oldMinTime == math.MaxFloat64 {
				oldMinTime = newMinTime
			}
			minTime := math.Min(oldMinTime, newMinTime)

			avgTime := 0.0
			successRate := 0.0
			if totalCount > 0 {
				avgTime = math.Round(totalTime/float64(totalCount)*100) / 100
				successRate = math.Round(float64(totalSuccess)/float64(totalCount)*10000) / 100
			}

			merged[period] = map[string]interface{}{
				"count":        totalCount,
				"success":      totalSuccess,
				"fail":         totalFail,
				"total_time":   totalTime,
				"max_time":     maxTime,
				"min_time":     minTime,
				"avg_time":     avgTime,
				"success_rate": successRate,
			}
		} else {
			avgTime := 0.0
			successRate := 0.0
			minTime := stats.MinTime
			if minTime == math.MaxFloat64 {
				minTime = 0
			}
			if stats.Count > 0 {
				avgTime = math.Round(stats.TotalTime/float64(stats.Count)*100) / 100
				successRate = math.Round(float64(stats.Success)/float64(stats.Count)*10000) / 100
			}
			merged[period] = map[string]interface{}{
				"count":        stats.Count,
				"success":      stats.Success,
				"fail":         stats.Fail,
				"total_time":   stats.TotalTime,
				"max_time":     stats.MaxTime,
				"min_time":     minTime,
				"avg_time":     avgTime,
				"success_rate": successRate,
			}
		}
	}

	// 按 period key 排序
	sortedPeriods := make([]string, 0, len(merged))
	for k := range merged {
		sortedPeriods = append(sortedPeriods, k)
	}
	sort.Strings(sortedPeriods)

	// 构建有序的 periods map（Go 的 map 无序，但 JSON 序列化时 GoFrame 会保持插入顺序）
	orderedPeriods := make(map[string]interface{}, len(merged))
	for _, k := range sortedPeriods {
		orderedPeriods[k] = merged[k]
	}

	// 确定基础信息
	var base DetailData
	if len(live) > 0 {
		base = live[0]
	} else if len(logDetails) > 0 {
		base = logDetails[0]
	}

	data := []g.Map{{
		"project": base.Project,
		"class":   base.Class,
		"method":  base.Method,
		"uri":     base.URI,
		"periods": orderedPeriods,
	}}
	c.jsonResponse(r, data)
}

// getDetailFromLog 从日志读取接口详情
func (c *HttpController) getDetailFromLog(project, class, method, date string) []DetailData {
	log := c.logger.ReadMinuteLog(date)
	if log == nil {
		return nil
	}
	var result []DetailData
	for _, detail := range log.Details {
		if project != "" && detail.Project != project {
			continue
		}
		if class != "" && detail.Class != class {
			continue
		}
		if method != "" && detail.Method != method {
			continue
		}
		result = append(result, DetailData{
			Project: detail.Project,
			Class:   detail.Class,
			Method:  detail.Method,
			URI:     detail.URI,
			Periods: detail.Periods,
		})
	}
	return result
}

// rankingSlow 慢速接口排行
// 合并内存排行与日志排行，相同接口累加统计后按 avg_time 降序重新排序
func (c *HttpController) rankingSlow(r *ghttp.Request) {
	date := r.GetQuery("date", time.Now().Format("2006-01-02")).String()
	limit := r.GetQuery("limit", 50).Int()

	live := c.storage.GetSlowRanking(date, limit)
	logRanking := c.getSlowRankingFromLog(date, limit)

	result := MergeSlowRanking(logRanking, live, limit)
	c.jsonResponse(r, result)
}

// getSlowRankingFromLog 从日志读取慢速排行
func (c *HttpController) getSlowRankingFromLog(date string, limit int) []SlowRankingItem {
	log := c.logger.ReadDayLog(date)
	if log == nil {
		return nil
	}
	ranking := log.SlowRanking
	if limit > 0 && len(ranking) > limit {
		ranking = ranking[:limit]
	}
	return ranking
}

// MergeSlowRanking 合并慢速排行榜
// 导出为公共函数供属性测试使用
// 逻辑与 PHP MonitorController::mergeRanking 完全一致
func MergeSlowRanking(old, new []SlowRankingItem, limit int) []SlowRankingItem {
	m := make(map[string]*SlowRankingItem)

	// 先放入日志数据（old）
	for i := range old {
		key := old[i].Project + "|" + old[i].Class + "|" + old[i].Method
		item := old[i]
		m[key] = &item
	}

	// 合并内存数据（new）
	for i := range new {
		key := new[i].Project + "|" + new[i].Class + "|" + new[i].Method
		if existing, ok := m[key]; ok {
			oldTotal := existing.AvgTime * float64(existing.Count)
			newTotal := new[i].AvgTime * float64(new[i].Count)
			tc := existing.Count + new[i].Count
			merged := new[i]
			merged.Count = tc
			if tc > 0 {
				merged.AvgTime = math.Round((oldTotal+newTotal)/float64(tc)*100) / 100
			}
			merged.MaxTime = math.Max(existing.MaxTime, new[i].MaxTime)
			m[key] = &merged
		} else {
			item := new[i]
			m[key] = &item
		}
	}

	// 转为列表并按 avg_time 降序排序
	result := make([]SlowRankingItem, 0, len(m))
	for _, item := range m {
		result = append(result, *item)
	}
	sort.Slice(result, func(i, j int) bool {
		return result[i].AvgTime > result[j].AvgTime
	})

	if limit > 0 && len(result) > limit {
		result = result[:limit]
	}
	return result
}

// rankingCount 访问次数排行
// 合并内存排行与日志排行，相同接口累加统计后按 count 降序重新排序
func (c *HttpController) rankingCount(r *ghttp.Request) {
	date := r.GetQuery("date", time.Now().Format("2006-01-02")).String()
	limit := r.GetQuery("limit", 50).Int()

	live := c.storage.GetCountRanking(date, limit)
	logRanking := c.getCountRankingFromLog(date, limit)

	result := MergeCountRanking(logRanking, live, limit)
	c.jsonResponse(r, result)
}

// getCountRankingFromLog 从日志读取访问量排行
func (c *HttpController) getCountRankingFromLog(date string, limit int) []CountRankingItem {
	log := c.logger.ReadDayLog(date)
	if log == nil {
		return nil
	}
	ranking := log.CountRanking
	if limit > 0 && len(ranking) > limit {
		ranking = ranking[:limit]
	}
	return ranking
}

// MergeCountRanking 合并访问量排行榜
// 导出为公共函数供属性测试使用
// 逻辑与 PHP MonitorController::mergeRanking 完全一致
func MergeCountRanking(old, new []CountRankingItem, limit int) []CountRankingItem {
	m := make(map[string]*CountRankingItem)

	// 先放入日志数据（old）
	for i := range old {
		key := old[i].Project + "|" + old[i].Class + "|" + old[i].Method
		item := old[i]
		m[key] = &item
	}

	// 合并内存数据（new）
	for i := range new {
		key := new[i].Project + "|" + new[i].Class + "|" + new[i].Method
		if existing, ok := m[key]; ok {
			tc := existing.Count + new[i].Count
			merged := new[i]
			merged.Count = tc
			merged.Success = existing.Success + new[i].Success
			merged.Fail = existing.Fail + new[i].Fail
			if tc > 0 {
				merged.SuccessRate = math.Round(float64(merged.Success)/float64(tc)*10000) / 100
			}
			m[key] = &merged
		} else {
			item := new[i]
			m[key] = &item
		}
	}

	// 转为列表并按 count 降序排序
	result := make([]CountRankingItem, 0, len(m))
	for _, item := range m {
		result = append(result, *item)
	}
	sort.Slice(result, func(i, j int) bool {
		return result[i].Count > result[j].Count
	})

	if limit > 0 && len(result) > limit {
		result = result[:limit]
	}
	return result
}

// realtime 实时访问量
func (c *HttpController) realtime(r *ghttp.Request) {
	c.jsonResponse(r, c.storage.GetRealtime())
}

// trend 全天访问趋势（分钟级）
// 合并内存趋势与日志分钟数据
// 逻辑与 PHP MonitorStorage::getDayTrend 一致
func (c *HttpController) trend(r *ghttp.Request) {
	date := r.GetQuery("date", time.Now().Format("2006-01-02")).String()

	live := c.storage.GetDayTrend(date)

	// 从日志读取分钟级数据
	logMinuteData := c.logger.ReadMinuteLog(date)
	if logMinuteData == nil {
		c.jsonResponse(r, live)
		return
	}

	// 构建日志中的分钟汇总（已是所有历史 session 的累加）
	logMinutes := make(map[string]*struct {
		Count   int
		Success int
		Fail    int
	})
	for _, detail := range logMinuteData.Details {
		for period, stats := range detail.Periods {
			if _, ok := logMinutes[period]; !ok {
				logMinutes[period] = &struct {
					Count   int
					Success int
					Fail    int
				}{}
			}
			logMinutes[period].Count += stats.Count
			logMinutes[period].Success += stats.Success
			logMinutes[period].Fail += stats.Fail
		}
	}

	if len(logMinutes) == 0 {
		c.jsonResponse(r, live)
		return
	}

	// 直接累加（日志是历史 session，内存是当前 session，不重复）
	for i := range live {
		if logMin, ok := logMinutes[live[i].Time]; ok {
			live[i].Count += logMin.Count
			live[i].Success += logMin.Success
			live[i].Fail += logMin.Fail
		}
	}

	c.jsonResponse(r, live)
}

// search 搜索接口
func (c *HttpController) search(r *ghttp.Request) {
	keyword := r.GetQuery("keyword", "").String()
	date := r.GetQuery("date", time.Now().Format("2006-01-02")).String()
	c.jsonResponse(r, c.storage.Search(keyword, date))
}

// dates 可用日期列表
// 返回前后 7 天列表，标注是否有数据及来源
// 逻辑与 PHP MonitorStorage::getAvailableDates 一致
func (c *HttpController) dates(r *ghttp.Request) {
	today := time.Now().Format("2006-01-02")
	var dates []g.Map

	for i := -7; i <= 7; i++ {
		date := time.Now().AddDate(0, 0, i).Format("2006-01-02")
		hasBackend := c.storage.HasData(date)
		hasLog := c.hasLogFile(date)

		source := "none"
		if hasBackend {
			source = "memory"
		} else if hasLog {
			source = "log"
		}

		dates = append(dates, g.Map{
			"date":     date,
			"has_data": hasBackend || hasLog,
			"source":   source,
			"is_today": date == today,
		})
	}

	c.jsonResponse(r, dates)
}

// hasLogFile 检查指定日期的日志文件是否存在
func (c *HttpController) hasLogFile(date string) bool {
	logFile := filepath.Join(c.logger.logDir, date+".json")
	_, err := os.Stat(logFile)
	return err == nil
}

// records 访问明细（某接口某分钟）
// 先从内存获取，如果为空则从日志回退
// 逻辑与 PHP MonitorStorage::getRecords 一致
func (c *HttpController) records(r *ghttp.Request) {
	project := r.GetQuery("project", "").String()
	class := r.GetQuery("class", "").String()
	method := r.GetQuery("method", "").String()
	minute := r.GetQuery("minute", "").String()
	limit := r.GetQuery("limit", 100).Int()

	records := c.storage.GetRecords(project, class, method, minute, limit)

	// 内存无数据时从日志文件回退
	if len(records) == 0 {
		date := ""
		if len(minute) >= 10 {
			date = minute[:10]
		}
		if date != "" {
			records = c.logger.ReadRecordsFromLog(date, project, class, method, minute)
		}
	}

	// 限制返回数量
	if limit > 0 && len(records) > limit {
		records = records[:limit]
	}

	c.jsonResponse(r, records)
}

// ============================================================================
// 登录验证和仪表盘页面
// ============================================================================

// GenerateToken 生成登录凭证
// 使用 md5(password + "_monitor_salt") 算法，与 PHP 版兼容
func GenerateToken(password string) string {
	hash := md5.Sum([]byte(password + "_monitor_salt"))
	return fmt.Sprintf("%x", hash)
}

// login 登录验证接口
// 验证密码，生成凭证 md5(password + "_monitor_salt")，通过 Set-Cookie 写入 monitor_token，有效期 30 天
// 逻辑与 PHP MonitorController::login 完全一致
func (c *HttpController) login(r *ghttp.Request) {
	pwd := r.GetForm("password", r.GetQuery("password", "")).String()

	if c.password == "" {
		c.jsonResponse(r, g.Map{"ok": true, "msg": "无需密码"})
		return
	}

	if pwd == c.password {
		token := GenerateToken(c.password)
		r.Response.Header().Set("Set-Cookie", fmt.Sprintf("monitor_token=%s; Path=/; Max-Age=%d", token, 86400*30))
		r.Response.WriteJsonExit(g.Map{"code": 0, "data": g.Map{"ok": true}})
		return
	}

	r.Response.WriteJsonExit(g.Map{"code": 1, "data": g.Map{"ok": false, "msg": "密码错误"}})
}

// checkLogin 检查登录状态
// 检查 cookie 中 monitor_token 是否匹配预期凭证
// 逻辑与 PHP MonitorController::checkLogin 完全一致
func (c *HttpController) checkLogin(r *ghttp.Request) {
	if c.password == "" {
		c.jsonResponse(r, g.Map{"need_login": false})
		return
	}

	token := r.Cookie.Get("monitor_token").String()
	expected := GenerateToken(c.password)
	loggedIn := token == expected

	c.jsonResponse(r, g.Map{"need_login": !loggedIn})
}

// index 仪表盘首页
// 返回 public/index.html 内容，Content-Type 为 text/html; charset=utf-8
func (c *HttpController) index(r *ghttp.Request) {
	htmlFile := filepath.Join(c.publicDir, "index.html")
	content, err := os.ReadFile(htmlFile)
	if err != nil {
		r.Response.WriteStatus(404, "Dashboard HTML not found")
		return
	}
	r.Response.Header().Set("Content-Type", "text/html; charset=utf-8")
	r.Response.WriteExit(string(content))
}


