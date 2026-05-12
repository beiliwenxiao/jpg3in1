package com.example.monitor.server.udp;

import com.example.monitor.server.model.MonitorRecord;
import com.example.monitor.server.storage.StorageInterface;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

/**
 * UDP 数据接收器。
 *
 * <p>监听 UDP 端口，接收客户端发送的 JSON 性能数据，写入 StorageInterface。
 * 协议格式：每条记录一行 JSON，多条用 \n 分隔，支持单个包内批量传输。</p>
 */
public class UdpReceiver {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int BUFFER_SIZE = 65535; // UDP 单包最大长度

    private final int port;
    private final StorageInterface storage;
    private volatile DatagramSocket socket;
    private volatile Thread thread;
    private volatile boolean running;

    public UdpReceiver(int port, StorageInterface storage) {
        this.port = port;
        this.storage = storage;
    }

    /** 在独立线程中启动 UDP 监听。 */
    public void start() {
        if (running) return;
        try {
            socket = new DatagramSocket(port);
        } catch (Exception e) {
            throw new RuntimeException("UDP 监听失败: " + e.getMessage(), e);
        }
        running = true;
        thread = new Thread(this::loop, "api-monitor-udp-receiver");
        thread.setDaemon(true);
        thread.start();
        System.out.println("[监控] UDP 接收器启动，监听端口 " + port);
    }

    private void loop() {
        byte[] buf = new byte[BUFFER_SIZE];
        while (running && socket != null && !socket.isClosed()) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                socket.receive(packet);
                if (packet.getLength() > 0) {
                    byte[] data = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), 0, data, 0, packet.getLength());
                    handleMessage(data);
                }
            } catch (Exception e) {
                if (running) {
                    // 非主动关闭时的异常，继续循环
                    try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                }
            }
        }
    }

    /** 解析一个 UDP 数据包，支持换行分隔的批量 JSON。 */
    void handleMessage(byte[] data) {
        String text = new String(data, StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) return;

        String[] lines = text.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            MonitorRecord record;
            try {
                record = MAPPER.readValue(trimmed, MonitorRecord.class);
            } catch (Exception e) {
                continue; // JSON 解析失败，跳过
            }

            // 验证必要字段
            if (isEmpty(record.getProject()) || isEmpty(record.getClazz())
                    || isEmpty(record.getMethod()) || isEmpty(record.getUri())) {
                continue;
            }

            try {
                storage.record(record);
            } catch (Exception ignored) {}
        }
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    public void stop() {
        running = false;
        if (socket != null) {
            try { socket.close(); } catch (Exception ignored) {}
            socket = null;
        }
        if (thread != null) {
            try { thread.join(1000); } catch (InterruptedException ignored) {}
            thread = null;
        }
    }
}
