package com.example.monitor.server.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 聚合统计字段集合。
 *
 * <p>用于分钟/小时/天三级粒度的统计数据。</p>
 */
public class AggregationStats {

    @JsonProperty("count")
    private int count;

    @JsonProperty("success")
    private int success;

    @JsonProperty("fail")
    private int fail;

    @JsonProperty("total_time")
    private double totalTime;

    @JsonProperty("max_time")
    private double maxTime;

    @JsonProperty("min_time")
    private double minTime;

    public AggregationStats() {
        this.minTime = Double.MAX_VALUE;
    }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    public int getSuccess() { return success; }
    public void setSuccess(int success) { this.success = success; }

    public int getFail() { return fail; }
    public void setFail(int fail) { this.fail = fail; }

    public double getTotalTime() { return totalTime; }
    public void setTotalTime(double totalTime) { this.totalTime = totalTime; }

    public double getMaxTime() { return maxTime; }
    public void setMaxTime(double maxTime) { this.maxTime = maxTime; }

    public double getMinTime() { return minTime; }
    public void setMinTime(double minTime) { this.minTime = minTime; }
}
