package com.example.application;

import java.time.Instant;
import java.util.UUID;

public class ModbusUnit {
    public enum Status {DISCONNECTED, CONNECTING, CONNECTED, ERROR}
    
    private final String id = UUID.randomUUID().toString();
    private final String host;
    private final int port;
    private final int unitId;
    
    private volatile Status status = Status.DISCONNECTED;
    private volatile String lastMessage;
    private volatile Instant lastUpdated;
    
    // transient runtime client (not serialized)
    private transient ModbusTcpClient client;
    
    public ModbusUnit(String host, int port, int unitId) {
        this.host = host;
        this.port = port;
        this.unitId = unitId;
        this.lastUpdated = Instant.now();
    }
    
    public String getId() { return id; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public int getUnitId() { return unitId; }
    public Status getStatus() { return status; }
    public String getLastMessage() { return lastMessage; }
    public Instant getLastUpdated() { return lastUpdated; }
    
    public String getDisplayName() {
        return "Modbus@" + host + ":" + port;
    }
    
    public ModbusTcpClient getClient() { return client; }
    
    void setClient(ModbusTcpClient c) { this.client = c; }
    
    synchronized void setStatus(Status s, String msg) {
        this.status = s;
        this.lastMessage = msg;
        this.lastUpdated = Instant.now();
    }
}
