package com.example.monitor.server.storage;

import com.example.monitor.server.model.AggregationStats;
import com.example.monitor.server.model.MonitorRecord;
import com.example.monitor.server.model.RecordItem;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 内存存储引擎。
 *
 * <p>使用进程内存存储监控数据，适合单实例模式或开发测试。无外部依赖，开箱即用。
 * 进程重启数据丢失（可配合 MonitorLogger 日志持久化）。</p>
 */
public class MemoryStorage implements StorageInterface {

    private static final DateTimeFormatter FMT_MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FMT_HOUR = DateTimeFormatter.ofPattern("yyyy-MM-dd HH");
    private static final DateTimeFormatter FMT_DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FMT_SECOND = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId ZONE = ZoneId.systemDefault();

    /** key -> minute -> stats */
    private final Map<String, Map<String, AggregationStats>> minuteStats = new HashMap<>();
    /** key -> hour -> stats */
    private final Map<String, Map<String, AggregationStats>> hourStats = new HashMap<>();
    /** key -> day -> stats */
    private final Map<String, Map<String, AggregationStats>> dayStats = new HashMap<>();
    /** project -> class -> method -> uri */
    private final Map<String, Map<String, Map<String, String>>> tree = new LinkedHashMap<>();
    /** key -> minute -> [records] */
    private final Map<String, Map<String, List<RecordItem>>> records = new HashMap<>();

    private final int maxRecordsPerMinute = 200;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    @Override
    public void record(MonitorRecord data) {
        String project = nvl(data.getProject(), "default");
        String clazz = nvl(data.getClazz(), "Unknown");
        String method = nvl(data.getMethod(), "unknown");
        String uri = nvl(data.getUri(), "/");
        double duration = data.getDuration();
        int status = data.getStatus() == 0 ? 200 : data.getStatus();
        long ts = data.getTimestamp() == 0 ? Instant.now().getEpochSecond() : data.getTimestamp();

        boolean success = status >= 200 && status < 400;

        String key = project + "|" + clazz + "|" + method + "|" + uri;

        LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochSecond(ts), ZONE);
        String minuteKey = ldt.format(FMT_MINUTE);
        String hourKey = ldt.format(FMT_HOUR);
        String dayKey = ldt.format(FMT_DAY);

        lock.writeLock().lock();
        try {
            // 更新树形结构
            tree.computeIfAbsent(project, k -> new LinkedHashMap<>())
                .computeIfAbsent(clazz, k -> new LinkedHashMap<>())
                .put(method, uri);

            aggregate(minuteStats, key, minuteKey, duration, success);
            aggregate(hourStats, key, hourKey, duration, success);
            aggregate(dayStats, key, dayKey, duration, success);

            // 保存访问明细
            Map<String, List<RecordItem>> byMinute = records.computeIfAbsent(key, k -> new HashMap<>());
            List<RecordItem> list = byMinute.computeIfAbsent(minuteKey, k -> new ArrayList<>());
            if (list.size() < maxRecordsPerMinute) {
                list.add(new RecordItem(
                    ldt.format(FMT_SECOND),
                    duration,
                    status,
                    data.getParams(),
                    data.getResponse()
                ));
            }

            // 清理超过 2 小时的明细
            String cutoff = LocalDateTime.ofInstant(Instant.ofEpochSecond(ts - 7200), ZONE).format(FMT_MINUTE);
            Iterator<Map.Entry<String, Map<String, List<RecordItem>>>> it = records.entrySet().iterator();
            while (it.hasNext()) {
                Map<String, List<RecordItem>> minutes = it.next().getValue();
                minutes.keySet().removeIf(m -> m.compareTo(cutoff) < 0);
                if (minutes.isEmpty()) it.remove();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void aggregate(Map<String, Map<String, AggregationStats>> store, String key, String period,
                           double duration, boolean success) {
        Map<String, AggregationStats> byPeriod = store.computeIfAbsent(key, k -> new HashMap<>());
        AggregationStats s = byPeriod.computeIfAbsent(period, k -> new AggregationStats());
        s.setCount(s.getCount() + 1);
        s.setTotalTime(s.getTotalTime() + duration);
        if (duration > s.getMaxTime()) s.setMaxTime(duration);
        if (duration < s.getMinTime()) s.setMinTime(duration);
        if (success) s.setSuccess(s.getSuccess() + 1);
        else s.setFail(s.getFail() + 1);
    }

    @Override
    public Map<String, Map<String, Map<String, String>>> getTree() {
        lock.readLock().lock();
        try {
            // 深拷贝
            Map<String, Map<String, Map<String, String>>> result = new LinkedHashMap<>();
            for (var pEntry : tree.entrySet()) {
                Map<String, Map<String, String>> classes = new LinkedHashMap<>();
                for (var cEntry : pEntry.getValue().entrySet()) {
                    classes.put(cEntry.getKey(), new LinkedHashMap<>(cEntry.getValue()));
                }
                result.put(pEntry.getKey(), classes);
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Map<String, Object> getDashboard(String date) {
        if (date == null || date.isEmpty()) date = LocalDate.now(ZONE).format(FMT_DAY);

        lock.readLock().lock();
        try {
            int totalCount = 0, totalSuccess = 0, totalFail = 0;
            double totalTime = 0;

            for (Map<String, AggregationStats> periods : dayStats.values()) {
                AggregationStats s = periods.get(date);
                if (s == null) continue;
                totalCount += s.getCount();
                totalSuccess += s.getSuccess();
                totalFail += s.getFail();
                totalTime += s.getTotalTime();
            }

            double successRate = totalCount > 0 ? round2(totalSuccess * 100.0 / totalCount) : 0;
            double avgTime = totalCount > 0 ? round2(totalTime / totalCount) : 0;

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("date", date);
            result.put("total_count", totalCount);
            result.put("success", totalSuccess);
            result.put("fail", totalFail);
            result.put("success_rate", successRate);
            result.put("avg_time", avgTime);
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<Map<String, Object>> getDetail(String project, String clazz, String method, String date, String granularity) {
        if (date == null || date.isEmpty()) date = LocalDate.now(ZONE).format(FMT_DAY);
        String g = granularity == null ? "minute" : granularity;

        lock.readLock().lock();
        try {
            Map<String, Map<String, AggregationStats>> store = switch (g) {
                case "hour" -> hourStats;
                case "day" -> dayStats;
                default -> minuteStats;
            };

            List<Map<String, Object>> result = new ArrayList<>();

            for (var pEntry : tree.entrySet()) {
                String p = pEntry.getKey();
                if (project != null && !project.isEmpty() && !p.equals(project)) continue;
                for (var cEntry : pEntry.getValue().entrySet()) {
                    String c = cEntry.getKey();
                    if (clazz != null && !clazz.isEmpty() && !c.equals(clazz)) continue;
                    for (var mEntry : cEntry.getValue().entrySet()) {
                        String m = mEntry.getKey();
                        String uri = mEntry.getValue();
                        if (method != null && !method.isEmpty() && !m.equals(method)) continue;

                        String key = p + "|" + c + "|" + m + "|" + uri;
                        Map<String, AggregationStats> periods = store.get(key);

                        Map<String, Map<String, Object>> filtered = new TreeMap<>();
                        if (periods != null) {
                            for (var e : periods.entrySet()) {
                                String period = e.getKey();
                                if (!period.startsWith(date)) continue;
                                filtered.put(period, buildPeriodStats(e.getValue()));
                            }
                        }
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("project", p);
                        item.put("class", c);
                        item.put("method", m);
                        item.put("uri", uri);
                        item.put("periods", filtered);
                        result.add(item);
                    }
                }
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** 构造包含 avg_time / success_rate 的 period 统计 map（用于 API 返回） */
    public static Map<String, Object> buildPeriodStats(AggregationStats s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("count", s.getCount());
        m.put("success", s.getSuccess());
        m.put("fail", s.getFail());
        m.put("total_time", s.getTotalTime());
        m.put("max_time", s.getMaxTime());
        double minTime = s.getMinTime() == Double.MAX_VALUE ? 0 : s.getMinTime();
        m.put("min_time", minTime);
        m.put("avg_time", s.getCount() > 0 ? round2(s.getTotalTime() / s.getCount()) : 0);
        m.put("success_rate", s.getCount() > 0 ? round2(s.getSuccess() * 100.0 / s.getCount()) : 0);
        return m;
    }

    @Override
    public List<Map<String, Object>> getSlowRanking(String date, int limit) {
        if (date == null || date.isEmpty()) date = LocalDate.now(ZONE).format(FMT_DAY);
        lock.readLock().lock();
        try {
            List<Map<String, Object>> ranking = new ArrayList<>();
            for (var entry : dayStats.entrySet()) {
                AggregationStats s = entry.getValue().get(date);
                if (s == null) continue;
                String[] parts = entry.getKey().split("\\|", 4);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("project", parts.length > 0 ? parts[0] : "");
                item.put("class", parts.length > 1 ? parts[1] : "");
                item.put("method", parts.length > 2 ? parts[2] : "");
                item.put("uri", parts.length > 3 ? parts[3] : "");
                item.put("avg_time", s.getCount() > 0 ? round2(s.getTotalTime() / s.getCount()) : 0);
                item.put("max_time", round2(s.getMaxTime()));
                item.put("count", s.getCount());
                ranking.add(item);
            }
            ranking.sort((a, b) -> Double.compare(
                ((Number) b.get("avg_time")).doubleValue(),
                ((Number) a.get("avg_time")).doubleValue()
            ));
            return limit > 0 && ranking.size() > limit ? ranking.subList(0, limit) : ranking;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<Map<String, Object>> getCountRanking(String date, int limit) {
        if (date == null || date.isEmpty()) date = LocalDate.now(ZONE).format(FMT_DAY);
        lock.readLock().lock();
        try {
            List<Map<String, Object>> ranking = new ArrayList<>();
            for (var entry : dayStats.entrySet()) {
                AggregationStats s = entry.getValue().get(date);
                if (s == null) continue;
                String[] parts = entry.getKey().split("\\|", 4);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("project", parts.length > 0 ? parts[0] : "");
                item.put("class", parts.length > 1 ? parts[1] : "");
                item.put("method", parts.length > 2 ? parts[2] : "");
                item.put("uri", parts.length > 3 ? parts[3] : "");
                item.put("count", s.getCount());
                item.put("success", s.getSuccess());
                item.put("fail", s.getFail());
                item.put("success_rate", s.getCount() > 0 ? round2(s.getSuccess() * 100.0 / s.getCount()) : 0);
                ranking.add(item);
            }
            ranking.sort((a, b) -> Integer.compare(
                ((Number) b.get("count")).intValue(),
                ((Number) a.get("count")).intValue()
            ));
            return limit > 0 && ranking.size() > limit ? ranking.subList(0, limit) : ranking;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<Map<String, Object>> getRealtime() {
        lock.readLock().lock();
        try {
            List<Map<String, Object>> result = new ArrayList<>(10);
            LocalDateTime now = LocalDateTime.now(ZONE);
            for (int i = 9; i >= 0; i--) {
                String minute = now.minusMinutes(i).format(FMT_MINUTE);
                int count = 0, success = 0, fail = 0;
                for (Map<String, AggregationStats> periods : minuteStats.values()) {
                    AggregationStats s = periods.get(minute);
                    if (s == null) continue;
                    count += s.getCount();
                    success += s.getSuccess();
                    fail += s.getFail();
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("time", minute);
                item.put("count", count);
                item.put("success", success);
                item.put("fail", fail);
                result.add(item);
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<Map<String, Object>> search(String keyword, String date) {
        if (date == null || date.isEmpty()) date = LocalDate.now(ZONE).format(FMT_DAY);
        String lowerKw = keyword == null ? "" : keyword.toLowerCase();
        lock.readLock().lock();
        try {
            List<Map<String, Object>> result = new ArrayList<>();
            for (var entry : dayStats.entrySet()) {
                if (!entry.getKey().toLowerCase().contains(lowerKw)) continue;
                AggregationStats s = entry.getValue().get(date);
                if (s == null) continue;
                String[] parts = entry.getKey().split("\\|", 4);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("project", parts.length > 0 ? parts[0] : "");
                item.put("class", parts.length > 1 ? parts[1] : "");
                item.put("method", parts.length > 2 ? parts[2] : "");
                item.put("uri", parts.length > 3 ? parts[3] : "");
                item.put("count", s.getCount());
                item.put("success_rate", s.getCount() > 0 ? round2(s.getSuccess() * 100.0 / s.getCount()) : 0);
                item.put("avg_time", s.getCount() > 0 ? round2(s.getTotalTime() / s.getCount()) : 0);
                result.add(item);
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<Map<String, Object>> getDayTrend(String date) {
        if (date == null || date.isEmpty()) date = LocalDate.now(ZONE).format(FMT_DAY);
        lock.readLock().lock();
        try {
            // 预聚合：minute -> [count, success, fail]
            Map<String, int[]> agg = new HashMap<>();
            for (Map<String, AggregationStats> periods : minuteStats.values()) {
                for (var e : periods.entrySet()) {
                    if (!e.getKey().startsWith(date)) continue;
                    int[] arr = agg.computeIfAbsent(e.getKey(), k -> new int[3]);
                    arr[0] += e.getValue().getCount();
                    arr[1] += e.getValue().getSuccess();
                    arr[2] += e.getValue().getFail();
                }
            }
            List<Map<String, Object>> result = new ArrayList<>(1440);
            for (int h = 0; h < 24; h++) {
                for (int m = 0; m < 60; m++) {
                    String minute = String.format("%s %02d:%02d", date, h, m);
                    int[] arr = agg.getOrDefault(minute, new int[3]);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("time", minute);
                    item.put("count", arr[0]);
                    item.put("success", arr[1]);
                    item.put("fail", arr[2]);
                    result.add(item);
                }
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean hasData(String date) {
        lock.readLock().lock();
        try {
            for (Map<String, AggregationStats> periods : dayStats.values()) {
                if (periods.containsKey(date)) return true;
            }
            return false;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<RecordItem> getRecords(String project, String clazz, String method, String minute, int limit) {
        lock.readLock().lock();
        try {
            Map<String, Map<String, String>> classes = tree.get(project);
            if (classes == null) return Collections.emptyList();
            Map<String, String> methods = classes.get(clazz);
            if (methods == null) return Collections.emptyList();
            String uri = methods.get(method);
            if (uri == null) return Collections.emptyList();

            String key = project + "|" + clazz + "|" + method + "|" + uri;
            Map<String, List<RecordItem>> byMinute = records.get(key);
            if (byMinute == null) return Collections.emptyList();
            List<RecordItem> list = byMinute.get(minute);
            if (list == null) return Collections.emptyList();
            if (limit > 0 && list.size() > limit) return new ArrayList<>(list.subList(0, limit));
            return new ArrayList<>(list);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Map<String, AggregationStats> getMinutePeriods(String project, String clazz, String method, String date) {
        lock.readLock().lock();
        try {
            Map<String, Map<String, String>> classes = tree.get(project);
            if (classes == null) return Collections.emptyMap();
            Map<String, String> methods = classes.get(clazz);
            if (methods == null) return Collections.emptyMap();
            String uri = methods.get(method);
            if (uri == null) return Collections.emptyMap();

            String key = project + "|" + clazz + "|" + method + "|" + uri;
            Map<String, AggregationStats> periods = minuteStats.get(key);
            if (periods == null) return Collections.emptyMap();

            Map<String, AggregationStats> result = new TreeMap<>();
            for (var e : periods.entrySet()) {
                if (!e.getKey().startsWith(date)) continue;
                result.put(e.getKey(), e.getValue());
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    private static String nvl(String v, String def) {
        return (v == null || v.isEmpty()) ? def : v;
    }

    public static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
