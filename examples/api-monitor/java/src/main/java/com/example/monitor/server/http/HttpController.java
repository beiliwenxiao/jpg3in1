package com.example.monitor.server.http;

import com.example.monitor.server.logger.MonitorLogger;
import com.example.monitor.server.model.RecordItem;
import com.example.monitor.server.storage.MemoryStorage;
import com.example.monitor.server.storage.StorageInterface;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * HTTP API 控制器。
 *
 * <p>提供与 PHP 版 MonitorController 完全兼容的 RESTful API 接口。
 * 所有查询自动合并内存实时数据（当前 session）与日志历史数据（排除当前 session）。</p>
 */
public class HttpController {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter FMT_DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final StorageInterface storage;
    private final MonitorLogger logger;
    private final String password;
    private final Path publicDir;

    public HttpController(StorageInterface storage, MonitorLogger logger, String password, String publicDirPath) {
        this.storage = storage;
        this.logger = logger;
        this.password = password == null ? "" : password;
        this.publicDir = Path.of(publicDirPath);
    }

    /** 注册所有路由到 HttpServer。 */
    public void registerRoutes(HttpServer server) {
        server.createContext("/api/dashboard", methodGuard("GET", this::dashboard));
        server.createContext("/api/tree", methodGuard("GET", this::tree));
        server.createContext("/api/detail", methodGuard("GET", this::detail));
        server.createContext("/api/ranking/slow", methodGuard("GET", this::rankingSlow));
        server.createContext("/api/ranking/count", methodGuard("GET", this::rankingCount));
        server.createContext("/api/realtime", methodGuard("GET", this::realtime));
        server.createContext("/api/trend", methodGuard("GET", this::trend));
        server.createContext("/api/search", methodGuard("GET", this::search));
        server.createContext("/api/dates", methodGuard("GET", this::dates));
        server.createContext("/api/records", methodGuard("GET", this::records));
        server.createContext("/api/login", this::login);
        server.createContext("/api/check-login", methodGuard("GET", this::checkLogin));
        // 根路径：仪表盘
        server.createContext("/", this::index);
    }

    // ========================================================================
    // 请求参数辅助
    // ========================================================================

    private static Map<String, String> parseQuery(URI uri) {
        Map<String, String> result = new HashMap<>();
        String q = uri.getRawQuery();
        if (q == null || q.isEmpty()) return result;
        for (String pair : q.split("&")) {
            int idx = pair.indexOf('=');
            String k = idx > 0 ? pair.substring(0, idx) : pair;
            String v = idx > 0 && idx < pair.length() - 1 ? pair.substring(idx + 1) : "";
            result.put(URLDecoder.decode(k, StandardCharsets.UTF_8),
                       URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return result;
    }

    private static String getParam(Map<String, String> query, String key, String def) {
        String v = query.get(key);
        return (v == null || v.isEmpty()) ? def : v;
    }

    private static int getIntParam(Map<String, String> query, String key, int def) {
        String v = query.get(key);
        if (v == null || v.isEmpty()) return def;
        try { return Integer.parseInt(v); } catch (NumberFormatException e) { return def; }
    }

    // ========================================================================
    // 响应辅助
    // ========================================================================

    private HttpHandler methodGuard(String method, HttpHandler next) {
        return exchange -> {
            if (!method.equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            try {
                next.handle(exchange);
            } catch (Exception e) {
                e.printStackTrace();
                writeJsonResponse(exchange, 500, Map.of("code", 1, "message", "Internal error"));
            }
        };
    }

    private void writeJson(HttpExchange exchange, Object data) throws IOException {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("code", 0);
        resp.put("data", data);
        writeJsonResponse(exchange, 200, resp);
    }

    private void writeJsonResponse(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = MAPPER.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String getCookie(HttpExchange exchange, String name) {
        List<String> cookies = exchange.getRequestHeaders().get("Cookie");
        if (cookies == null) return "";
        for (String cookieLine : cookies) {
            for (String pair : cookieLine.split(";")) {
                pair = pair.trim();
                int idx = pair.indexOf('=');
                if (idx > 0 && pair.substring(0, idx).equals(name)) {
                    return pair.substring(idx + 1);
                }
            }
        }
        return "";
    }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String expectedToken() {
        return md5(password + "_monitor_salt");
    }

    // ========================================================================
    // API 接口实现
    // ========================================================================

    /** 仪表盘概览：合并内存实时数据与日志历史数据。 */
    private void dashboard(HttpExchange exchange) throws IOException {
        Map<String, String> q = parseQuery(exchange.getRequestURI());
        String date = getParam(q, "date", LocalDate.now().format(FMT_DAY));

        Map<String, Object> live = storage.getDashboard(date);
        Map<String, Object> logData = getDashboardFromLog(date);

        int logCount = toInt(logData.get("total_count"));
        int liveCount = toInt(live.get("total_count"));

        if (logCount > 0 && liveCount > 0) {
            int totalCount = logCount + liveCount;
            int totalSuccess = toInt(logData.get("success")) + toInt(live.get("success"));
            int totalFail = toInt(logData.get("fail")) + toInt(live.get("fail"));
            double logTime = toDouble(logData.get("avg_time")) * logCount;
            double liveTime = toDouble(live.get("avg_time")) * liveCount;

            Map<String, Object> merged = new LinkedHashMap<>();
            merged.put("date", date);
            merged.put("total_count", totalCount);
            merged.put("success", totalSuccess);
            merged.put("fail", totalFail);
            merged.put("success_rate", totalCount > 0 ? Math.round(totalSuccess * 10000.0 / totalCount) / 100.0 : 0);
            merged.put("avg_time", totalCount > 0 ? Math.round((logTime + liveTime) * 100.0 / totalCount) / 100.0 : 0);
            writeJson(exchange, merged);
        } else if (logCount > 0) {
            writeJson(exchange, logData);
        } else {
            writeJson(exchange, live);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getDashboardFromLog(String date) {
        Map<String, Object> log = logger.readDayLog(date);
        if (log == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("date", date);
            empty.put("total_count", 0);
            empty.put("success", 0);
            empty.put("fail", 0);
            empty.put("success_rate", 0);
            empty.put("avg_time", 0);
            return empty;
        }
        Map<String, Object> dashboard = (Map<String, Object>) log.get("dashboard");
        if (dashboard == null) dashboard = new LinkedHashMap<>();
        if (!dashboard.containsKey("date") || "".equals(dashboard.get("date"))) {
            dashboard.put("date", date);
        }
        return dashboard;
    }

    /** 树形菜单：合并内存 tree 与日志 tree（union）。 */
    @SuppressWarnings("unchecked")
    private void tree(HttpExchange exchange) throws IOException {
        Map<String, String> q = parseQuery(exchange.getRequestURI());
        String date = q.get("date");

        Map<String, Map<String, Map<String, String>>> tree = storage.getTree();

        if (date != null && !date.isEmpty()) {
            Map<String, Object> log = logger.readDayLog(date);
            if (log != null) {
                Object logTree = log.get("tree");
                if (logTree instanceof Map) {
                    for (var pe : ((Map<String, Object>) logTree).entrySet()) {
                        if (!(pe.getValue() instanceof Map)) continue;
                        Map<String, Object> classes = (Map<String, Object>) pe.getValue();
                        Map<String, Map<String, String>> treeClasses =
                            tree.computeIfAbsent(pe.getKey(), k -> new LinkedHashMap<>());
                        for (var ce : classes.entrySet()) {
                            if (!(ce.getValue() instanceof Map)) continue;
                            Map<String, Object> methods = (Map<String, Object>) ce.getValue();
                            Map<String, String> treeMethods =
                                treeClasses.computeIfAbsent(ce.getKey(), k -> new LinkedHashMap<>());
                            for (var me : methods.entrySet()) {
                                treeMethods.putIfAbsent(me.getKey(), me.getValue() == null ? "" : me.getValue().toString());
                            }
                        }
                    }
                }
            }
        }
        writeJson(exchange, tree);
    }

    /** 接口详情：合并内存 periods 与日志 periods。 */
    @SuppressWarnings("unchecked")
    private void detail(HttpExchange exchange) throws IOException {
        Map<String, String> q = parseQuery(exchange.getRequestURI());
        String project = getParam(q, "project", "");
        String clazz = getParam(q, "class", "");
        String method = getParam(q, "method", "");
        String date = getParam(q, "date", LocalDate.now().format(FMT_DAY));
        String granularity = getParam(q, "granularity", "minute");

        List<Map<String, Object>> live = storage.getDetail(project, clazz, method, date, granularity);
        List<Map<String, Object>> logDetails = getDetailFromLog(project, clazz, method, date);

        Map<String, Object> livePeriods = live.isEmpty() ? null : (Map<String, Object>) live.get(0).get("periods");
        Map<String, Object> logPeriods = logDetails.isEmpty() ? null : (Map<String, Object>) logDetails.get(0).get("periods");

        if ((livePeriods == null || livePeriods.isEmpty()) && (logPeriods == null || logPeriods.isEmpty())) {
            writeJson(exchange, Collections.emptyList());
            return;
        }

        Map<String, Map<String, Object>> merged = new TreeMap<>();
        if (logPeriods != null) {
            for (var e : logPeriods.entrySet()) {
                if (e.getValue() instanceof Map) {
                    merged.put(e.getKey(), new LinkedHashMap<>((Map<String, Object>) e.getValue()));
                }
            }
        }
        if (livePeriods != null) {
            for (var e : livePeriods.entrySet()) {
                if (!(e.getValue() instanceof Map)) continue;
                String period = e.getKey();
                Map<String, Object> liveStats = (Map<String, Object>) e.getValue();
                Map<String, Object> existing = merged.get(period);
                if (existing == null) {
                    merged.put(period, new LinkedHashMap<>(liveStats));
                } else {
                    int totalCount = toInt(existing.get("count")) + toInt(liveStats.get("count"));
                    int totalSuccess = toInt(existing.get("success")) + toInt(liveStats.get("success"));
                    int totalFail = toInt(existing.get("fail")) + toInt(liveStats.get("fail"));
                    double totalTime = toDouble(existing.get("total_time")) + toDouble(liveStats.get("total_time"));
                    double mx = Math.max(toDouble(existing.get("max_time")), toDouble(liveStats.get("max_time")));
                    double mn = Math.min(
                        existing.containsKey("min_time") ? toDouble(existing.get("min_time")) : Double.MAX_VALUE,
                        liveStats.containsKey("min_time") ? toDouble(liveStats.get("min_time")) : Double.MAX_VALUE
                    );
                    if (mn == Double.MAX_VALUE) mn = 0;

                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("count", totalCount);
                    m.put("success", totalSuccess);
                    m.put("fail", totalFail);
                    m.put("total_time", totalTime);
                    m.put("max_time", mx);
                    m.put("min_time", mn);
                    m.put("avg_time", totalCount > 0 ? Math.round(totalTime * 100.0 / totalCount) / 100.0 : 0);
                    m.put("success_rate", totalCount > 0 ? Math.round(totalSuccess * 10000.0 / totalCount) / 100.0 : 0);
                    merged.put(period, m);
                }
            }
        }

        Map<String, Object> base = !live.isEmpty() ? live.get(0) : logDetails.get(0);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("project", base.get("project"));
        item.put("class", base.get("class"));
        item.put("method", base.get("method"));
        item.put("uri", base.get("uri"));
        item.put("periods", merged);
        writeJson(exchange, List.of(item));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getDetailFromLog(String project, String clazz, String method, String date) {
        Map<String, Object> log = logger.readMinuteLog(date);
        if (log == null) return Collections.emptyList();
        List<Map<String, Object>> details = (List<Map<String, Object>>) log.get("details");
        if (details == null) return Collections.emptyList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> detail : details) {
            if (!project.isEmpty() && !project.equals(String.valueOf(detail.get("project")))) continue;
            if (!clazz.isEmpty() && !clazz.equals(String.valueOf(detail.get("class")))) continue;
            if (!method.isEmpty() && !method.equals(String.valueOf(detail.get("method")))) continue;
            result.add(detail);
        }
        return result;
    }

    /** 慢速接口排行（内存 + 日志，按 avg_time 降序）。 */
    @SuppressWarnings("unchecked")
    private void rankingSlow(HttpExchange exchange) throws IOException {
        Map<String, String> q = parseQuery(exchange.getRequestURI());
        String date = getParam(q, "date", LocalDate.now().format(FMT_DAY));
        int limit = getIntParam(q, "limit", 50);

        List<Map<String, Object>> live = storage.getSlowRanking(date, limit);
        List<Map<String, Object>> logRanking = getSlowRankingFromLog(date);

        writeJson(exchange, mergeRanking(logRanking, live, "avg_time", limit));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getSlowRankingFromLog(String date) {
        Map<String, Object> log = logger.readDayLog(date);
        if (log == null) return Collections.emptyList();
        Object r = log.get("slow_ranking");
        return r instanceof List ? (List<Map<String, Object>>) r : Collections.emptyList();
    }

    /** 访问次数排行（内存 + 日志，按 count 降序）。 */
    @SuppressWarnings("unchecked")
    private void rankingCount(HttpExchange exchange) throws IOException {
        Map<String, String> q = parseQuery(exchange.getRequestURI());
        String date = getParam(q, "date", LocalDate.now().format(FMT_DAY));
        int limit = getIntParam(q, "limit", 50);

        List<Map<String, Object>> live = storage.getCountRanking(date, limit);
        List<Map<String, Object>> logRanking = getCountRankingFromLog(date);

        writeJson(exchange, mergeRanking(logRanking, live, "count", limit));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getCountRankingFromLog(String date) {
        Map<String, Object> log = logger.readDayLog(date);
        if (log == null) return Collections.emptyList();
        Object r = log.get("count_ranking");
        return r instanceof List ? (List<Map<String, Object>>) r : Collections.emptyList();
    }

    /**
     * 合并排行榜：日志（历史 session 累加）+ 内存（当前 session），按 sortField 降序。
     * 逻辑与 PHP MonitorController::mergeRanking 完全一致。
     */
    static List<Map<String, Object>> mergeRanking(
            List<Map<String, Object>> old, List<Map<String, Object>> neu, String sortField, int limit) {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        for (Map<String, Object> item : old) {
            String key = asStr(item.get("project")) + "|" + asStr(item.get("class")) + "|" + asStr(item.get("method"));
            map.put(key, new LinkedHashMap<>(item));
        }
        for (Map<String, Object> item : neu) {
            String key = asStr(item.get("project")) + "|" + asStr(item.get("class")) + "|" + asStr(item.get("method"));
            Map<String, Object> existing = map.get(key);
            if (existing == null) {
                map.put(key, new LinkedHashMap<>(item));
                continue;
            }
            int oldCount = toInt(existing.get("count"));
            int newCount = toInt(item.get("count"));
            int tc = oldCount + newCount;
            Map<String, Object> merged = new LinkedHashMap<>(item);
            merged.put("count", tc);
            if (item.containsKey("success")) {
                int s = toInt(existing.get("success")) + toInt(item.get("success"));
                int f = toInt(existing.get("fail")) + toInt(item.get("fail"));
                merged.put("success", s);
                merged.put("fail", f);
                merged.put("success_rate", tc > 0 ? Math.round(s * 10000.0 / tc) / 100.0 : 0);
            }
            if (item.containsKey("avg_time")) {
                double ot = toDouble(existing.get("avg_time")) * oldCount;
                double nt = toDouble(item.get("avg_time")) * newCount;
                merged.put("avg_time", tc > 0 ? Math.round((ot + nt) * 100.0 / tc) / 100.0 : 0);
            }
            if (item.containsKey("max_time")) {
                merged.put("max_time", Math.max(toDouble(existing.get("max_time")), toDouble(item.get("max_time"))));
            }
            map.put(key, merged);
        }
        List<Map<String, Object>> result = new ArrayList<>(map.values());
        result.sort((a, b) -> Double.compare(toDouble(b.get(sortField)), toDouble(a.get(sortField))));
        if (limit > 0 && result.size() > limit) result = new ArrayList<>(result.subList(0, limit));
        return result;
    }

    /** 实时访问量：最近 10 分钟。 */
    private void realtime(HttpExchange exchange) throws IOException {
        writeJson(exchange, storage.getRealtime());
    }

    /** 全天访问趋势（分钟级）：合并内存趋势与日志分钟数据。 */
    @SuppressWarnings("unchecked")
    private void trend(HttpExchange exchange) throws IOException {
        Map<String, String> q = parseQuery(exchange.getRequestURI());
        String date = getParam(q, "date", LocalDate.now().format(FMT_DAY));

        List<Map<String, Object>> live = storage.getDayTrend(date);

        Map<String, Object> log = logger.readMinuteLog(date);
        if (log == null) {
            writeJson(exchange, live);
            return;
        }

        // 预聚合日志中的分钟数据（所有接口累加）
        Map<String, int[]> logMinutes = new HashMap<>();
        List<Map<String, Object>> details = (List<Map<String, Object>>) log.get("details");
        if (details != null) {
            for (Map<String, Object> detail : details) {
                Map<String, Object> periods = (Map<String, Object>) detail.get("periods");
                if (periods == null) continue;
                for (var e : periods.entrySet()) {
                    if (!(e.getValue() instanceof Map)) continue;
                    Map<String, Object> stats = (Map<String, Object>) e.getValue();
                    int[] arr = logMinutes.computeIfAbsent(e.getKey(), k -> new int[3]);
                    arr[0] += toInt(stats.get("count"));
                    arr[1] += toInt(stats.get("success"));
                    arr[2] += toInt(stats.get("fail"));
                }
            }
        }
        if (logMinutes.isEmpty()) {
            writeJson(exchange, live);
            return;
        }

        // 直接累加
        for (Map<String, Object> item : live) {
            String time = (String) item.get("time");
            int[] arr = logMinutes.get(time);
            if (arr != null) {
                item.put("count", toInt(item.get("count")) + arr[0]);
                item.put("success", toInt(item.get("success")) + arr[1]);
                item.put("fail", toInt(item.get("fail")) + arr[2]);
            }
        }
        writeJson(exchange, live);
    }

    /** 搜索接口。 */
    private void search(HttpExchange exchange) throws IOException {
        Map<String, String> q = parseQuery(exchange.getRequestURI());
        String keyword = getParam(q, "keyword", "");
        String date = getParam(q, "date", LocalDate.now().format(FMT_DAY));
        writeJson(exchange, storage.search(keyword, date));
    }

    /** 可用日期列表：前后 7 天，标注是否有数据及来源。 */
    private void dates(HttpExchange exchange) throws IOException {
        String today = LocalDate.now().format(FMT_DAY);
        List<Map<String, Object>> dates = new ArrayList<>();
        for (int i = -7; i <= 7; i++) {
            String date = LocalDate.now().plusDays(i).format(FMT_DAY);
            boolean hasBackend = storage.hasData(date);
            boolean hasLog = logger.hasLogFile(date);

            String source = "none";
            if (hasBackend) source = "memory";
            else if (hasLog) source = "log";

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date);
            item.put("has_data", hasBackend || hasLog);
            item.put("source", source);
            item.put("is_today", date.equals(today));
            dates.add(item);
        }
        writeJson(exchange, dates);
    }

    /** 访问明细：先从内存取，再从日志回退。 */
    @SuppressWarnings("unchecked")
    private void records(HttpExchange exchange) throws IOException {
        Map<String, String> q = parseQuery(exchange.getRequestURI());
        String project = getParam(q, "project", "");
        String clazz = getParam(q, "class", "");
        String method = getParam(q, "method", "");
        String minute = getParam(q, "minute", "");
        int limit = getIntParam(q, "limit", 100);

        List<RecordItem> memRecords = storage.getRecords(project, clazz, method, minute, limit);
        if (!memRecords.isEmpty()) {
            writeJson(exchange, memRecords);
            return;
        }

        // 从日志回退
        List<Map<String, Object>> logRecords = Collections.emptyList();
        if (minute.length() >= 10) {
            String date = minute.substring(0, 10);
            logRecords = logger.readRecordsFromLog(date, project, clazz, method, minute);
        }

        if (limit > 0 && logRecords.size() > limit) {
            logRecords = logRecords.subList(0, limit);
        }
        writeJson(exchange, logRecords);
    }

    // ========================================================================
    // 登录验证
    // ========================================================================

    /** 登录验证：POST 或 GET password 参数，验证通过后写入 cookie（30 天）。 */
    private void login(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())
            && !"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        String pwd = "";
        // 优先读 POST body
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            byte[] body = exchange.getRequestBody().readAllBytes();
            String formData = new String(body, StandardCharsets.UTF_8);
            for (String pair : formData.split("&")) {
                int idx = pair.indexOf('=');
                if (idx > 0) {
                    String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                    if ("password".equals(k)) {
                        pwd = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                        break;
                    }
                }
            }
        }
        if (pwd.isEmpty()) {
            pwd = parseQuery(exchange.getRequestURI()).getOrDefault("password", "");
        }

        if (password.isEmpty()) {
            writeJson(exchange, Map.of("ok", true, "msg", "无需密码"));
            return;
        }

        if (pwd.equals(password)) {
            exchange.getResponseHeaders().add("Set-Cookie",
                "monitor_token=" + expectedToken() + "; Path=/; Max-Age=" + (86400 * 30));
            writeJson(exchange, Map.of("ok", true));
            return;
        }

        // 密码错误：返回 code=1
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("code", 1);
        resp.put("data", Map.of("ok", false, "msg", "密码错误"));
        writeJsonResponse(exchange, 200, resp);
    }

    /** 检查登录状态。 */
    private void checkLogin(HttpExchange exchange) throws IOException {
        if (password.isEmpty()) {
            writeJson(exchange, Map.of("need_login", false));
            return;
        }
        String token = getCookie(exchange, "monitor_token");
        boolean loggedIn = expectedToken().equals(token);
        writeJson(exchange, Map.of("need_login", !loggedIn));
    }

    // ========================================================================
    // 仪表盘页面
    // ========================================================================

    /** 仪表盘首页：返回 public/index.html。 */
    private void index(HttpExchange exchange) throws IOException {
        // 只处理 "/"，其他路径交给上面注册的 handler（GET /a/b 会命中更具体的路由）
        String path = exchange.getRequestURI().getPath();
        if (!"/".equals(path)) {
            exchange.sendResponseHeaders(404, -1);
            return;
        }
        Path htmlFile = publicDir.resolve("index.html");
        if (!Files.exists(htmlFile)) {
            byte[] msg = "Dashboard HTML not found".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(404, msg.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(msg); }
            return;
        }
        byte[] content = Files.readAllBytes(htmlFile);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, content.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(content); }
    }

    // ========================================================================
    // 辅助
    // ========================================================================

    private static int toInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
        if (o instanceof Boolean) return ((Boolean) o) ? 1 : 0;
        if (o instanceof String) {
            try { return Integer.parseInt((String) o); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }

    private static double toDouble(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o instanceof String) {
            try { return Double.parseDouble((String) o); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }

    private static String asStr(Object o) { return o == null ? "" : o.toString(); }
}
