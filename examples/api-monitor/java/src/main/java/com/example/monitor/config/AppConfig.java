package com.example.monitor.config;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 应用配置加载器。
 *
 * <p>对应 YAML 结构：
 * <pre>
 * monitor:
 *   enabled: true
 *   udp_host: "127.0.0.1"
 *   udp_port: 9501
 *   project: "demo-project"
 *   sample_rate: 1.0
 *   buffer_size: 10
 *   exclude: ["/health"]
 * server:
 *   http_port: 8095
 *   udp_port: 9501
 *   storage_driver: "memory"
 *   password: "888888"
 *   log:
 *     enable: true
 *     interval: 60
 * </pre></p>
 */
public class AppConfig {

    public final MonitorConfig monitor = new MonitorConfig();
    public final ServerConfig server = new ServerConfig();

    public static class MonitorConfig {
        public boolean enabled = true;
        public String udpHost = "127.0.0.1";
        public int udpPort = 9501;
        public String project = "demo-project";
        public double sampleRate = 1.0;
        public int bufferSize = 10;
        public List<String> exclude = List.of("/health", "/favicon.ico");
    }

    public static class ServerConfig {
        public int httpPort = 8095;
        public int udpPort = 9501;
        public String storageDriver = "memory";
        public String password = "888888";
        public boolean logEnable = true;
        public int logInterval = 60;
    }

    /**
     * 从 YAML 配置文件加载配置，文件不存在或解析失败时使用默认值。
     *
     * @param path 配置文件路径（相对或绝对）
     * @return 配置对象（永不返回 null）
     */
    @SuppressWarnings("unchecked")
    public static AppConfig load(String path) {
        AppConfig cfg = new AppConfig();
        Path p = Path.of(path);
        if (!Files.exists(p)) {
            System.out.println("[监控] 配置文件 " + path + " 不存在，使用默认配置");
            return cfg;
        }

        try (InputStream in = Files.newInputStream(p)) {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(in);
            if (root == null) return cfg;

            Object monitorRaw = root.get("monitor");
            if (monitorRaw instanceof Map) {
                Map<String, Object> m = (Map<String, Object>) monitorRaw;
                cfg.monitor.enabled = asBool(m.get("enabled"), cfg.monitor.enabled);
                cfg.monitor.udpHost = asStr(m.get("udp_host"), cfg.monitor.udpHost);
                cfg.monitor.udpPort = asInt(m.get("udp_port"), cfg.monitor.udpPort);
                cfg.monitor.project = asStr(m.get("project"), cfg.monitor.project);
                cfg.monitor.sampleRate = asDouble(m.get("sample_rate"), cfg.monitor.sampleRate);
                cfg.monitor.bufferSize = asInt(m.get("buffer_size"), cfg.monitor.bufferSize);
                Object excludeRaw = m.get("exclude");
                if (excludeRaw instanceof List) {
                    List<Object> list = (List<Object>) excludeRaw;
                    cfg.monitor.exclude = list.stream()
                        .filter(o -> o != null)
                        .map(Object::toString)
                        .toList();
                }
            }

            Object serverRaw = root.get("server");
            if (serverRaw instanceof Map) {
                Map<String, Object> s = (Map<String, Object>) serverRaw;
                cfg.server.httpPort = asInt(s.get("http_port"), cfg.server.httpPort);
                cfg.server.udpPort = asInt(s.get("udp_port"), cfg.server.udpPort);
                cfg.server.storageDriver = asStr(s.get("storage_driver"), cfg.server.storageDriver);
                cfg.server.password = asStr(s.get("password"), cfg.server.password);
                Object logRaw = s.get("log");
                if (logRaw instanceof Map) {
                    Map<String, Object> l = (Map<String, Object>) logRaw;
                    cfg.server.logEnable = asBool(l.get("enable"), cfg.server.logEnable);
                    cfg.server.logInterval = asInt(l.get("interval"), cfg.server.logInterval);
                }
            }
        } catch (Exception e) {
            System.err.println("[监控] 配置文件解析失败: " + e.getMessage() + "，使用默认配置");
        }
        return cfg;
    }

    private static String asStr(Object o, String def) {
        return o == null ? def : o.toString();
    }

    private static int asInt(Object o, int def) {
        if (o instanceof Number) return ((Number) o).intValue();
        if (o instanceof String) {
            try { return Integer.parseInt((String) o); } catch (NumberFormatException e) { return def; }
        }
        return def;
    }

    private static double asDouble(Object o, double def) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o instanceof String) {
            try { return Double.parseDouble((String) o); } catch (NumberFormatException e) { return def; }
        }
        return def;
    }

    private static boolean asBool(Object o, boolean def) {
        if (o instanceof Boolean) return (Boolean) o;
        if (o instanceof String) return Boolean.parseBoolean((String) o);
        return def;
    }
}
