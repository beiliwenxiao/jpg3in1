package com.framework.protocol.router;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 服务端点
 * 
 * 表示一个可路由的目标服务实例
 */
public class ServiceEndpoint {
    
    private String serviceId;
    private String serviceName;
    private String address;
    private int port;
    private String protocol;  // 内部协议: gRPC, JSON-RPC, Custom
    private Map<String, String> metadata;
    
    public ServiceEndpoint() {
        this.metadata = new HashMap<>();
    }
    
    public ServiceEndpoint(String serviceId, String serviceName, String address, int port, String protocol) {
        this.serviceId = serviceId;
        this.serviceName = serviceName;
        this.address = address;
        this.port = port;
        this.protocol = protocol;
        this.metadata = new HashMap<>();
    }
    
    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }
    
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    
    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }
    
    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceEndpoint that = (ServiceEndpoint) o;
        return port == that.port && 
               Objects.equals(serviceId, that.serviceId) && 
               Objects.equals(address, that.address);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(serviceId, address, port);
    }
    
    @Override
    public String toString() {
        return "ServiceEndpoint{" +
               "serviceId='" + serviceId + '\'' +
               ", serviceName='" + serviceName + '\'' +
               ", address='" + address + '\'' +
               ", port=" + port +
               ", protocol='" + protocol + '\'' +
               '}';
    }
}
