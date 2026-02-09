package com.framework.model;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务信息
 */
public class ServiceInfo {
    
    private String id;
    private String name;
    private String version;
    private String language;
    private String address;
    private int port;
    private List<String> protocols;
    private Map<String, String> metadata;
    private Date registeredAt;
    
    public ServiceInfo() {
        this.metadata = new HashMap<>();
    }
    
    public ServiceInfo(String id, String name, String version, String language, 
                      String address, int port, List<String> protocols) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.language = language;
        this.address = address;
        this.port = port;
        this.protocols = protocols;
        this.metadata = new HashMap<>();
        this.registeredAt = new Date();
    }
    
    // Getters and Setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getLanguage() {
        return language;
    }
    
    public void setLanguage(String language) {
        this.language = language;
    }
    
    public String getAddress() {
        return address;
    }
    
    public void setAddress(String address) {
        this.address = address;
    }
    
    public int getPort() {
        return port;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
    
    public List<String> getProtocols() {
        return protocols;
    }
    
    public void setProtocols(List<String> protocols) {
        this.protocols = protocols;
    }
    
    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }
    
    public Date getRegisteredAt() {
        return registeredAt;
    }
    
    public void setRegisteredAt(Date registeredAt) {
        this.registeredAt = registeredAt;
    }
}
