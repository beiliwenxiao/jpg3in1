package com.example.monitor.server.logger;

import com.example.monitor.server.model.AggregationStats;
import com.example.monitor.server.model.RecordItem;
import com.example.monitor.server.storage.MemoryStorage;
import com.example.monitor.server.storage.StorageInterface;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * 监控数据日志持久化。
 *
 * <p>使用 sessionId 机制避免重启后数据重复或丢失：
 * 每次进程启动生成唯一 sessionId，日志按 session 分槽存储，
 * 查询时合并所有历史 session（排除当前 session），内存实时数据单独叠加。</p>
 *
 * <p>日志文件结构：
 * <pre>
 *   {date}.json          // 天级汇总
 *     { sessions: { "s_xxx": {dashboard, slow_ranking, count_ranking, tree} } }
 *   {date}_minute.json   // 分钟级明细
 *     { sessions: { "s_xxx": {exported_at, details: [...]} } }
 * </pre></p>
 */
public class MonitorLogger {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final DateTimeFormatter FMT_DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FMT_SECOND = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    static {
        MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
    }

    private final StorageInterface storage;
    private final Path logDir;
    private final int intervalSeconds;
    private final String sessionId;
    private ScheduledExecutorService scheduler;

    public MonitorLogger(StorageInterface storage, String logDirPath, int intervalSeconds) {
        this.storage = storage;
        this.logDir = Paths.get(logDirPath);
        this.intervalSeconds = intervalSeconds;
        // 生成唯一 sessionId：s_{timestamp}_{random6hex}
        String rand = String.format("%06x", new Random().nextInt(0xFFFFFF));
        this.sessionId = "s_" + (System.currentTimeMillis() / 1000) + "_" + rand;

        try {
            Files.createDirectories(logDir);
        } catch (Exception e) {
            System.err.println("[监控日志] 无法创建日志目录: " + e.getMessage());
        }
    }

    public String getSessionId() {
        return sessionId;
    }

    public Path getLogDir() {
        return logDir;
    }

    /** 启动定时导出。 */
    public void start() {
        if (scheduler != null) return;
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "api-monitor-logger");
            t.setDaemon(true);
            return t;
        };
        scheduler = Executors.newSingleThreadScheduledExecutor(tf);
        scheduler.scheduleAtFixedRate(this::safeExport, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        System.out.println("[监控] 日志持久化启动 (session: " + sessionId + ")，间隔 " + intervalSeconds + " 秒");
    }

    /** 停止定时导出并执行最后一次导出。 */
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
            try { scheduler.awaitTermination(2, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            scheduler = null;
        }
        safeExport();
    }

    private void safeExport() {
        try {
            export();
        } catch (Throwable t) {
            System.err.println("[监控日志] 导出失败: " + t.getMessage());
        }
    }

    /** 立即执行一次导出。 */
    public void export() {
        String date = LocalDate.now().format(FMT_DAY);
        exportDaySummary(date);
        exportMinuteDetail(date);
    }

    // ========================================================================
    // 导出：写入当前 session 槽位
    // ========================================================================

    @SuppressWarnings("unchecked")
    private void exportDaySummary(String date) {
        Path file = logDir.resolve(date + ".json");

        Map<String, Object> logData = readJsonFile(file);
        // 兼容旧格式：直接有 dashboard 字段的文件迁移为 legacy session
        if (logData != null && logData.containsKey("dashboard") && !logData.containsKey("sessions")) {
            Map<String, Object> legacy = new LinkedHashMap<>(logData);
            logData = new LinkedHashMap<>();
            logData.put("sessions", new LinkedHashMap<String, Object>(Map.of("legacy", legacy)));
        }
        if (logData == null) logData = new LinkedHashMap<>();
        Map<String, Object> sessions = (Map<String, Object>) logData.get("sessions");
        if (sessions == null) {
            sessions = new LinkedHashMap<>();
            logData.put("sessions", sessions);
        }

        Map<String, Object> current = new LinkedHashMap<>();
        current.put("exported_at", LocalDateTime.now().format(FMT_SECOND));
        current.put("dashboard", storage.getDashboard(date));
        current.put("slow_ranking", storage.getSlowRanking(date, 50));
        current.put("count_ranking", storage.getCountRanking(date, 50));
        current.put("tree", storage.getTree());
        sessions.put(sessionId, current);

        writeJsonFile(file, logData);
    }

    @SuppressWarnings("unchecked")
    private void exportMinuteDetail(String date) {
        Path file = logDir.resolve(date + "_minute.json");

        Map<String, Object> logData = readJsonFile(file);
        // 兼容旧格式
        if (logData != null && logData.containsKey("details") && !logData.containsKey("sessions")) {
            Map<String, Object> legacy = new LinkedHashMap<>(logData);
            logData = new LinkedHashMap<>();
            logData.put("sessions", new LinkedHashMap<String, Object>(Map.of("legacy", legacy)));
        }
        if (logData == null) logData = new LinkedHashMap<>();
        Map<String, Object> sessions = (Map<String, Object>) logData.get("sessions");
        if (sessions == null) {
            sessions = new LinkedHashMap<>();
            logData.put("sessions", sessions);
        }

        // 收集所有接口的分钟级明细
        List<Map<String, Object>> details = new ArrayList<>();
        Map<String, Map<String, Map<String, String>>> tree = storage.getTree();
        for (var pEntry : tree.entrySet()) {
            String project = pEntry.getKey();
            for (var cEntry : pEntry.getValue().entrySet()) {
                String clazz = cEntry.getKey();
                for (var mEntry : cEntry.getValue().entrySet()) {
                    String method = mEntry.getKey();
                    String uri = mEntry.getValue();

                    Map<String, AggregationStats> periods = storage.getMinutePeriods(project, clazz, method, date);
                    if (periods.isEmpty()) continue;

                    // periods（包含 avg_time / success_rate）
                    Map<String, Map<String, Object>> periodsOut = new TreeMap<>();
                    Map<String, List<RecordItem>> records = new TreeMap<>();
                    for (var pe : periods.entrySet()) {
                        String minute = pe.getKey();
                        periodsOut.put(minute, MemoryStorage.buildPeriodStats(pe.getValue()));
                        List<RecordItem> recs = storage.getRecords(project, clazz, method, minute, 200);
                        if (!recs.isEmpty()) records.put(minute, recs);
                    }

                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("project", project);
                    item.put("class", clazz);
                    item.put("method", method);
                    item.put("uri", uri);
                    item.put("periods", periodsOut);
                    item.put("records", records);
                    details.add(item);
                }
            }
        }

        Map<String, Object> current = new LinkedHashMap<>();
        current.put("exported_at", LocalDateTime.now().format(FMT_SECOND));
        current.put("details", details);
        sessions.put(sessionId, current);

        writeJsonFile(file, logData);
    }

    private Map<String, Object> readJsonFile(Path file) {
        if (!Files.exists(file)) return null;
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length == 0) return null;
            return MAPPER.readValue(bytes, MAP_TYPE);
        } catch (Exception e) {
            return null;
        }
    }

    private void writeJsonFile(Path file, Map<String, Object> data) {
        try {
            byte[] bytes = MAPPER.writeValueAsBytes(data);
            Files.write(file, bytes);
        } catch (Exception e) {
            System.err.println("[监控日志] 写入失败: " + file + " - " + e.getMessage());
        }
    }

    // ========================================================================
    // 读取：合并所有 session（排除当前 session）
    // ========================================================================

    /** 读取天级汇总日志（合并所有历史 session，排除当前 session）。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> readDayLog(String date) {
        Path file = logDir.resolve(date + ".json");
        Map<String, Object> logData = readJsonFile(file);
        if (logData == null) return null;

        // 旧格式：直接有 dashboard
        if (logData.containsKey("dashboard")) return logData;

        Map<String, Object> sessionsRaw = (Map<String, Object>) logData.get("sessions");
        if (sessionsRaw == null || sessionsRaw.isEmpty()) return null;

        Map<String, Object> sessions = new LinkedHashMap<>(sessionsRaw);
        sessions.remove(sessionId);
        if (sessions.isEmpty()) return null;

        return mergeDaySessions(sessions);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mergeDaySessions(Map<String, Object> sessions) {
        int totalCount = 0, totalSuccess = 0, totalFail = 0;
        double totalTime = 0;
        Map<String, Map<String, Map<String, String>>> tree = new LinkedHashMap<>();
        Map<String, Map<String, Object>> slowMap = new LinkedHashMap<>();
        Map<String, Map<String, Object>> countMap = new LinkedHashMap<>();

        for (Object sessionRaw : sessions.values()) {
            if (!(sessionRaw instanceof Map)) continue;
            Map<String, Object> session = (Map<String, Object>) sessionRaw;

            Map<String, Object> dashboard = (Map<String, Object>) session.get("dashboard");
            if (dashboard != null) {
                int c = toInt(dashboard.get("total_count"));
                totalCount += c;
                totalSuccess += toInt(dashboard.get("success"));
                totalFail += toInt(dashboard.get("fail"));
                totalTime += toDouble(dashboard.get("avg_time")) * c;
            }

            // 合并 tree
            Map<String, Object> sessionTree = (Map<String, Object>) session.get("tree");
            if (sessionTree != null) {
                for (var pe : sessionTree.entrySet()) {
                    Map<String, Object> classes = (Map<String, Object>) pe.getValue();
                    if (classes == null) continue;
                    var treeClasses = tree.computeIfAbsent(pe.getKey(), k -> new LinkedHashMap<>());
                    for (var ce : classes.entrySet()) {
                        Map<String, Object> methods = (Map<String, Object>) ce.getValue();
                        if (methods == null) continue;
                        var treeMethods = treeClasses.computeIfAbsent(ce.getKey(), k -> new LinkedHashMap<>());
                        for (var me : methods.entrySet()) {
                            treeMethods.putIfAbsent(me.getKey(), me.getValue() == null ? "" : me.getValue().toString());
                        }
                    }
                }
            }

            accumulateRanking(slowMap, (List<Map<String, Object>>) session.get("slow_ranking"));
            accumulateRanking(countMap, (List<Map<String, Object>>) session.get("count_ranking"));
        }

        List<Map<String, Object>> slowList = new ArrayList<>(slowMap.values());
        slowList.sort((a, b) -> Double.compare(toDouble(b.get("avg_time")), toDouble(a.get("avg_time"))));
        if (slowList.size() > 50) slowList = new ArrayList<>(slowList.subList(0, 50));

        List<Map<String, Object>> countList = new ArrayList<>(countMap.values());
        countList.sort((a, b) -> Integer.compare(toInt(b.get("count")), toInt(a.get("count"))));
        if (countList.size() > 50) countList = new ArrayList<>(countList.subList(0, 50));

        double successRate = totalCount > 0 ? Math.round(totalSuccess * 10000.0 / totalCount) / 100.0 : 0;
        double avgTime = totalCount > 0 ? Math.round(totalTime * 100.0 / totalCount) / 100.0 : 0;

        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("date", "");
        dashboard.put("total_count", totalCount);
        dashboard.put("success", totalSuccess);
        dashboard.put("fail", totalFail);
        dashboard.put("success_rate", successRate);
        dashboard.put("avg_time", avgTime);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dashboard", dashboard);
        result.put("slow_ranking", slowList);
        result.put("count_ranking", countList);
        result.put("tree", tree);
        return result;
    }

    private static void accumulateRanking(Map<String, Map<String, Object>> map, List<Map<String, Object>> ranking) {
        if (ranking == null) return;
        for (Map<String, Object> item : ranking) {
            String key = asStr(item.get("project")) + "|" + asStr(item.get("class")) + "|" + asStr(item.get("method"));
            Map<String, Object> existing = map.get(key);
            if (existing == null) {
                map.put(key, new LinkedHashMap<>(item));
            } else {
                int oldCount = toInt(existing.get("count"));
                int newCount = toInt(item.get("count"));
                int tc = oldCount + newCount;
                existing.put("count", tc);
                if (item.containsKey("success")) {
                    int s = toInt(existing.get("success")) + toInt(item.get("success"));
                    int f = toInt(existing.get("fail")) + toInt(item.get("fail"));
                    existing.put("success", s);
                    existing.put("fail", f);
                    existing.put("success_rate", tc > 0 ? Math.round(s * 10000.0 / tc) / 100.0 : 0);
                }
                if (item.containsKey("avg_time")) {
                    double ot = toDouble(existing.get("avg_time")) * oldCount;
                    double nt = toDouble(item.get("avg_time")) * newCount;
                    existing.put("avg_time", tc > 0 ? Math.round((ot + nt) * 100.0 / tc) / 100.0 : 0);
                }
                if (item.containsKey("max_time")) {
                    double mx = Math.max(toDouble(existing.get("max_time")), toDouble(item.get("max_time")));
                    existing.put("max_time", mx);
                }
                if (!existing.containsKey("uri") && item.containsKey("uri")) {
                    existing.put("uri", item.get("uri"));
                }
            }
        }
    }

    /** 读取分钟级明细日志（合并所有历史 session，排除当前 session）。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> readMinuteLog(String date) {
        Path file = logDir.resolve(date + "_minute.json");
        Map<String, Object> logData = readJsonFile(file);
        if (logData == null) return null;

        // 旧格式：直接有 details
        if (logData.containsKey("details")) return logData;

        Map<String, Object> sessionsRaw = (Map<String, Object>) logData.get("sessions");
        if (sessionsRaw == null || sessionsRaw.isEmpty()) return null;

        Map<String, Object> sessions = new LinkedHashMap<>(sessionsRaw);
        sessions.remove(sessionId);
        if (sessions.isEmpty()) return null;

        return mergeMinuteSessions(sessions);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mergeMinuteSessions(Map<String, Object> sessions) {
        // key(project|class|method) -> detail
        Map<String, Map<String, Object>> detailMap = new LinkedHashMap<>();

        for (Object sessionRaw : sessions.values()) {
            if (!(sessionRaw instanceof Map)) continue;
            Map<String, Object> session = (Map<String, Object>) sessionRaw;
            List<Map<String, Object>> details = (List<Map<String, Object>>) session.get("details");
            if (details == null) continue;

            for (Map<String, Object> detail : details) {
                String key = asStr(detail.get("project")) + "|" + asStr(detail.get("class")) + "|" + asStr(detail.get("method"));
                Map<String, Object> existing = detailMap.get(key);

                if (existing == null) {
                    // 深拷贝必要字段
                    Map<String, Object> cp = new LinkedHashMap<>();
                    cp.put("project", detail.get("project"));
                    cp.put("class", detail.get("class"));
                    cp.put("method", detail.get("method"));
                    cp.put("uri", detail.get("uri"));

                    Map<String, Object> periods = new TreeMap<>();
                    Map<String, Object> srcPeriods = (Map<String, Object>) detail.get("periods");
                    if (srcPeriods != null) {
                        for (var pe : srcPeriods.entrySet()) {
                            if (pe.getValue() instanceof Map) {
                                periods.put(pe.getKey(), new LinkedHashMap<>((Map<String, Object>) pe.getValue()));
                            }
                        }
                    }
                    cp.put("periods", periods);

                    Map<String, Object> records = new TreeMap<>();
                    Map<String, Object> srcRecords = (Map<String, Object>) detail.get("records");
                    if (srcRecords != null) {
                        for (var re : srcRecords.entrySet()) {
                            if (re.getValue() instanceof List) {
                                records.put(re.getKey(), new ArrayList<>((List<Object>) re.getValue()));
                            }
                        }
                    }
                    cp.put("records", records);

                    detailMap.put(key, cp);
                } else {
                    // 合并 periods
                    Map<String, Object> existingPeriods = (Map<String, Object>) existing.get("periods");
                    Map<String, Object> srcPeriods = (Map<String, Object>) detail.get("periods");
                    if (srcPeriods != null && existingPeriods != null) {
                        for (var pe : srcPeriods.entrySet()) {
                            String minute = pe.getKey();
                            if (!(pe.getValue() instanceof Map)) continue;
                            Map<String, Object> newStats = (Map<String, Object>) pe.getValue();
                            Map<String, Object> oldStats = (Map<String, Object>) existingPeriods.get(minute);
                            if (oldStats == null) {
                                existingPeriods.put(minute, new LinkedHashMap<>(newStats));
                            } else {
                                int tc = toInt(oldStats.get("count")) + toInt(newStats.get("count"));
                                double totalTime = toDouble(oldStats.get("total_time")) + toDouble(newStats.get("total_time"));
                                int s = toInt(oldStats.get("success")) + toInt(newStats.get("success"));
                                int f = toInt(oldStats.get("fail")) + toInt(newStats.get("fail"));
                                double mx = Math.max(toDouble(oldStats.get("max_time")), toDouble(newStats.get("max_time")));
                                double mn = Math.min(
                                    oldStats.containsKey("min_time") ? toDouble(oldStats.get("min_time")) : Double.MAX_VALUE,
                                    newStats.containsKey("min_time") ? toDouble(newStats.get("min_time")) : Double.MAX_VALUE
                                );
                                if (mn == Double.MAX_VALUE) mn = 0;

                                Map<String, Object> merged = new LinkedHashMap<>();
                                merged.put("count", tc);
                                merged.put("success", s);
                                merged.put("fail", f);
                                merged.put("total_time", totalTime);
                                merged.put("max_time", mx);
                                merged.put("min_time", mn);
                                merged.put("avg_time", tc > 0 ? Math.round(totalTime * 100.0 / tc) / 100.0 : 0);
                                merged.put("success_rate", tc > 0 ? Math.round(s * 10000.0 / tc) / 100.0 : 0);
                                existingPeriods.put(minute, merged);
                            }
                        }
                    }

                    // 合并 records（按 time 去重）
                    Map<String, Object> existingRecords = (Map<String, Object>) existing.get("records");
                    Map<String, Object> srcRecords = (Map<String, Object>) detail.get("records");
                    if (srcRecords != null && existingRecords != null) {
                        for (var re : srcRecords.entrySet()) {
                            String minute = re.getKey();
                            if (!(re.getValue() instanceof List)) continue;
                            List<Object> newList = (List<Object>) re.getValue();
                            List<Object> oldList = (List<Object>) existingRecords.get(minute);
                            if (oldList == null) {
                                existingRecords.put(minute, new ArrayList<>(newList));
                            } else {
                                Set<String> existingTimes = new HashSet<>();
                                for (Object o : oldList) {
                                    if (o instanceof Map) {
                                        existingTimes.add(asStr(((Map<String, Object>) o).get("time")));
                                    }
                                }
                                for (Object rec : newList) {
                                    if (rec instanceof Map && !existingTimes.contains(asStr(((Map<String, Object>) rec).get("time")))) {
                                        oldList.add(rec);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("details", new ArrayList<>(detailMap.values()));
        return result;
    }

    /** 从日志读取某接口某分钟的明细记录。 */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> readRecordsFromLog(String date, String project, String clazz, String method, String minute) {
        Map<String, Object> log = readMinuteLog(date);
        if (log == null) return Collections.emptyList();
        List<Map<String, Object>> details = (List<Map<String, Object>>) log.get("details");
        if (details == null) return Collections.emptyList();

        for (Map<String, Object> detail : details) {
            if (project.equals(asStr(detail.get("project")))
                && clazz.equals(asStr(detail.get("class")))
                && method.equals(asStr(detail.get("method")))) {
                Map<String, Object> records = (Map<String, Object>) detail.get("records");
                if (records == null) return Collections.emptyList();
                Object rs = records.get(minute);
                if (rs instanceof List) {
                    return (List<Map<String, Object>>) rs;
                }
                return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }

    /** 检查日期日志文件是否存在。 */
    public boolean hasLogFile(String date) {
        return Files.exists(logDir.resolve(date + ".json"));
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    private static int toInt(Object o) {
        if (o instanceof Number) return ((Number) o).intValue();
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

    private static String asStr(Object o) {
        return o == null ? "" : o.toString();
    }
}
