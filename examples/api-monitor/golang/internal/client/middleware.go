package client

import (
	"strings"
	"time"
	"unicode"

	"github.com/gogf/gf/v2/net/ghttp"
)

// NewMonitorMiddleware 创建 GoFrame 监控中间件
// 返回 ghttp.HandlerFunc，可通过 s.Use() 注册为全局中间件
func NewMonitorMiddleware(client *MonitorClient, exclude []string, enabled bool) ghttp.HandlerFunc {
	return func(r *ghttp.Request) {
		// enabled 为 false 时直接放行
		if !enabled {
			r.Middleware.Next()
			return
		}

		// 获取请求 URI
		uri := r.URL.Path
		if uri == "" {
			uri = "/"
		}

		// 匹配 exclude 列表时跳过采集
		if IsExcluded(uri, exclude) {
			r.Middleware.Next()
			return
		}

		// 记录请求开始时间
		startTime := time.Now()

		// 执行后续中间件和处理器
		r.Middleware.Next()

		// 计算耗时（毫秒）
		duration := float64(time.Since(startTime).Microseconds()) / 1000.0

		// 获取 HTTP 状态码
		status := r.Response.Status
		if status == 0 {
			status = 200
		}

		// 解析控制器和方法
		class, method := ParseAction(r)

		// 上报数据
		client.Report(class, method, uri, status, duration, nil, nil)
	}
}

// ParseAction 从请求中解析控制器类名和方法名
// 导出为公共函数，供属性测试使用
func ParseAction(r *ghttp.Request) (string, string) {
	// 降级：从 URI 路径提取 class 和 method
	// GoFrame 的路由处理器信息解析较复杂，与 PHP 版 ThinkPHP 的 parseAction 类似，
	// 优先从 URI 路径中提取
	return ParseActionFromURI(r.URL.Path)
}

// ParseActionFromURI 从 URI 路径中提取控制器名和方法名
// 导出为公共函数，供属性测试使用
// 规则：第一段首字母大写 + Controller 后缀作为 class，第二段作为 method（默认 index）
func ParseActionFromURI(uri string) (string, string) {
	path := strings.Trim(uri, "/")
	if path == "" {
		return "IndexController", "index"
	}

	parts := strings.SplitN(path, "/", 3)

	// 提取 class：首字母大写 + Controller 后缀
	className := parts[0]
	if className == "" {
		className = "Index"
	} else {
		// 首字母大写
		runes := []rune(className)
		runes[0] = unicode.ToUpper(runes[0])
		className = string(runes)
	}
	if !strings.HasSuffix(className, "Controller") {
		className += "Controller"
	}

	// 提取 method：第二段，默认 index
	method := "index"
	if len(parts) > 1 && parts[1] != "" {
		method = parts[1]
	}

	return className, method
}

// IsExcluded 检查 URI 是否在排除列表中
// 导出为公共函数，供属性测试使用
func IsExcluded(uri string, exclude []string) bool {
	for _, pattern := range exclude {
		if strings.HasPrefix(uri, pattern) {
			return true
		}
	}
	return false
}
