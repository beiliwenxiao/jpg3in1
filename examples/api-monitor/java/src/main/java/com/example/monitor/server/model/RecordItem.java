package com.example.monitor.server.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 单次请求的访问明细记录。
 */
public class RecordItem {

    @JsonProperty("time")
    private String time;

    @JsonProperty("duration")
    private double duration;

    @JsonProperty("status")
    private int status;

    @JsonProperty("params")
    private Object params;

    @JsonProperty("response")
    private Object response;

    public RecordItem() {}

    public RecordItem(String time, double duration, int status, Object params, Object response) {
        this.time = time;
        this.duration = duration;
        this.status = status;
        this.params = params;
        this.response = response;
    }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }

    public double getDuration() { return duration; }
    public void setDuration(double duration) { this.duration = duration; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public Object getParams() { return params; }
    public void setParams(Object params) { this.params = params; }

    public Object getResponse() { return response; }
    public void setResponse(Object response) { this.response = response; }
}
