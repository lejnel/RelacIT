package com.example.application.s7;

import com.example.application.datalog.DataLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Polling service for Siemens S7 PLCs.
 * Supports DB (Data Blocks), M (Memory), I (Inputs), O (Outputs).
 */
@Service
public class S7PollingService {
    
    private final S7ConnectionService connectionService;
    private final List<DataLogger> loggers;
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<String, ScheduledFuture<?>> activePolls = new ConcurrentHashMap<>();
    private final Map<String, PollConfig> configs = new ConcurrentHashMap<>();
    
    @Autowired
    public S7PollingService(S7ConnectionService connectionService, List<DataLogger> loggers) {
        this.connectionService = connectionService;
        this.loggers = loggers;
    }
    
    public void configurePolling(String unitId, List<DataAddress> addresses, int intervalMs) {
        PollConfig config = new PollConfig(unitId, addresses, intervalMs);
        configs.put(unitId, config);
    }
    
    public void startPolling(String unitId) {
        PollConfig config = configs.get(unitId);
        if (config == null) {
            throw new IllegalArgumentException("No configuration for unit: " + unitId);
        }
        
        stopPolling(unitId);
        
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
            () -> pollUnit(unitId, config),
            0,
            config.intervalMs,
            TimeUnit.MILLISECONDS
        );
        
        activePolls.put(unitId, future);
        System.out.println("Started S7 polling unit " + unitId + " every " + config.intervalMs + "ms");
    }
    
    public void stopPolling(String unitId) {
        ScheduledFuture<?> future = activePolls.remove(unitId);
        if (future != null) {
            future.cancel(false);
            System.out.println("Stopped S7 polling unit " + unitId);
        }
    }
    
    public void stopAll() {
        activePolls.keySet().forEach(this::stopPolling);
    }
    
    public Map<String, String> getPollingStatus() {
        Map<String, String> status = new HashMap<>();
        configs.keySet().forEach(unitId -> {
            status.put(unitId, activePolls.containsKey(unitId) ? "ACTIVE" : "STOPPED");
        });
        return status;
    }
    
    private void pollUnit(String unitId, PollConfig config) {
        S7Unit unit = connectionService.getUnit(unitId);
        if (unit == null || unit.getClient() == null || !unit.getClient().isConnected()) {
            return;
        }
        
        if (unit.getStatus() != S7Unit.Status.CONNECTED) {
            return;
        }
        
        try {
            Map<Integer, Integer> values = new HashMap<>();
            
            for (DataAddress addr : config.addresses) {
                try {
                    byte[] data = unit.getClient().readArea(
                        addr.area,
                        addr.dbNumber,
                        addr.startByte,
                        addr.length,
                        S7Client.S7WL_BYTE
                    );
                    
                    // Convert to integer(s)
                    for (int i = 0; i < addr.length; i++) {
                        values.put(addr.startByte + i, data[i] & 0xFF);
                    }
                    
                } catch (Exception e) {
                    System.err.println("Error reading S7 address " + addr + ": " + e.getMessage());
                }
            }
            
            // Log to all configured loggers
            if (!values.isEmpty()) {
                Instant timestamp = Instant.now();
                for (DataLogger logger : loggers) {
                    if (logger.getStatus() == DataLogger.LoggerStatus.READY ||
                        logger.getStatus() == DataLogger.LoggerStatus.LOGGING) {
                        logger.logBatch(values, unitId, timestamp);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("S7 polling error for unit " + unitId + ": " + e.getMessage());
        }
    }
    
    public void shutdown() {
        stopAll();
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
    
    /**
     * S7 data address specification.
     */
    public static class DataAddress {
        public final byte area;      // S7AREA_DB, S7AREA_MK, etc.
        public final int dbNumber;    // DB number (0 for non-DB areas)
        public final int startByte;   // Starting byte address
        public final int length;      // Number of bytes
        
        public DataAddress(byte area, int dbNumber, int startByte, int length) {
            this.area = area;
            this.dbNumber = dbNumber;
            this.startByte = startByte;
            this.length = length;
        }
        
        /**
         * Create DB address (e.g., DB1.DBW0).
         */
        public static DataAddress DB(int dbNumber, int startByte, int length) {
            return new DataAddress(S7Client.S7AREA_DB, dbNumber, startByte, length);
        }
        
        /**
         * Create memory address (e.g., MW0).
         */
        public static DataAddress M(int startByte, int length) {
            return new DataAddress(S7Client.S7AREA_MK, 0, startByte, length);
        }
        
        /**
         * Create input address (e.g., IW0).
         */
        public static DataAddress I(int startByte, int length) {
            return new DataAddress(S7Client.S7AREA_PE, 0, startByte, length);
        }
        
        /**
         * Create output address (e.g., QW0).
         */
        public static DataAddress Q(int startByte, int length) {
            return new DataAddress(S7Client.S7AREA_PA, 0, startByte, length);
        }
        
        @Override
        public String toString() {
            String areaName;
            if (area == S7Client.S7AREA_DB) {
                areaName = "DB" + dbNumber;
            } else if (area == S7Client.S7AREA_MK) {
                areaName = "M";
            } else if (area == S7Client.S7AREA_PE) {
                areaName = "I";
            } else if (area == S7Client.S7AREA_PA) {
                areaName = "Q";
            } else {
                areaName = "?";
            }
            return areaName + ".DBB" + startByte + " (" + length + " bytes)";
        }
    }
    
    private static class PollConfig {
        final String unitId;
        final List<DataAddress> addresses;
        final int intervalMs;
        
        PollConfig(String unitId, List<DataAddress> addresses, int intervalMs) {
            this.unitId = unitId;
            this.addresses = Collections.unmodifiableList(new ArrayList<>(addresses));
            this.intervalMs = Math.max(100, intervalMs);
        }
    }
}
