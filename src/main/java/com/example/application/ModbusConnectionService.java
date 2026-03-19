package com.example.application;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ModbusConnectionService {

    private final Map<String, ModbusUnit> units = new ConcurrentHashMap<>();

    public Collection<ModbusUnit> listUnits() {
        return units.values();
    }

    public ModbusUnit addUnit(String host, int port, int unitId) {
        ModbusUnit u = new ModbusUnit(host, port, unitId);
        units.put(u.getId(), u);
        return u;
    }

    public ModbusUnit getUnit(String id) { return units.get(id); }

    public void removeUnit(String id) {
        ModbusUnit u = units.remove(id);
        if (u != null && u.getClient() != null) {
            try { u.getClient().close(); } catch (Exception ignored) {}
        }
    }

    public void connectAsync(ModbusUnit unit, int timeoutMs) {
        unit.setStatus(ModbusUnit.Status.CONNECTING, "Connecting");
        CompletableFuture.runAsync(() -> {
            try {
                ModbusTcpClient c = new ModbusTcpClient(unit.getHost(), unit.getPort(), timeoutMs);
                unit.setClient(c);
                unit.setStatus(ModbusUnit.Status.CONNECTED, "Connected");
            } catch (IOException ex) {
                unit.setStatus(ModbusUnit.Status.ERROR, "Connect failed: " + ex.getMessage());
            }
        });
    }

    public void disconnectAsync(ModbusUnit unit) {
        unit.setStatus(ModbusUnit.Status.DISCONNECTED, "Disconnecting");
        CompletableFuture.runAsync(() -> {
            ModbusTcpClient c = unit.getClient();
            if (c != null) {
                try {
                    c.close();
                } catch (Exception ignored) {}
                unit.setClient(null);
            }
            unit.setStatus(ModbusUnit.Status.DISCONNECTED, "Disconnected");
        });
    }
}
