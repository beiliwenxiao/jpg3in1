package com.example.monitor.server.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 单条监控数据记录。
 *
 * <p>包含接口调用的完整信息：项目、控制器、方法、URI、状态码、耗时、时间戳等。</p>
 *
 * <p>字段命名与 PHP 和 Golang 版完全一致，使用 snake_case（JSON 中），确保跨语言兼容。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MonitorRecord {

    @JsonProperty("project")
    private String project;

    @JsonProperty("class")
    private String clazz;

    @JsonProperty("method")
    private String method;

    @JsonProperty("uri")
    private String uri;

    @JsonProperty("status")
    private int status;

    @JsonProperty("duration")
    private double duration;

    @JsonProperty("timestamp")
    private long timestamp;

    @JsonProperty("params")
    private Object params;

    @JsonProperty("response")
    private Object response;

    public MonitorRecord() {}

    public String getProject() { return project; }
    public void setProject(String project) { this.project = project; }

    public String getClazz() { return clazz; }
    public void setClazz(String clazz) { this.clazz = clazz; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getUri() { return uri; }
    public void setUri(String uri) { this.uri = uri; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public double getDuration() { return duration; }
    public void setDuration(double duration) { this.duration = duration; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public Object getParams() { return params; }
    public void setParams(Object params) { this.params = params; }

    public Object getResponse() { return response; }
    public void setResponse(Object response) { this.response = response; }
}
