/**
 * Hello World - Java 示例
 * 启动一个 JSON-RPC 服务（端口 8091），同时调用 PHP（8092）和 Go（8093）的服务
 *
 * 使用框架的 RpcProxy 进行跨语言调用，服务地址通过 config.yaml 配置
 * 无需 Spring Boot，使用 JDK 内置 HttpServer 即可独立运行
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.client.RpcProxy;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class HelloWorld {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static RpcProxy rpc;

    // ---- 启动本地 JSON-RPC 服务 ----

    static void startJavaServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

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

        // /hello 接口：通过 RpcProxy 调用其他语言（传递 name 参数）
        server.createContext("/hello", exchange -> {
            // 从 query string 获取 name 参数，如 /hello?name=Kiro
            String query = exchange.getRequestURI().getQuery();
            String name = null;
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] kv = param.split("=", 2);
                    if ("name".equals(kv[0]) && kv.length > 1 && !kv[1].isEmpty()) {
                        name = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                    }
                }
            }
            Map<String, String> rpcParams = (name != null) ? Map.of("name", name) : null;

            String javaMsg = "Hello " + (name != null ? name : "world") + ", I am JAVA";
            String phpMsg, goMsg;
            try {
                phpMsg = rpc.call("php-service", "hello.sayHello", rpcParams, String.class);
            } catch (Exception e) {
                phpMsg = "调用失败: " + e.getMessage();
            }
            try {
                goMsg = rpc.call("go-service", "hello.sayHello", rpcParams, String.class);
            } catch (Exception e) {
                goMsg = "调用失败: " + e.getMessage();
            }
            String json = String.format(
                "{\"java\":\"%s\",\"php\":\"%s\",\"go\":\"%s\"}",
                javaMsg.replace("\"", "\\\""),
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
        System.out.println("[Java] JSON-RPC 服务启动，监听端口 " + port + "...");
        System.out.println("[Java] 浏览器访问: http://localhost:" + port);
    }

    @SuppressWarnings("unchecked")
    static String handleJsonRpc(String requestBody) {
        try {
            Map<String, Object> req = mapper.readValue(requestBody, Map.class);
            Object id = req.get("id");
            String method = (String) req.getOrDefault("method", "");

            Object result;
            if ("hello.sayHello".equals(method)) {
                // 支持 name 参数：params 可以是 {"name":"xxx"} 或 ["xxx"] 或 null
                String name = "world";
                Object params = req.get("params");
                if (params instanceof Map) {
                    Object n = ((Map<?,?>) params).get("name");
                    if (n != null && !n.toString().isEmpty()) name = n.toString();
                } else if (params instanceof java.util.List<?> list && !list.isEmpty()) {
                    Object n = list.get(0);
                    if (n != null && !n.toString().isEmpty()) name = n.toString();
                }
                result = "Hello " + name + ", I am JAVA";
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

        // 1. 从配置文件加载远程服务定义
        String configPath = "src/main/resources/config.yaml";
        // 兼容从 jar 包外或项目根目录运行
        if (!new File(configPath).exists()) {
            configPath = "examples/hello-world/java/src/main/resources/config.yaml";
        }
        rpc = RpcProxy.fromConfig(configPath);

        // 2. 启动本地 Java JSON-RPC 服务
        startJavaServer(8091);

        // 3. 输出本地 Java 的问候
        System.out.println("\n[Java 本地] Hello world, I am JAVA");

        // 4. 通过 RpcProxy 调用 PHP 服务（带 name 参数）
        System.out.print("[Java → PHP] ");
        try {
            System.out.println(rpc.call("php-service", "hello.sayHello",
                    Map.of("name", "Java"), String.class));
        } catch (Exception e) {
            System.out.println("调用失败: " + e.getMessage());
        }

        // 5. 通过 RpcProxy 调用 Go 服务（带 name 参数）
        System.out.print("[Java → Go] ");
        try {
            System.out.println(rpc.call("go-service", "hello.sayHello",
                    Map.of("name", "Java"), String.class));
        } catch (Exception e) {
            System.out.println("调用失败: " + e.getMessage());
        }

        System.out.println("\n[Java] 示例运行完毕，服务继续运行中（Ctrl+C 退出）...");
        Thread.currentThread().join();
    }
}
