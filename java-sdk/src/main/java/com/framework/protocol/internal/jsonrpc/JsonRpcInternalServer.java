package com.framework.protocol.internal.jsonrpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.framework.exception.ErrorCode;
import com.framework.exception.FrameworkException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * JSON-RPC 内部协议服务端实现
 * 
 * 使用 jsonrpc4j 库实现服务端，提供服务注册和请求处理功能
 * 
 * **验证需求: 3.2, 3.5**
 */
public class JsonRpcInternalServer {
    
    private static final Logger logger = LoggerFactory.getLogger(JsonRpcInternalServer.class);
    
    private final int port;
    private final ObjectMapper objectMapper;
    private final Map<String, ServiceHandler> registeredServices;
    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private volatile boolean started;
    private Thread acceptThread;
    
    public JsonRpcInternalServer(int port) {
        this.port = port;
        this.objectMapper = new ObjectMapper();
        this.registeredServices = new ConcurrentHashMap<>();
        this.started = false;
    }
    
    /**
     * 启动 JSON-RPC 服务端
     */
    public void start() throws IOException {
        if (started) {
            logger.warn("JSON-RPC 内部服务端已经启动");
            return;
        }
        
        logger.info("启动 JSON-RPC 内部服务端: port={}", port);
        
        try {
            // 创建服务器套接字
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
            
            // 创建线程池处理请求
            executorService = Executors.newFixedThreadPool(100);
            
            started = true;
            
            // 启动接受连接的线程
            acceptThread = new Thread(this::acceptConnections, "JsonRpc-Accept-Thread");
            acceptThread.setDaemon(false);
            acceptThread.start();
            
            logger.info("JSON-RPC 内部服务端启动成功，监听端口: {}", port);
            
            // 添加关闭钩子
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("JVM 关闭，停止 JSON-RPC 内部服务端");
                try {
                    JsonRpcInternalServer.this.shutdown();
                } catch (InterruptedException e) {
                    logger.error("关闭 JSON-RPC 内部服务端时被中断", e);
                    Thread.currentThread().interrupt();
                }
            }));
            
        } catch (IOException e) {
            logger.error("JSON-RPC 内部服务端启动失败", e);
            throw e;
        }
    }
    
    /**
     * 接受客户端连接
     */
    private void acceptConnections() {
        logger.info("开始接受 JSON-RPC 连接");
        
        while (started && !Thread.currentThread().isInterrupted()) {
            try {
                Socket clientSocket = serverSocket.accept();
                logger.debug("接受到新的 JSON-RPC 连接: {}", clientSocket.getRemoteSocketAddress());
                
                // 提交到线程池处理
                executorService.submit(() -> handleClient(clientSocket));
                
            } catch (IOException e) {
                if (started) {
                    logger.error("接受连接失败", e);
                }
            }
        }
        
        logger.info("停止接受 JSON-RPC 连接");
    }
    
    /**
     * 处理客户端请求
     */
    private void handleClient(Socket clientSocket) {
        try {
            // 设置超时
            clientSocket.setSoTimeout(30000); // 30 秒
            
            InputStream inputStream = clientSocket.getInputStream();
            OutputStream outputStream = clientSocket.getOutputStream();
            
            // 读取请求
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder requestBuilder = new StringBuilder();
            String line;
            int contentLength = 0;
            
            // 读取 HTTP 头（如果有）
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (line.startsWith("Content-Length:")) {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                }
            }
            
            // 读取请求体
            if (contentLength > 0) {
                char[] buffer = new char[contentLength];
                reader.read(buffer, 0, contentLength);
                requestBuilder.append(buffer);
            } else {
                // 如果没有 Content-Length，读取到流结束
                while (reader.ready() && (line = reader.readLine()) != null) {
                    requestBuilder.append(line);
                }
            }
            
            String requestBody = requestBuilder.toString();
            logger.debug("收到 JSON-RPC 请求: {}", requestBody);
            
            // 处理 JSON-RPC 请求
            String responseBody = handleJsonRpcRequest(requestBody);
            
            // 发送 HTTP 响应
            PrintWriter writer = new PrintWriter(outputStream, true);
            writer.println("HTTP/1.1 200 OK");
            writer.println("Content-Type: application/json");
            writer.println("Content-Length: " + responseBody.length());
            writer.println();
            writer.print(responseBody);
            writer.flush();
            
        } catch (Exception e) {
            logger.error("处理 JSON-RPC 请求失败", e);
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                logger.error("关闭客户端连接失败", e);
            }
        }
    }
    
    /**
     * 处理 JSON-RPC 请求
     */
    private String handleJsonRpcRequest(String requestBody) {
        try {
            // 解析 JSON-RPC 请求
            @SuppressWarnings("unchecked")
            Map<String, Object> request = objectMapper.readValue(requestBody, Map.class);
            
            // 验证 JSON-RPC 版本
            if (!"2.0".equals(request.get("jsonrpc"))) {
                return buildErrorResponse(request.get("id"), -32600, "Invalid Request", null);
            }
            
            // 获取方法名和参数
            String method = (String) request.get("method");
            Object params = request.get("params");
            Object id = request.get("id");
            
            if (method == null) {
                return buildErrorResponse(id, -32600, "Invalid Request", null);
            }
            
            // 健康检查
            if ("health.check".equals(method)) {
                Map<String, Object> result = new HashMap<>();
                result.put("status", "healthy");
                result.put("timestamp", System.currentTimeMillis());
                result.put("services", registeredServices.keySet());
                return buildSuccessResponse(id, result);
            }
            
            // 解析服务名和方法名
            String[] parts = method.split("\\.", 2);
            if (parts.length != 2) {
                return buildErrorResponse(id, -32601, "Method not found", null);
            }
            
            String serviceName = parts[0];
            String serviceMethod = parts[1];
            
            // 查找服务处理器
            ServiceHandler handler = registeredServices.get(serviceName);
            if (handler == null) {
                return buildErrorResponse(id, -32601, "Method not found: " + serviceName, null);
            }
            
            // 序列化参数
            byte[] payload = params != null ? objectMapper.writeValueAsBytes(params) : new byte[0];
            
            // 调用服务处理器
            Map<String, String> headers = new ConcurrentHashMap<>();
            byte[] resultBytes = handler.handle(serviceMethod, payload, headers);
            
            // 反序列化结果
            Object result = null;
            if (resultBytes != null && resultBytes.length > 0) {
                result = objectMapper.readValue(resultBytes, Object.class);
            }
            
            return buildSuccessResponse(id, result);
            
        } catch (FrameworkException e) {
            logger.error("处理 JSON-RPC 请求失败", e);
            return buildErrorResponse(null, -32603, e.getMessage(), null);
        } catch (Exception e) {
            logger.error("处理 JSON-RPC 请求异常", e);
            return buildErrorResponse(null, -32603, "Internal error: " + e.getMessage(), null);
        }
    }
    
    /**
     * 构建成功响应
     */
    private String buildSuccessResponse(Object id, Object result) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("result", result);
            response.put("id", id);
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            logger.error("构建响应失败", e);
            return buildErrorResponse(id, -32603, "Internal error", null);
        }
    }
    
    /**
     * 构建错误响应
     */
    private String buildErrorResponse(Object id, int code, String message, Object data) {
        try {
            Map<String, Object> error = new HashMap<>();
            error.put("code", code);
            error.put("message", message);
            if (data != null) {
                error.put("data", data);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("error", error);
            response.put("id", id);
            
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            logger.error("构建错误响应失败", e);
            return "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"Internal error\"},\"id\":null}";
        }
    }
    
    /**
     * 关闭 JSON-RPC 服务端
     */
    public void shutdown() throws InterruptedException {
        if (!started) {
            logger.warn("JSON-RPC 内部服务端未启动");
            return;
        }
        
        logger.info("关闭 JSON-RPC 内部服务端");
        
        started = false;
        
        try {
            // 关闭服务器套接字
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            
            // 等待接受线程结束
            if (acceptThread != null) {
                acceptThread.interrupt();
                acceptThread.join(5000);
            }
            
            // 关闭线程池
            if (executorService != null) {
                executorService.shutdown();
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            }
            
            logger.info("JSON-RPC 内部服务端已关闭");
            
        } catch (Exception e) {
            logger.error("关闭 JSON-RPC 内部服务端失败", e);
            throw new FrameworkException(ErrorCode.INTERNAL_ERROR, 
                                        "关闭 JSON-RPC 内部服务端失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 注册服务处理器
     * 
     * @param serviceName 服务名称
     * @param handler 服务处理器
     */
    public void registerService(String serviceName, ServiceHandler handler) {
        if (serviceName == null || serviceName.trim().isEmpty()) {
            throw new FrameworkException(ErrorCode.BAD_REQUEST, "服务名称不能为空");
        }
        
        if (handler == null) {
            throw new FrameworkException(ErrorCode.BAD_REQUEST, "服务处理器不能为空");
        }
        
        logger.info("注册 JSON-RPC 内部服务: {}", serviceName);
        registeredServices.put(serviceName, handler);
    }
    
    /**
     * 注销服务
     * 
     * @param serviceName 服务名称
     */
    public void unregisterService(String serviceName) {
        logger.info("注销 JSON-RPC 内部服务: {}", serviceName);
        registeredServices.remove(serviceName);
    }
    
    /**
     * 服务处理器接口
     */
    public interface ServiceHandler {
        /**
         * 处理服务调用
         * 
         * @param method 方法名称
         * @param payload 请求负载
         * @param headers 请求头
         * @return 响应负载
         */
        byte[] handle(String method, byte[] payload, Map<String, String> headers);
    }
}
