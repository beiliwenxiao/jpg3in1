package com.example.monitor.client;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.List;

/**
 * JDK HttpServer Filter：自动采集 HTTP 请求的性能数据并通过 MonitorClient 上报。
 *
 * <p>使用示例：
 * <pre>
 * MonitorClient client = new MonitorClient("127.0.0.1", 9501, "my-project", 1.0, 10);
 * MonitorFilter filter = new MonitorFilter(client, List.of("/health"), true);
 * server.createContext("/api", handler).getFilters().add(filter);
 * </pre></p>
 *
 * <p>规则：
 * <ul>
 *   <li>enabled 为 false 直接放行</li>
 *   <li>exclude 列表（前缀匹配）的请求不采集</li>
 *   <li>从 URI 路径自动解析 class（首字母大写 + Controller 后缀）和 method</li>
 * </ul></p>
 */
public class MonitorFilter extends Filter {

    private final MonitorClient client;
    private final List<String> exclude;
    private final boolean enabled;

    public MonitorFilter(MonitorClient client, List<String> exclude, boolean enabled) {
        this.client = client;
        this.exclude = exclude == null ? List.of() : exclude;
        this.enabled = enabled;
    }

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {
        if (!enabled) {
            chain.doFilter(exchange);
            return;
        }

        String uri = exchange.getRequestURI().getPath();
        if (uri == null || uri.isEmpty()) uri = "/";

        if (isExcluded(uri, exclude)) {
            chain.doFilter(exchange);
            return;
        }

        long startNanos = System.nanoTime();
        try {
            chain.doFilter(exchange);
        } finally {
            double duration = (System.nanoTime() - startNanos) / 1_000_000.0;
            int status = exchange.getResponseCode();
            if (status < 0) status = 200;
            String[] action = parseActionFromUri(uri);
            client.report(action[0], action[1], uri, status, duration, null, null);
        }
    }

    @Override
    public String description() {
        return "API Monitor Filter";
    }

    /** URI 是否在排除列表中（前缀匹配）。 */
    public static boolean isExcluded(String uri, List<String> exclude) {
        if (exclude == null) return false;
        for (String pattern : exclude) {
            if (uri.startsWith(pattern)) return true;
        }
        return false;
    }

    /**
     * 从 URI 路径提取 class 和 method。
     * 规则：第一段首字母大写 + Controller 后缀作为 class，第二段作为 method（默认 index）。
     */
    public static String[] parseActionFromUri(String uri) {
        String path = uri == null ? "" : uri.replaceAll("^/+|/+$", "");
        if (path.isEmpty()) return new String[]{"IndexController", "index"};

        String[] parts = path.split("/", 3);
        String className = parts[0];
        if (className.isEmpty()) className = "Index";
        else className = Character.toUpperCase(className.charAt(0)) + className.substring(1);
        if (!className.endsWith("Controller")) className += "Controller";

        String method = "index";
        if (parts.length > 1 && !parts[1].isEmpty()) method = parts[1];

        return new String[]{className, method};
    }
}
