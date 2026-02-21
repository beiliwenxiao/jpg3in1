// Hello World gRPC - Golang 示例
// gRPC 服务端（端口 9093）+ HTTP 展示（端口 8093）
// 通过 gRPC 调用 Java（9091）的 Greeter.SayHello
package main

import (
	"context"
	"fmt"
	"net"
	"time"

	"hello-world-grpc/hellopb"

	"github.com/gogf/gf/v2/frame/g"
	"github.com/gogf/gf/v2/net/ghttp"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
)

// ---- gRPC 服务端实现 ----

type greeterServer struct {
	hellopb.UnimplementedGreeterServer
}

func (s *greeterServer) SayHello(ctx context.Context, req *hellopb.HelloRequest) (*hellopb.HelloReply, error) {
	name := req.GetName()
	if name == "" {
		name = "world"
	}
	fmt.Printf("[Go gRPC] 收到请求: name=%s\n", name)
	return &hellopb.HelloReply{Message: "Hello " + name + ", I am GoLang (gRPC)"}, nil
}

// ---- gRPC 客户端调用 ----

func callGrpc(host string, port int, name string) string {
	target := fmt.Sprintf("%s:%d", host, port)
	// 带重试
	for i := 0; i < 30; i++ {
		conn, err := grpc.NewClient(target, grpc.WithTransportCredentials(insecure.NewCredentials()))
		if err != nil {
			fmt.Printf("（连接失败 %ds）\r", i+1)
			time.Sleep(1 * time.Second)
			continue
		}
		client := hellopb.NewGreeterClient(conn)
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		reply, err := client.SayHello(ctx, &hellopb.HelloRequest{Name: name})
		cancel()
		conn.Close()
		if err == nil {
			return reply.GetMessage()
		}
		fmt.Printf("（等待 gRPC 服务就绪 %ds）\r", i+1)
		time.Sleep(1 * time.Second)
	}
	return "调用超时"
}

func main() {
	fmt.Println("========================================")
	fmt.Println("  Hello World gRPC - Golang (GoFrame)")
	fmt.Println("========================================")

	// 1. 启动 gRPC 服务端
	go func() {
		lis, err := net.Listen("tcp", ":9093")
		if err != nil {
			fmt.Printf("[Go] gRPC 监听失败: %v\n", err)
			return
		}
		s := grpc.NewServer()
		hellopb.RegisterGreeterServer(s, &greeterServer{})
		fmt.Println("[Go] gRPC 服务启动，端口 9093")
		if err := s.Serve(lis); err != nil {
			fmt.Printf("[Go] gRPC 服务异常: %v\n", err)
		}
	}()

	// 2. 启动 HTTP 服务
	s := g.Server()
	s.SetPort(8093)

	s.BindHandler("GET:/hello", func(r *ghttp.Request) {
		name := r.GetQuery("name").String()
		if name == "" {
			name = "world"
		}
		r.Response.WriteJsonExit(g.Map{
			"go":   "Hello " + name + ", I am GoLang (gRPC)",
			"java": callGrpc("localhost", 9091, name),
		})
	})

	s.BindHandler("GET:/", func(r *ghttp.Request) {
		r.Response.Header().Set("Content-Type", "text/html; charset=utf-8")
		r.Response.Write(helloPage("GoLang", "#00ADD8", "/hello"))
	})

	fmt.Println("[Go] HTTP 服务启动，端口 8093")
	fmt.Println("[Go] 浏览器访问: http://localhost:8093")

	go func() {
		time.Sleep(2 * time.Second)
		fmt.Println("\n[Go 本地 gRPC] Hello world, I am GoLang (gRPC)")
		fmt.Println("[Go → Java gRPC] " + callGrpc("localhost", 9091, "GoLang"))
		fmt.Println("\n[Go] 服务运行中（Ctrl+C 退出）...")
	}()

	s.Run()
}

func helloPage(lang, color, apiURL string) string {
	return `<!DOCTYPE html>
<html lang="zh"><head><meta charset="UTF-8"><title>Hello World gRPC - ` + lang + `</title>
<style>body{font-family:Arial,sans-serif;background:#f5f5f5;display:flex;justify-content:center;padding:40px}
.card{background:#fff;border-radius:12px;box-shadow:0 4px 16px rgba(0,0,0,.1);padding:40px;max-width:560px;width:100%}
h1{color:` + color + `;margin-top:0}.tag{background:#4CAF50;color:#fff;border-radius:4px;padding:2px 8px;font-size:11px;margin-left:8px}
.item{display:flex;align-items:center;gap:12px;padding:14px 0;border-bottom:1px solid #eee}.item:last-child{border-bottom:none}
.badge{color:#fff;border-radius:6px;padding:4px 10px;font-size:13px;white-space:nowrap}.msg{color:#333;font-size:15px}
.loading{color:#aaa;font-style:italic}</style></head><body>
<div class="card"><h1>🌍 Hello World gRPC — ` + lang + ` <span class="tag">gRPC</span></h1>
<div id="results"><div class="loading">正在通过 gRPC 调用各语言服务...</div></div></div>
<script>fetch('` + apiURL + `').then(r=>r.json()).then(data=>{
const labels={go:'GoLang',java:'Java'};const colors={go:'#00ADD8',java:'#ED8B00'};
document.getElementById('results').innerHTML=Object.entries(data).map(([k,v])=>` + "`" + `<div class="item">
<span class="badge" style="background:${colors[k]||'#666'}">${labels[k]||k}</span>
<span class="msg">${v}</span></div>` + "`" + `).join('');
}).catch(e=>{document.getElementById('results').innerHTML='<div class="msg" style="color:red">加载失败: '+e+'</div>';});
</script></body></html>`
}
