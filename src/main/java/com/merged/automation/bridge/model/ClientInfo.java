package com.merged.automation.bridge.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Information about connected clients
 */
public class ClientInfo {
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("type")
    private ClientType type;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("version")
    private String version;
    
    @JsonProperty("connectedAt")
    private long connectedAt;
    
    @JsonProperty("lastActivity")
    private long lastActivity;
    
    @JsonProperty("status")
    private ClientStatus status;
    
    public ClientInfo() {
        this.connectedAt = System.currentTimeMillis();
        this.lastActivity = this.connectedAt;
        this.status = ClientStatus.CONNECTED;
    }
    
    public ClientInfo(String id, ClientType type, String name) {
        this();
        this.id = id;
        this.type = type;
        this.name = name;
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public ClientType getType() { return type; }
    public void setType(ClientType type) { this.type = type; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    
    public long getConnectedAt() { return connectedAt; }
    public void setConnectedAt(long connectedAt) { this.connectedAt = connectedAt; }
    
    public long getLastActivity() { return lastActivity; }
    public void setLastActivity(long lastActivity) { this.lastActivity = lastActivity; }
    
    public ClientStatus getStatus() { return status; }
    public void setStatus(ClientStatus status) { this.status = status; }
    
    public void updateLastActivity() {
        this.lastActivity = System.currentTimeMillis();
    }
    
    // Client types
    public enum ClientType {
        AUTOMATION_SERVICE, AUTOMATION_SCHEDULER, ADMIN
    }
    
    // Client status
    public enum ClientStatus {
        CONNECTING, CONNECTED, BUSY, IDLE, RECONNECTING, DISCONNECTED
    }
}