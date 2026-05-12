package com.example.monitor.client;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * UDP 监控客户端。
 *
 * <p>通过 UDP 发送接口性能数据到监控服务端。UDP 是无连接的，发送失败不会影响业务逻辑。
 * 内部维护缓冲区，累积到 bufferSize 条后批量发送（换行分隔），减少网络开销。</p>
 *
 * <p>线程安全：所有 public 方法加锁。</p>
 *
 * <p>数据格式（与 PHP、Golang 版完全一致）：
 * <pre>
 *   {"project":"...", "class":"...", "method":"...", "uri":"...",
 *    "status":200, "duration":35.6, "timestamp":1699999999,
 *    "params":{...}, "response":{...}}
 * </pre></p>
 */
public class MonitorClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String host;
    private final int port;
    private final String project;
    private final double sampleRate;
    private final int bufferSize;
    private final List<String> buffer;
    private DatagramSocket socket;
    private InetAddress address;

    public MonitorClient(String host, int port, String project, double sampleRate, int bufferSize) {
        this.host = host;
        this.port = port;
        this.project = project;
        this.sampleRate = sampleRate;
        this.bufferSize = Math.max(1, bufferSize);
        this.buffer = new ArrayList<>(this.bufferSize);
    }

    /**
     * 上报一条接口性能数据。
     *
     * @param clazz    控制器/类名
     * @param method   方法名
     * @param uri      请求 URI
     * @param status   HTTP 状态码
     * @param duration 耗时（毫秒）
     * @param params   请求参数（可选，null 则不包含）
     * @param response 响应数据（可选，null 则不包含）
     */
    public synchronized void report(String clazz, String method, String uri, int status, double duration,
                                    Object params, Object response) {
        // 采样率控制
        if (sampleRate < 1.0 && ThreadLocalRandom.current().nextDouble() > sampleRate) {
            return;
        }

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("project", project);
        record.put("class", clazz);
        record.put("method", method);
        record.put("uri", uri);
        record.put("status", status);
        record.put("duration", Math.round(duration * 100.0) / 100.0);
        record.put("timestamp", Instant.now().getEpochSecond());
        if (params != null) record.put("params", params);
        if (response != null) record.put("response", response);

        String json;
        try {
            json = MAPPER.writeValueAsString(record);
        } catch (Exception e) {
            return; // 序列化失败，静默忽略
        }

        buffer.add(json);
        if (buffer.size() >= bufferSize) {
            flushInternal();
        }
    }

    /** 刷新缓冲区，立即发送所有待发送数据。 */
    public synchronized void flush() {
        flushInternal();
    }

    private void flushInternal() {
        if (buffer.isEmpty()) return;
        String payload = String.join("\n", buffer);
        buffer.clear();
        sendUdp(payload);
    }

    private void sendUdp(String data) {
        try {
            if (socket == null) {
                socket = new DatagramSocket();
            }
            if (address == null) {
                address = InetAddress.getByName(host);
            }
            byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address, port);
            socket.send(packet);
        } catch (Exception ignored) {
            // UDP 发送失败静默忽略
        }
    }

    /** 关闭客户端，自动 flush 剩余数据。 */
    public synchronized void close() {
        flushInternal();
        if (socket != null) {
            try { socket.close(); } catch (Exception ignored) {}
            socket = null;
        }
    }

    /** 获取当前缓冲区长度（用于测试/监控）。 */
    public synchronized int getBufferLen() {
        return buffer.size();
    }
}
