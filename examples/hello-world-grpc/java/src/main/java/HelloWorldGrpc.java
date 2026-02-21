/**
 * Hello World gRPC - Java 示例
 * gRPC 服务端（端口 9091）+ HTTP 展示（端口 8091）
 * 通过 gRPC 调用 Go（9093）的 Greeter.SayHello
 */

import com.example.grpc.hello.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class HelloWorldGrpc {

    private static final ObjectMapper mapper = new ObjectMapper();

    // ---- gRPC 服务端实现 ----
    static class GreeterImpl extends GreeterGrpc.GreeterImplBase {
        @Override
        public void sayHello(HelloRequest req, StreamObserver<HelloReply> observer) {
            String name = req.getName().isEmpty() ? "world" : req.getName();
            System.out.println("[Java gRPC] 收到请求: name=" + name);
            observer.onNext(HelloReply.newBuilder()
                    .setMessage("Hello " + name + ", I am JAVA (gRPC)")
                    .build());
            observer.onCompleted();
        }
    }

    // ---- gRPC 客户端调用 ----
    static String callGrpc(String host, int port, String name) {
        ManagedChannel ch = null;
        try {
            ch = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
            GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(ch);
            HelloReply reply = stub.sayHello(
                    HelloRequest.newBuilder().setName(name != null ? name : "").build());
            return reply.getMessage();
        } catch (Exception e) {
            return "调用失败: " + e.getMessage();
        } finally {
            if (ch != null) try { ch.shutdown().awaitTermination(3, TimeUnit.SECONDS); } catch (Exception ignored) {}
        }
    }

    // ---- HTTP 服务 ----
    static void startHttpServer(int httpPort) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(httpPort), 0);

        server.createContext("/hello", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String name = null;
            if (query != null) for (String p : query.split("&")) {
                String[] kv = p.split("=", 2);
                if ("name".equals(kv[0]) && kv.length > 1 && !kv[1].isEmpty())
                    name = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
            String displayName = name != null ? name : "world";
            String json = mapper.writeValueAsString(Map.of(
                    "java", "Hello " + displayName + ", I am JAVA (gRPC)",
                    "go", callGrpc("localhost", 9093, displayName)));
            byte[] resp = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(resp); }
        });

        server.createContext("/", exchange -> {
            byte[] html = helloPage("Java", "#ED8B00", "/hello").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, html.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(html); }
        });

        server.start();
        System.out.println("[Java] HTTP 服务启动，端口 " + httpPort);
        System.out.println("[Java] 浏览器访问: http://localhost:" + httpPort);
    }

    static String helloPage(String lang, String color, String apiURL) {
        return """
<!DOCTYPE html><html lang="zh"><head><meta charset="UTF-8"><title>Hello World gRPC - %s</title>
<style>body{font-family:Arial,sans-serif;background:#f5f5f5;display:flex;justify-content:center;padding:40px}
.card{background:#fff;border-radius:12px;box-shadow:0 4px 16px rgba(0,0,0,.1);padding:40px;max-width:560px;width:100%%}
h1{color:%s;margin-top:0}.tag{background:#4CAF50;color:#fff;border-radius:4px;padding:2px 8px;font-size:11px;margin-left:8px}
.item{display:flex;align-items:center;gap:12px;padding:14px 0;border-bottom:1px solid #eee}.item:last-child{border-bottom:none}
.badge{color:#fff;border-radius:6px;padding:4px 10px;font-size:13px;white-space:nowrap}.msg{color:#333;font-size:15px}
.loading{color:#aaa;font-style:italic}</style></head><body>
<div class="card"><h1>🌍 Hello World gRPC — %s <span class="tag">gRPC</span></h1>
<div id="results"><div class="loading">正在通过 gRPC 调用各语言服务...</div></div></div>
<script>fetch('%s').then(r=>r.json()).then(data=>{
const labels={go:'GoLang',java:'Java'};const colors={go:'#00ADD8',java:'#ED8B00'};
document.getElementById('results').innerHTML=Object.entries(data).map(([k,v])=>`<div class="item">
<span class="badge" style="background:${colors[k]||'#666'}">${labels[k]||k}</span>
<span class="msg">${v}</span></div>`).join('');
}).catch(e=>{document.getElementById('results').innerHTML='<div class="msg" style="color:red">加载失败: '+e+'</div>';});
</script></body></html>""".formatted(lang, color, lang, apiURL);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  Hello World gRPC - Java 示例");
        System.out.println("========================================");

        // 1. 启动 gRPC 服务端
        Server grpcServer = ServerBuilder.forPort(9091)
                .addService(new GreeterImpl()).build().start();
        System.out.println("[Java] gRPC 服务启动，端口 9091");

        // 2. 启动 HTTP 服务
        startHttpServer(8091);

        // 3. 后台调用
        new Thread(() -> {
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            System.out.println("\n[Java 本地 gRPC] Hello world, I am JAVA (gRPC)");
            System.out.println("[Java → Go gRPC] " + callGrpc("localhost", 9093, "Java"));
            System.out.println("\n[Java] 服务运行中（Ctrl+C 退出）...");
        }).start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Java] 正在关闭...");
            grpcServer.shutdown();
        }));
        grpcServer.awaitTermination();
    }
}
