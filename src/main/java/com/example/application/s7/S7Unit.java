package com.example.application.s7;

import java.util.UUID;

/**
 * Represents a connected Siemens S7 PLC unit.
 */
public class S7Unit {
    
    public enum Status {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }
    
    private final String id;
    private final String host;
    private final int rack;
    private final int slot;
    
    private S7Client client;
    private Status status = Status.DISCONNECTED;
    private String statusMessage = "";
    
    public S7Unit(String host, int rack, int slot) {
        this.id = UUID.randomUUID().toString();
        this.host = host;
        this.rack = rack;
        this.slot = slot;
    }
    
    public String getId() {
        return id;
    }
    
    public String getHost() {
        return host;
    }
    
    public int getRack() {
        return rack;
    }
    
    public int getSlot() {
        return slot;
    }
    
    public S7Client getClient() {
        return client;
    }
    
    public void setClient(S7Client client) {
        this.client = client;
    }
    
    public Status getStatus() {
        return status;
    }
    
    public String getStatusMessage() {
        return statusMessage;
    }
    
    public void setStatus(Status status, String message) {
        this.status = status;
        this.statusMessage = message;
    }
    
    public String getDisplayName() {
        return "S7@" + host + " (R" + rack + "/S" + slot + ")";
    }
    
    @Override
    public String toString() {
        return String.format("S7Unit{id=%s, host=%s, rack=%d, slot=%d, status=%s}",
            id, host, rack, slot, status);
    }
}
