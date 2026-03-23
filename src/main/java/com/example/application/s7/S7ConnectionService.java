package com.example.application.s7;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * Service for managing S7 PLC connections.
 * Similar to ModbusConnectionService but for Siemens PLCs.
 */
@Service
public class S7ConnectionService {
    
    private final Map<String, S7Unit> units = new ConcurrentHashMap<>();
    
    public Collection<S7Unit> listUnits() {
        return units.values();
    }
    
    public S7Unit addUnit(String host, int rack, int slot) {
        S7Unit unit = new S7Unit(host, rack, slot);
        units.put(unit.getId(), unit);
        return unit;
    }
    
    public S7Unit getUnit(String id) {
        return units.get(id);
    }
    
    public void removeUnit(String id) {
        S7Unit unit = units.remove(id);
        if (unit != null && unit.getClient() != null) {
            try {
                unit.getClient().close();
            } catch (Exception ignored) {}
        }
    }
    
    public void connectAsync(S7Unit unit, int timeoutMs) {
        unit.setStatus(S7Unit.Status.CONNECTING, "Connecting");
        CompletableFuture.runAsync(() -> {
            try {
                S7Client client = new S7Client(unit.getHost(), unit.getRack(), unit.getSlot(), timeoutMs);
                client.connect();
                unit.setClient(client);
                unit.setStatus(S7Unit.Status.CONNECTED, "Connected to " + unit.getHost());
            } catch (IOException ex) {
                unit.setStatus(S7Unit.Status.ERROR, "Connect failed: " + ex.getMessage());
            }
        });
    }
    
    public void disconnectAsync(S7Unit unit) {
        unit.setStatus(S7Unit.Status.DISCONNECTED, "Disconnecting");
        CompletableFuture.runAsync(() -> {
            S7Client client = unit.getClient();
            if (client != null) {
                try {
                    client.close();
                } catch (Exception ignored) {}
                unit.setClient(null);
            }
            unit.setStatus(S7Unit.Status.DISCONNECTED, "Disconnected");
        });
    }
}
