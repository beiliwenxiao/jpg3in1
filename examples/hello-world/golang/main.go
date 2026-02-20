// Hello World - Golang 示例（GoFrame）
// 使用 GoFrame ghttp.Server 处理 HTTP，复用框架已有依赖。
// 端口 8093，与 Java(8091)、PHP(8092) 互调 hello.sayHello
package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"github.com/gogf/gf/v2/frame/g"
	"github.com/gogf/gf/v2/net/ghttp"
)

// ---- JSON-RPC 数据结构 ----

type jsonRpcRequest struct {
	Jsonrpc string      `json:"jsonrpc"`
	Method  string      `json:"method"`
	Params  interface{} `json:"params,omitempty"`
	ID      int         `json:"id"`
}

type jsonRpcResponse struct {
	Jsonrpc string      `json:"jsonrpc"`
	Result  interface{} `json:"result,omitempty"`
	Error   interface{} `json:"error,omitempty"`
	ID      int         `json:"id"`
}

func main() {
	fmt.Println("========================================")
	fmt.Println("  Hello World - Golang (GoFrame)")
	fmt.Println("========================================")

	s := g.Server()
	s.SetPort(8093)

	// POST /jsonrpc — 供其他语言调用
	s.BindHandler("POST:/jsonrpc", func(r *ghttp.Request) {
		var req jsonRpcRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			r.Response.WriteStatus(400, "parse error")
			return
		}
		var result interface{}
		switch req.Method {
		case "hello.sayHello":
			result = "Hello world, I am GoLang"
		default:
			result = map[string]interface{}{"error": "method not found"}
		}
		resp := jsonRpcResponse{Jsonrpc: "2.0", Result: result, ID: req.ID}
		r.Response.WriteJsonExit(resp)
	})

	// GET /hello — 调用其他两个语言，返回 JSON
	s.BindHandler("GET:/hello", func(r *ghttp.Request) {
		phpMsg := callRemote("http://127.0.0.1:8092/jsonrpc", "hello.sayHello", 1)
		javaMsg := callRemote("http://127.0.0.1:8091/jsonrpc", "hello.sayHello", 2)
		r.Response.WriteJsonExit(g.Map{
			"go":   "Hello world, I am GoLang",
			"php":  phpMsg,
			"java": javaMsg,
		})
	})

	// GET / — 浏览器首页
	s.BindHandler("GET:/", func(r *ghttp.Request) {
		r.Response.WriteHeader(200)
		r.Response.Header().Set("Content-Type", "text/html; charset=utf-8")
		r.Response.Write(helloPage("GoLang", "#00ADD8", "/hello"))
	})

	fmt.Println("[Go/GoFrame] 监听端口 8093...")
	fmt.Println("[Go/GoFrame] 浏览器访问: http://localhost:8093")

	// 启动前先在后台调用其他服务（不阻塞 HTTP 服务）
	go func() {
		time.Sleep(500 * time.Millisecond)
		fmt.Println("\n[Go 本地] Hello world, I am GoLang")
		fmt.Print("[Go 调用 PHP] ")
		fmt.Println(callRemote("http://127.0.0.1:8092/jsonrpc", "hello.sayHello", 1))
		fmt.Print("[Go 调用 Java] ")
		fmt.Println(callRemote("http://127.0.0.1:8091/jsonrpc", "hello.sayHello", 2))
		fmt.Println("\n[Go] 服务运行中（Ctrl+C 退出）...")
	}()

	s.Run()
}

// callRemote 调用远程 JSON-RPC（带重试）
func callRemote(url, method string, id int) string {
	client := &http.Client{Timeout: 3 * time.Second}
	for i := 0; i < 30; i++ {
		reqBody, _ := json.Marshal(jsonRpcRequest{Jsonrpc: "2.0", Method: method, ID: id})
		resp, err := client.Post(url, "application/json", bytes.NewBuffer(reqBody))
		if err != nil {
			fmt.Printf("（等待服务就绪 %ds）\r", i+1)
			time.Sleep(1 * time.Second)
			continue
		}
		defer resp.Body.Close()
		var rpcResp jsonRpcResponse
		if err := json.NewDecoder(resp.Body).Decode(&rpcResp); err != nil {
			return "解析响应失败"
		}
		if rpcResp.Result != nil {
			return fmt.Sprintf("%v", rpcResp.Result)
		}
		return fmt.Sprintf("错误: %v", rpcResp.Error)
	}
	return "调用超时"
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
  <p style="color:#666;font-size:13px">HTTP 由 GoFrame ghttp.Server 处理</p>
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
