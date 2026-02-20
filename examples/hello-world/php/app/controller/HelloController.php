<?php
/**
 * HelloController - Webman HTTP 控制器
 *
 * 职责分工：
 *   Webman  → 处理所有 HTTP 路由（/jsonrpc、/hello、/）
 *   Workerman → 底层 Worker 进程管理（由 Webman 内部使用）
 *
 * 复用 php-sdk 的 JsonRpcHandler 处理 JSON-RPC 协议解析。
 */

namespace app\controller;

use Framework\Protocol\External\JsonRpcHandler;
use support\Request;
use Webman\Http\Response;

class HelloController
{
    private JsonRpcHandler $rpc;

    public function __construct()
    {
        // 复用 php-sdk 的 JsonRpcHandler
        $this->rpc = new JsonRpcHandler();
        $this->rpc->register('hello.sayHello', fn(array $p): string => 'Hello world, I am PHP');
    }

    /**
     * POST /jsonrpc — 供 Go / Java 调用
     */
    public function jsonrpc(Request $request): Response
    {
        $result = $this->rpc->handle($request->rawBody());
        return new Response(200, ['Content-Type' => 'application/json'], $result);
    }

    /**
     * GET /hello — 调用其他两个语言，返回 JSON（供浏览器 fetch）
     */
    public function hello(Request $request): Response
    {
        $goMsg   = $this->callRemote('http://127.0.0.1:8093/jsonrpc', 'hello.sayHello', 1);
        $javaMsg = $this->callRemote('http://127.0.0.1:8092/jsonrpc', 'hello.sayHello', 2);

        $json = json_encode([
            'php'  => 'Hello world, I am PHP',
            'go'   => $goMsg,
            'java' => $javaMsg,
        ], JSON_UNESCAPED_UNICODE);

        return new Response(200, ['Content-Type' => 'application/json; charset=utf-8'], $json);
    }

    /**
     * GET / — 浏览器首页
     */
    public function index(Request $request): Response
    {
        return new Response(200, ['Content-Type' => 'text/html; charset=utf-8'], $this->helloPage());
    }

    // ---- 调用远程 JSON-RPC（同步，适合示例演示）----

    private function callRemote(string $url, string $method, int $id): string
    {
        $payload = json_encode(['jsonrpc' => '2.0', 'method' => $method, 'id' => $id]);
        $ctx = stream_context_create([
            'http' => [
                'method'        => 'POST',
                'header'        => "Content-Type: application/json\r\n",
                'content'       => $payload,
                'timeout'       => 5,
                'ignore_errors' => true,
            ],
        ]);
        $resp = @file_get_contents($url, false, $ctx);
        if ($resp === false) return '调用失败';
        $data = json_decode($resp, true);
        return $data['result'] ?? ('错误: ' . json_encode($data['error'] ?? '未知'));
    }

    // ---- HTML 页面模板 ----

    private function helloPage(): string
    {
        $lang  = 'PHP';
        $color = '#8892BF';
        $api   = '/hello';
        return <<<HTML
<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="UTF-8">
<title>Hello World - {$lang}</title>
<style>
  body{font-family:Arial,sans-serif;background:#f5f5f5;display:flex;justify-content:center;padding:40px}
  .card{background:#fff;border-radius:12px;box-shadow:0 4px 16px rgba(0,0,0,.1);padding:40px;max-width:560px;width:100%}
  h1{color:{$color};margin-top:0}
  .item{display:flex;align-items:center;gap:12px;padding:14px 0;border-bottom:1px solid #eee}
  .item:last-child{border-bottom:none}
  .badge{color:#fff;border-radius:6px;padding:4px 10px;font-size:13px;white-space:nowrap}
  .msg{color:#333;font-size:15px}
  .loading{color:#aaa;font-style:italic}
</style>
</head>
<body>
<div class="card">
  <h1>🌍 Hello World — {$lang}</h1>
  <p style="color:#666;font-size:13px">HTTP 由 Webman 处理 · Worker 进程由 Workerman 管理</p>
  <div id="results"><div class="loading">正在调用各语言服务...</div></div>
</div>
<script>
fetch('{$api}')
  .then(r=>r.json())
  .then(data=>{
    const labels={go:'GoLang',php:'PHP',java:'Java'};
    const colors={go:'#00ADD8',php:'#8892BF',java:'#ED8B00'};
    document.getElementById('results').innerHTML=Object.entries(data)
      .map(([k,v])=>`<div class="item">
        <span class="badge" style="background:\${colors[k]}">\${labels[k]}</span>
        <span class="msg">\${v}</span></div>`)
      .join('');
  })
  .catch(e=>{
    document.getElementById('results').innerHTML=`<div class="msg" style="color:red">加载失败: \${e}</div>`;
  });
</script>
</body>
</html>
HTML;
    }
}
