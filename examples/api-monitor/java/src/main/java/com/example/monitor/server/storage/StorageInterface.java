package com.example.monitor.server.storage;

import com.example.monitor.server.model.AggregationStats;
import com.example.monitor.server.model.MonitorRecord;
import com.example.monitor.server.model.RecordItem;

import java.util.List;
import java.util.Map;

/**
 * 监控数据存储接口。
 *
 * <p>定义所有数据读写操作的抽象层，支持内存存储和未来扩展。</p>
 */
public interface StorageInterface {

    /** 写入一条监控记录，自动按分钟/小时/天三级聚合统计。 */
    void record(MonitorRecord data);

    /** 获取树形菜单：project → class → method → uri 的三级结构。 */
    Map<String, Map<String, Map<String, String>>> getTree();

    /** 获取仪表盘概览：总数、成功/失败数、成功率、平均耗时。 */
    Map<String, Object> getDashboard(String date);

    /** 获取接口详情：granularity 可选 minute/hour/day。 */
    List<Map<String, Object>> getDetail(String project, String clazz, String method, String date, String granularity);

    /** 慢速排行（按平均耗时降序）。 */
    List<Map<String, Object>> getSlowRanking(String date, int limit);

    /** 访问量排行（按次数降序）。 */
    List<Map<String, Object>> getCountRanking(String date, int limit);

    /** 实时访问量：最近 10 分钟每分钟的访问统计。 */
    List<Map<String, Object>> getRealtime();

    /** 搜索接口：对 project/class/method/uri 进行大小写不敏感关键词匹配。 */
    List<Map<String, Object>> search(String keyword, String date);

    /** 全天分钟级访问趋势：1440 个数据点，无数据填零。 */
    List<Map<String, Object>> getDayTrend(String date);

    /** 判断指定日期是否有数据。 */
    boolean hasData(String date);

    /** 获取访问明细列表。 */
    List<RecordItem> getRecords(String project, String clazz, String method, String minute, int limit);

    /** 获取某个接口在某分钟的原始聚合统计（用于日志导出）。 */
    Map<String, AggregationStats> getMinutePeriods(String project, String clazz, String method, String date);
}
