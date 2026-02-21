<?php
/**
 * Hello World gRPC - PHP 客户端
 * PHP 通过 HTTP 调用 Java/Go 的 gRPC 服务展示结果
 * 端口 8092
 *
 * 注意：PHP 本身不启动 gRPC 服务端，而是作为 HTTP 客户端
 * 调用 Java(8091)/Go(8093) 的 /hello 接口（它们内部走 gRPC）
 */

$port = 8092;

// 简单 HTTP 服务
$socket = stream_socket_server("tcp://0.0.0.0:$port", $errno, $errstr);
if (!$socket) {
    die("[PHP] 启动失败: $errstr ($errno)\n");
}

echo "========================================\n";
echo "  Hello World gRPC - PHP 客户端\n";
echo "========================================\n";
echo "[PHP] HTTP 服务启动，端口 $port\n";
echo "[PHP] 浏览器访问: http://localhost:$port\n";

// 后台调用测试
echo "\n[PHP → Java gRPC] " . callHttp('localhost', 8091, 'PHP') . "\n";
echo "[PHP → Go gRPC] " . callHttp('localhost', 8093, 'PHP') . "\n";
echo "\n[PHP] 服务运行中（Ctrl+C 退出）...\n";

while ($conn = @stream_socket_accept($socket, -1)) {
    $request = fread($conn, 8192);

    // 解析请求路径
    preg_match('/^(GET|POST)\s+(\S+)/', $request, $matches);
    $path = $matches[2] ?? '/';

    if (str_starts_with($path, '/hello')) {
        // 解析 name 参数
        $query = parse_url($path, PHP_URL_QUERY);
        parse_str($query ?? '', $params);
        $name = $params['name'] ?? 'world';

        $javaMsg = callHttp('localhost', 8091, $name);
        $goMsg = callHttp('localhost', 8093, $name);

        $json = json_encode([
            'php' => "Hello $name, I am PHP (via gRPC proxy)",
            'java' => $javaMsg,
            'go' => $goMsg,
        ], JSON_UNESCAPED_UNICODE);

        $response = "HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=utf-8\r\n\r\n$json";
    } elseif ($path === '/' || $path === '/index.html') {
        $html = helloPage('PHP', '#8892BF', '/hello');
        $response = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n\r\n$html";
    } else {
        $response = "HTTP/1.1 404 Not Found\r\n\r\nNot Found";
    }

    fwrite($conn, $response);
    fclose($conn);
}

function callHttp(string $host, int $port, string $name): string {
    $url = "http://$host:$port/hello?name=" . urlencode($name);
    for ($i = 0; $i < 30; $i++) {
        $ctx = stream_context_create(['http' => ['timeout' => 5]]);
        $result = @file_get_contents($url, false, $ctx);
        if ($result !== false) {
            $data = json_decode($result, true);
            // 返回对应语言的消息
            if ($port === 8091) return $data['java'] ?? $result;
            if ($port === 8093) return $data['go'] ?? $result;
            return $result;
        }
        usleep(1000000);
    }
    return "调用超时: $host:$port";
}

function helloPage(string $lang, string $color, string $apiURL): string {
    return <<<HTML
<!DOCTYPE html><html lang="zh"><head><meta charset="UTF-8"><title>Hello World gRPC - $lang</title>
<style>body{font-family:Arial,sans-serif;background:#f5f5f5;display:flex;justify-content:center;padding:40px}
.card{background:#fff;border-radius:12px;box-shadow:0 4px 16px rgba(0,0,0,.1);padding:40px;max-width:560px;width:100%}
h1{color:$color;margin-top:0}.tag{background:#4CAF50;color:#fff;border-radius:4px;padding:2px 8px;font-size:11px;margin-left:8px}
.item{display:flex;align-items:center;gap:12px;padding:14px 0;border-bottom:1px solid #eee}.item:last-child{border-bottom:none}
.badge{color:#fff;border-radius:6px;padding:4px 10px;font-size:13px;white-space:nowrap}.msg{color:#333;font-size:15px}
.loading{color:#aaa;font-style:italic}</style></head><body>
<div class="card"><h1>🌍 Hello World gRPC — $lang <span class="tag">gRPC Proxy</span></h1>
<div id="results"><div class="loading">正在通过 gRPC 代理调用各语言服务...</div></div></div>
<script>fetch('$apiURL').then(r=>r.json()).then(data=>{
const labels={go:'GoLang',java:'Java',php:'PHP'};const colors={go:'#00ADD8',java:'#ED8B00',php:'#8892BF'};
document.getElementById('results').innerHTML=Object.entries(data).map(([k,v])=>`<div class="item">
<span class="badge" style="background:\${colors[k]||'#666'}">\${labels[k]||k}</span>
<span class="msg">\${v}</span></div>`).join('');
}).catch(e=>{document.getElementById('results').innerHTML='<div class="msg" style="color:red">加载失败: '+e+'</div>';});
</script></body></html>
HTML;
}
