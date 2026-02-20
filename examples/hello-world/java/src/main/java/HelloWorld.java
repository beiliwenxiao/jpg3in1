/**
 * Hello World - Java 示例
 * 启动一个 JSON-RPC 服务（端口 8091），同时调用 PHP（8092）和 Go（8093）的服务
 *
 * 复用框架的 JSON-RPC HTTP 处理逻辑（参考 JsonRpcInternalServer）
 * 无需 Spring Boot，使用 JDK 内置 HttpServer 即可独立运行
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class HelloWorld {

    private static final ObjectMapper mapper = new ObjectMapper();

    // ---- 启动本地 JSON-RPC 服务（端口 8091）----

    static void startJavaServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8091), 0);

        // JSON-RPC 接口（供其他语言调用）
        server.createContext("/jsonrpc", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
            String responseJson = handleJsonRpc(new String(bodyBytes, StandardCharsets.UTF_8));
            byte[] respBytes = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, respBytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(respBytes); }
        });

        // /hello 接口：调用其他两个语言并返回 JSON（供浏览器页面 fetch）
        server.createContext("/hello", exchange -> {
            String phpMsg = callRemote("http://localhost:8092/jsonrpc", "hello.sayHello", 1);
            String goMsg  = callRemote("http://localhost:8093/jsonrpc", "hello.sayHello", 2);
            String json = String.format(
                "{\"java\":\"Hello world, I am JAVA\",\"php\":\"%s\",\"go\":\"%s\"}",
                phpMsg.replace("\"", "\\\""), goMsg.replace("\"", "\\\"")
            );
            byte[] respBytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, respBytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(respBytes); }
        });

        // 浏览器首页
        server.createContext("/", exchange -> {
            byte[] html = helloPage("Java", "#ED8B00", "/hello")
                              .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, html.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(html); }
        });

        server.start();
        System.out.println("[Java] JSON-RPC 服务启动，监听端口 8091...");
        System.out.println("[Java] 浏览器访问: http://localhost:8091");
    }

    @SuppressWarnings("unchecked")
    static String handleJsonRpc(String requestBody) {
        try {
            Map<String, Object> req = mapper.readValue(requestBody, Map.class);
            Object id = req.get("id");
            String method = (String) req.getOrDefault("method", "");

            Object result;
            if ("hello.sayHello".equals(method)) {
                result = "Hello world, I am JAVA";
            } else {
                Map<String, Object> err = new HashMap<>();
                err.put("code", -32601);
                err.put("message", "Method not found: " + method);
                Map<String, Object> errResp = new HashMap<>();
                errResp.put("jsonrpc", "2.0");
                errResp.put("error", err);
                errResp.put("id", id);
                return mapper.writeValueAsString(errResp);
            }

            Map<String, Object> resp = new HashMap<>();
            resp.put("jsonrpc", "2.0");
            resp.put("result", result);
            resp.put("id", id);
            return mapper.writeValueAsString(resp);

        } catch (Exception e) {
            return "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32700,\"message\":\"Parse error\"},\"id\":null}";
        }
    }

    // ---- 调用远程 JSON-RPC 服务（带重试等待）----

    static String callRemote(String url, String method, int id) {
        Map<String, Object> req = new HashMap<>();
        req.put("jsonrpc", "2.0");
        req.put("method", method);
        req.put("id", id);

        // 最多等待 30 秒，每隔 1 秒重试一次
        for (int i = 0; i < 30; i++) {
            try {
                byte[] payload = mapper.writeValueAsBytes(req);
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }
                byte[] respBytes = conn.getInputStream().readAllBytes();
                @SuppressWarnings("unchecked")
                Map<String, Object> resp = mapper.readValue(respBytes, Map.class);
                Object result = resp.get("result");
                return result != null ? result.toString() : "错误: " + resp.get("error");
            } catch (Exception e) {
                System.out.printf("（等待服务就绪 %ds）\r", i + 1);
                try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
        return "等待超时，服务未就绪: " + url;
    }

    static String helloPage(String lang, String color, String apiURL) {
        return """
<!DOCTYPE html>
<html lang="zh">
<head>
<meta charset="UTF-8">
<title>Hello World - """ + lang + """
</title>
<style>
  body { font-family: Arial, sans-serif; background: #f5f5f5; display: flex; justify-content: center; padding: 40px; }
  .card { background: #fff; border-radius: 12px; box-shadow: 0 4px 16px rgba(0,0,0,.1); padding: 40px; max-width: 560px; width: 100%; }
  h1 { color: """ + color + """
; margin-top: 0; }
  .item { display: flex; align-items: center; gap: 12px; padding: 14px 0; border-bottom: 1px solid #eee; }
  .item:last-child { border-bottom: none; }
  .badge { background: """ + color + """
; color: #fff; border-radius: 6px; padding: 4px 10px; font-size: 13px; white-space: nowrap; }
  .msg { color: #333; font-size: 15px; }
  .loading { color: #aaa; font-style: italic; }
</style>
</head>
<body>
<div class="card">
  <h1>🌍 Hello World — """ + lang + """
</h1>
  <div id="results"><div class="loading">正在调用各语言服务...</div></div>
</div>
<script>
fetch('""" + apiURL + """
')
  .then(r => r.json())
  .then(data => {
    const labels = { go: 'GoLang', php: 'PHP', java: 'Java' };
    const colors = { go: '#00ADD8', php: '#8892BF', java: '#ED8B00' };
    document.getElementById('results').innerHTML = Object.entries(data)
      .map(([k, v]) => `<div class="item">
        <span class="badge" style="background:${colors[k]}">${labels[k]}</span>
        <span class="msg">${v}</span>
      </div>`)
      .join('');
  })
  .catch(e => {
    document.getElementById('results').innerHTML = '<div class="msg" style="color:red">加载失败: ' + e + '</div>';
  });
</script>
</body>
</html>""";
    }

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  Hello World - Java 示例");
        System.out.println("========================================");

        // 1. 启动本地 Java JSON-RPC 服务
        startJavaServer();

        // 2. 输出本地 Java 的问候
        System.out.println("\n[Java 本地] Hello world, I am JAVA");

        // 3. 调用 PHP 服务（端口 8092）
        System.out.print("[Java 调用 PHP] 正在调用 PHP 服务... ");
        System.out.println(callRemote("http://localhost:8092/jsonrpc", "hello.sayHello", 1));

        // 4. 调用 Go 服务（端口 8093）
        System.out.print("[Java 调用 Go] 正在调用 Go 服务... ");
        System.out.println(callRemote("http://localhost:8093/jsonrpc", "hello.sayHello", 2));

        System.out.println("\n[Java] 示例运行完毕，服务继续运行中（Ctrl+C 退出）...");
        Thread.currentThread().join();
    }
}
