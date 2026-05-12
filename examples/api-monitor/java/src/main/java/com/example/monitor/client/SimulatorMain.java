package com.example.monitor.client;

import com.example.monitor.config.AppConfig;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * API 监控模拟客户端。
 *
 * <p>随机生成 15 个模拟接口的调用数据，通过 MonitorClient 发送到服务端。
 * 模拟接口列表、耗时区间、状态码加权概率与 PHP / Golang 版完全一致，
 * 便于交叉测试。</p>
 */
public class SimulatorMain {

    private static final DateTimeFormatter FMT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private record SimApi(String clazz, String method, String uri, double minTime, double maxTime) {}
    private record StatusWeight(int status, int weight) {}

    private static final List<SimApi> APIS = List.of(
        new SimApi("UserController", "index", "/api/user", 10, 50),
        new SimApi("UserController", "show", "/api/user/1", 20, 80),
        new SimApi("UserController", "store", "/api/user", 30, 150),
        new SimApi("OrderController", "index", "/api/order", 15, 60),
        new SimApi("OrderController", "create", "/api/order", 50, 300),
        new SimApi("OrderController", "pay", "/api/order/pay", 100, 2000),
        new SimApi("ProductController", "list", "/api/product", 5, 30),
        new SimApi("ProductController", "detail", "/api/product/1", 10, 40),
        new SimApi("ProductController", "search", "/api/product/search", 20, 500),
        new SimApi("AuthController", "login", "/api/auth/login", 30, 200),
        new SimApi("AuthController", "logout", "/api/auth/logout", 5, 20),
        new SimApi("ReportController", "daily", "/api/report/daily", 200, 3000),
        new SimApi("ReportController", "export", "/api/report/export", 500, 5000),
        new SimApi("CartController", "add", "/api/cart/add", 10, 60),
        new SimApi("CartController", "list", "/api/cart", 8, 35)
    );

    private static final List<StatusWeight> STATUS_WEIGHTS = List.of(
        new StatusWeight(200, 85),
        new StatusWeight(201, 5),
        new StatusWeight(400, 3),
        new StatusWeight(401, 2),
        new StatusWeight(404, 2),
        new StatusWeight(500, 3)
    );

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  API 监控 - Java 模拟客户端");
        System.out.println("========================================");

        String configPath = args.length > 0 ? args[0] : "config.yaml";
        AppConfig cfg = AppConfig.load(configPath);

        System.out.printf("目标: UDP %s:%d%n", cfg.monitor.udpHost, cfg.monitor.udpPort);
        System.out.printf("项目: %s%n%n", cfg.monitor.project);

        MonitorClient client = new MonitorClient(
            cfg.monitor.udpHost,
            cfg.monitor.udpPort,
            cfg.monitor.project,
            cfg.monitor.sampleRate,
            cfg.monitor.bufferSize
        );

        AtomicLong count = new AtomicLong(0);

        // 优雅退出
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n\n[模拟器] 收到退出信号，正在退出...");
            client.flush();
            client.close();
            System.out.printf("[模拟器] 已发送 %d 条数据，退出完成%n", count.get());
        }, "api-monitor-simulator-shutdown"));

        System.out.println("开始发送模拟数据（Ctrl+C 停止）...\n");

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        while (!Thread.currentThread().isInterrupted()) {
            int batch = rnd.nextInt(5) + 1;
            for (int i = 0; i < batch; i++) {
                SimApi api = APIS.get(rnd.nextInt(APIS.size()));
                double duration = api.minTime + rnd.nextDouble() * (api.maxTime - api.minTime);
                int status = randomStatus(rnd);

                Map<String, Object> params = new LinkedHashMap<>();
                params.put("page", rnd.nextInt(10) + 1);
                params.put("id", rnd.nextInt(1000) + 1);

                Map<String, Object> response = new LinkedHashMap<>();
                if (status < 400) {
                    response.put("code", 0);
                    response.put("msg", "ok");
                } else {
                    response.put("code", 1);
                    response.put("msg", "error");
                }

                client.report(api.clazz, api.method, api.uri, status, duration, params, response);
                long c = count.incrementAndGet();

                String statusColor = status < 400 ? "\u001B[32m" : "\u001B[31m";
                System.out.printf("[%s] #%d %s%d\u001B[0m %s.%s %.1fms%n",
                    LocalTime.now().format(FMT_TIME), c, statusColor, status,
                    api.clazz, api.method, duration);
            }

            client.flush();

            try {
                Thread.sleep(100 + rnd.nextInt(900));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private static int randomStatus(ThreadLocalRandom rnd) {
        int total = 0;
        for (StatusWeight w : STATUS_WEIGHTS) total += w.weight;
        int r = rnd.nextInt(total) + 1;
        int sum = 0;
        for (StatusWeight w : STATUS_WEIGHTS) {
            sum += w.weight;
            if (r <= sum) return w.status;
        }
        return 200;
    }
}
