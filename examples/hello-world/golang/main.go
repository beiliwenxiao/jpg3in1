// Hello World - Golang 示例（GoFrame）
// 使用 RpcProxy 进行跨语言调用，服务地址通过 config.yaml 配置
// 端口 8093，与 Java(8091)、PHP(8092) 互调 hello.sayHello
package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"time"

	"github.com/gogf/gf/v2/frame/g"
	"github.com/gogf/gf/v2/net/ghttp"
	"gopkg.in/yaml.v3"
)

// ---- RPC 代理：从配置文件读取远程服务地址 ----

type rpcProxy struct {
	services map[string]serviceEndpoint
	client   *http.Client
}

type serviceEndpoint struct {
	Host string `yaml:"host"`
	Port int    `yaml:"port"`
}

type proxyConfig struct {
	Framework struct {
		Services map[string]serviceEndpoint `yaml:"services"`
	} `yaml:"framework"`
}

func loadRpcProxy(configPath string) *rpcProxy {
	p := &rpcProxy{
		services: make(map[string]serviceEndpoint),
		client:   &http.Client{Timeout: 5 * time.Second},
	}
	data, err := os.ReadFile(configPath)
	if err != nil {
		fmt.Printf("[Go] 警告: 无法读取配置 %s: %v\n", configPath, err)
		return p
	}
	var cfg proxyConfig
	if err := yaml.Unmarshal(data, &cfg); err != nil {
		fmt.Printf("[Go] 警告: 解析配置失败: %v\n", err)
		return p
	}
	p.services = cfg.Framework.Services
	for name, ep := range p.services {
		fmt.Printf("[Go] 注册远程服务: %s -> %s:%d\n", name, ep.Host, ep.Port)
	}
	return p
}

func (p *rpcProxy) Call(service, method string, params interface{}) string {
	ep, ok := p.services[service]
	if !ok {
		return "未知服务: " + service
	}
	url := fmt.Sprintf("http://%s:%d/jsonrpc", ep.Host, ep.Port)

	reqBody, _ := json.Marshal(map[string]interface{}{
		"jsonrpc": "2.0",
		"method":  method,
		"params":  params,
		"id":      1,
	})

	// 带重试（等待其他服务启动）
	for i := 0; i < 30; i++ {
		resp, err := p.client.Post(url, "application/json", bytes.NewBuffer(reqBody))
		if err != nil {
			fmt.Printf("（等待 %s 就绪 %ds）\r", service, i+1)
			time.Sleep(1 * time.Second)
			continue
		}
		defer resp.Body.Close()

		var rpcResp struct {
			Result interface{} `json:"result"`
			Error  interface{} `json:"error"`
		}
		if err := json.NewDecoder(resp.Body).Decode(&rpcResp); err != nil {
			return "解析响应失败"
		}
		if rpcResp.Result != nil {
			return fmt.Sprintf("%v", rpcResp.Result)
		}
		return fmt.Sprintf("错误: %v", rpcResp.Error)
	}
	return "调用超时: " + service
}

func main() {
	fmt.Println("========================================")
	fmt.Println("  Hello World - Golang (GoFrame)")
	fmt.Println("========================================")

	// 1. 从配置文件加载远程服务定义
	rpc := loadRpcProxy("config.yaml")

	s := g.Server()
	s.SetPort(8093)

	// POST /jsonrpc — 供其他语言调用
	s.BindHandler("POST:/jsonrpc", func(r *ghttp.Request) {
		var req struct {
			Method string      `json:"method"`
			Params interface{} `json:"params"`
			ID     int         `json:"id"`
		}
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			r.Response.WriteStatus(400, "parse error")
			return
		}
		var result interface{}
		switch req.Method {
		case "hello.sayHello":
			name := "world"
			// 支持 params 为 {"name":"xxx"} 或 ["xxx"]
			switch p := req.Params.(type) {
			case map[string]interface{}:
				if n, ok := p["name"].(string); ok && n != "" {
					name = n
				}
			case []interface{}:
				if len(p) > 0 {
					if n, ok := p[0].(string); ok && n != "" {
						name = n
					}
				}
			}
			result = "Hello " + name + ", I am GoLang"
		default:
			result = map[string]interface{}{"error": "method not found"}
		}
		r.Response.WriteJsonExit(g.Map{
			"jsonrpc": "2.0",
			"result":  result,
			"id":      req.ID,
		})
	})

	// GET /hello — 通过 RpcProxy 调用其他语言（支持 ?name=xxx）
	s.BindHandler("GET:/hello", func(r *ghttp.Request) {
		name := r.GetQuery("name").String()
		displayName := "world"
		if name != "" {
			displayName = name
		}
		var params interface{}
		if name != "" {
			params = map[string]string{"name": name}
		}
		r.Response.WriteJsonExit(g.Map{
			"go":   "Hello " + displayName + ", I am GoLang",
			"php":  rpc.Call("php-service", "hello.sayHello", params),
			"java": rpc.Call("java-service", "hello.sayHello", params),
		})
	})

	// GET / — 浏览器首页
	s.BindHandler("GET:/", func(r *ghttp.Request) {
		r.Response.Header().Set("Content-Type", "text/html; charset=utf-8")
		r.Response.Write(helloPage("GoLang", "#00ADD8", "/hello"))
	})

	fmt.Println("[Go/GoFrame] 监听端口 8093...")
	fmt.Println("[Go/GoFrame] 浏览器访问: http://localhost:8093")

	// 后台调用其他服务
	go func() {
		time.Sleep(500 * time.Millisecond)
		fmt.Println("\n[Go 本地] Hello world, I am GoLang")
		fmt.Println("[Go → PHP] " + rpc.Call("php-service", "hello.sayHello", map[string]string{"name": "GoLang"}))
		fmt.Println("[Go → Java] " + rpc.Call("java-service", "hello.sayHello", map[string]string{"name": "GoLang"}))
		fmt.Println("\n[Go] 服务运行中（Ctrl+C 退出）...")
	}()

	s.Run()
}

func helloPage(lang, color, apiURL string) string {
	return `<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="UTF-8">
<title>Hello World - ` + lang + `</title>
<style>
  body{font-family:Arial,sans-serif;background:#f5f5f5;display:flex;justify-content:center;padding:40px}
  .card{background:#fff;border-radius:12px;box-shadow:0 4px 16px rgba(0,0,0,.1);padding:40px;max-width:560px;width:100%}
  h1{color:` + color + `;margin-top:0}
  .item{display:flex;align-items:center;gap:12px;padding:14px 0;border-bottom:1px solid #eee}
  .item:last-child{border-bottom:none}
  .badge{color:#fff;border-radius:6px;padding:4px 10px;font-size:13px;white-space:nowrap}
  .msg{color:#333;font-size:15px}
  .loading{color:#aaa;font-style:italic}
</style>
</head>
<body>
<div class="card">
  <h1>🌍 Hello World — ` + lang + `</h1>
  <div id="results"><div class="loading">正在调用各语言服务...</div></div>
</div>
<script>
fetch('` + apiURL + `')
  .then(r=>r.json())
  .then(data=>{
    const labels={go:'GoLang',php:'PHP',java:'Java'};
    const colors={go:'#00ADD8',php:'#8892BF',java:'#ED8B00'};
    document.getElementById('results').innerHTML=Object.entries(data)
      .map(([k,v])=>` + "`" + `<div class="item">
        <span class="badge" style="background:${colors[k]}">${labels[k]}</span>
        <span class="msg">${v}</span></div>` + "`" + `)
      .join('');
  })
  .catch(e=>{
    document.getElementById('results').innerHTML='<div class="msg" style="color:red">加载失败: '+e+'</div>';
  });
</script>
</body>
</html>`
}
