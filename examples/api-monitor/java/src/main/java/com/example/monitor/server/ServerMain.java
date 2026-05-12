package com.example.monitor.server;

import com.example.monitor.config.AppConfig;
import com.example.monitor.server.http.HttpController;
import com.example.monitor.server.logger.MonitorLogger;
import com.example.monitor.server.storage.MemoryStorage;
import com.example.monitor.server.storage.StorageInterface;
import com.example.monitor.server.udp.UdpReceiver;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

/**
 * API 监控服务端主入口。
 *
 * <p>启动流程：
 * <ol>
 *   <li>加载 config.yaml</li>
 *   <li>初始化内存存储</li>
 *   <li>初始化日志持久化（可选）</li>
 *   <li>启动 UDP 接收器</li>
 *   <li>启动 HTTP 服务（API + 仪表盘）</li>
 *   <li>注册优雅退出钩子</li>
 * </ol></p>
 */
public class ServerMain {

    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("  API 性能监控 - Java 服务端");
        System.out.println("========================================");

        // 1. 读取配置
        String configPath = args.length > 0 ? args[0] : "config.yaml";
        AppConfig cfg = AppConfig.load(configPath);

        // 2. 初始化存储
        StorageInterface storage = new MemoryStorage();
        System.out.println("[监控] 使用内存存储");

        // 3. 初始化日志持久化
        Path logDir = resolveRuntimeDir().resolve("logs").resolve("monitor");
        Files.createDirectories(logDir);
        MonitorLogger logger = new MonitorLogger(storage, logDir.toString(), cfg.server.logInterval);

        // 4. 初始化 UDP 接收器
        UdpReceiver udpReceiver = new UdpReceiver(cfg.server.udpPort, storage);

        // 5. 确定 public 目录
        Path publicDir = resolvePublicDir();

        // 6. 初始化 HTTP 控制器
        HttpController controller = new HttpController(storage, logger, cfg.server.password, publicDir.toString());

        // 7. 启动 UDP 接收器
        udpReceiver.start();

        // 8. 启动日志持久化
        if (cfg.server.logEnable) {
            logger.start();
        }

        // 9. 启动 HTTP 服务（JDK 内置）
        HttpServer server = HttpServer.create(new InetSocketAddress(cfg.server.httpPort), 0);
        controller.registerRoutes(server);
        server.setExecutor(Executors.newFixedThreadPool(Math.max(8, Runtime.getRuntime().availableProcessors() * 2)));
        server.start();

        System.out.println("[监控] HTTP 服务启动，端口 " + cfg.server.httpPort);
        System.out.println("[监控] UDP 接收端口 " + cfg.server.udpPort);
        System.out.println("[监控] 仪表盘地址: http://localhost:" + cfg.server.httpPort);

        // 10. 优雅退出
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[监控] 收到退出信号，正在优雅退出...");
            udpReceiver.stop();
            System.out.println("[监控] UDP 接收器已停止");
            if (cfg.server.logEnable) {
                logger.stop();
                System.out.println("[监控] 日志持久化已停止，最后一次导出完成");
            }
            server.stop(2);
            System.out.println("[监控] HTTP 服务已停止");
        }, "api-monitor-shutdown"));
    }

    /** 按项目根（当前工作目录）下的 runtime 目录为优先。 */
    private static Path resolveRuntimeDir() {
        return Path.of("runtime");
    }

    /**
     * 查找 public 目录。优先当前目录下 public/，然后尝试 jar 所在目录下的 public/，
     * 最后 fallback 到项目源码目录（开发环境）。
     */
    private static Path resolvePublicDir() {
        // 1. 工作目录下
        Path p = Path.of("public");
        if (Files.exists(p.resolve("index.html"))) return p;

        // 2. 源码开发目录
        Path dev = Path.of("examples/api-monitor/java/public");
        if (Files.exists(dev.resolve("index.html"))) return dev;

        // 3. 兜底：返回工作目录下的 public，HttpController 会处理 404
        return p;
    }
}
