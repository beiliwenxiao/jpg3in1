package com.framework.protocol.internal.jsonrpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JSON-RPC 内部协议客户端实现
 *
 * 使用原生 HttpURLConnection 发送 JSON-RPC 请求，全程强制 UTF-8 编码。
 *
 * **验证需求: 3.2, 3.5**
 */
public class JsonRpcInternalClient {

    private static final Logger logger = LoggerFactory.getLogger(JsonRpcInternalClient.class);

    private final String host;
    private final int port;
    private final ObjectMapper objectMapper;
    private final AtomicLong idCounter = new AtomicLong(1);
    private volatile boolean started;

    public JsonRpcInternalClient(String host, int port) {
        this.host = host;
        this.port = port;
        this.objectMapper = new ObjectMapper();
        this.started = false;
    }

    /** 启动客户端（无需初始化连接，按需建立） */
    public void start() {
        if (started) {
            logger.warn("JSON-RPC 内部客户端已经启动");
            return;
        }
        logger.info("启动 JSON-RPC 内部客户端: {}:{}", host, port);
        started = true;
        logger.info("JSON-RPC 内部客户端启动成功");
    }

    /** 关闭客户端 */
    public void shutdown() {
        if (!started) {
            logger.warn("JSON-RPC 内部客户端未启动");
            return;
        }
        logger.info("关闭 JSON-RPC 内部客户端");
        started = false;
        logger.info("JSON-RPC 内部客户端已关闭");
    }

    /**
     * 同步调用服务
     */
    public byte[] call(String service, String method, byte[] payload,
                       Map<String, String> headers, long timeout) {
        validateState();
        logger.debug("JSON-RPC 内部同步调用: service={}, method={}", service, method);

        try {
            String rpcMethod = service + "." + method;
            Object params = deserializePayload(payload);
            long id = idCounter.getAndIncrement();

            // 构建 JSON-RPC 2.0 请求体
            Map<String, Object> request = new HashMap<>();
            request.put("jsonrpc", "2.0");
            request.put("method", rpcMethod);
            request.put("params", params);
            request.put("id", id);
            byte[] requestBytes = objectMapper.writeValueAsBytes(request);

            // 发送 HTTP 请求，强制 UTF-8
            byte[] responseBytes = sendHttp(requestBytes, timeout);

            // 解析 JSON-RPC 响应
            return extractResult(responseBytes, id);

        } catch (FrameworkException e) {
            throw e;
        } catch (Exception e) {
            logger.error("JSON-RPC 调用异常: service={}, method={}", service, method, e);
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR,
                    "JSON-RPC 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 异步调用服务
     */
    public CompletableFuture<byte[]> callAsync(String service, String method, byte[] payload,
                                               Map<String, String> headers, long timeout) {
        validateState();
        logger.debug("JSON-RPC 内部异步调用: service={}, method={}", service, method);

        return CompletableFuture.supplyAsync(() -> {
            try {
                return call(service, method, payload, headers, timeout);
            } catch (Exception e) {
                throw new FrameworkException(ErrorCode.INTERNAL_ERROR,
                        "JSON-RPC 异步调用失败: " + e.getMessage(), e);
            }
        });
    }

    /** 健康检查 */
    public boolean healthCheck() {
        validateState();
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("jsonrpc", "2.0");
            request.put("method", "health.check");
            request.put("id", idCounter.getAndIncrement());
            byte[] requestBytes = objectMapper.writeValueAsBytes(request);
            byte[] responseBytes = sendHttp(requestBytes, 5000);
            return responseBytes != null && responseBytes.length > 0;
        } catch (Exception e) {
            logger.error("健康检查失败", e);
            return false;
        }
    }

    // ---- 私有辅助方法 ----

    private void validateState() {
        if (!started) {
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, "JSON-RPC 内部客户端未启动");
        }
    }

    /** 发送 HTTP POST，请求体和响应体均使用 UTF-8 */
    private byte[] sendHttp(byte[] requestBytes, long timeoutMs) throws Exception {
        URL url = new URL(String.format("http://%s:%d/jsonrpc", host, port));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout((int) Math.min(timeoutMs, Integer.MAX_VALUE));
            conn.setReadTimeout((int) Math.min(timeoutMs, Integer.MAX_VALUE));
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Length", String.valueOf(requestBytes.length));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBytes);
                os.flush();
            }

            int status = conn.getResponseCode();
            InputStream is = (status >= 200 && status < 300)
                    ? conn.getInputStream() : conn.getErrorStream();

            if (is == null) {
                throw new FrameworkException(ErrorCode.INTERNAL_ERROR,
                        "JSON-RPC 服务端返回空响应, HTTP status=" + status);
            }

            return is.readAllBytes();
        } finally {
            conn.disconnect();
        }
    }

    /** 从 JSON-RPC 响应中提取 result 字段，序列化为字节 */
    @SuppressWarnings("unchecked")
    private byte[] extractResult(byte[] responseBytes, long id) throws Exception {
        Map<String, Object> response = objectMapper.readValue(responseBytes, Map.class);

        if (response.containsKey("error")) {
            Map<String, Object> error = (Map<String, Object>) response.get("error");
            int code = error.containsKey("code") ? ((Number) error.get("code")).intValue() : -32603;
            String msg = error.containsKey("message") ? (String) error.get("message") : "Unknown error";
            throw new FrameworkException(mapJsonRpcErrorCode(code), msg);
        }

        Object result = response.get("result");
        if (result == null) {
            return new byte[0];
        }
        return objectMapper.writeValueAsBytes(result);
    }

    private Object deserializePayload(byte[] payload) throws Exception {
        if (payload == null || payload.length == 0) return null;
        return objectMapper.readValue(payload, Object.class);
    }

    private ErrorCode mapJsonRpcErrorCode(int code) {
        return switch (code) {
            case -32700 -> ErrorCode.SERIALIZATION_ERROR;
            case -32600 -> ErrorCode.PROTOCOL_ERROR;
            case -32601 -> ErrorCode.NOT_FOUND;
            case -32602 -> ErrorCode.BAD_REQUEST;
            default     -> ErrorCode.INTERNAL_ERROR;
        };
    }
}
