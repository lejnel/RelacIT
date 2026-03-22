package com.example.application.datalog;

import com.example.application.ModbusConnectionService;
import com.example.application.ModbusUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Service for polling PLC registers at configurable intervals.
 * Supports multiple units with different polling rates.
 */
@Service
public class PlcPollingService {
    
    private final ModbusConnectionService connectionService;
    private final List<DataLogger> loggers;
    
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<String, ScheduledFuture<?>> activePolls = new ConcurrentHashMap<>();
    private final Map<String, PollConfig> configs = new ConcurrentHashMap<>();
    
    @Autowired
    public PlcPollingService(ModbusConnectionService connectionService, List<DataLogger> loggers) {
        this.connectionService = connectionService;
        this.loggers = loggers;
    }
    
    /**
     * Configure polling for a unit.
     */
    public void configurePolling(String unitId, List<Integer> registers, int intervalMs) {
        PollConfig config = new PollConfig(unitId, registers, intervalMs);
        configs.put(unitId, config);
    }
    
    /**
     * Start polling a configured unit.
     */
    public void startPolling(String unitId) {
        PollConfig config = configs.get(unitId);
        if (config == null) {
            throw new IllegalArgumentException("No configuration for unit: " + unitId);
        }
        
        stopPolling(unitId); // Stop any existing poll
        
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
            () -> pollUnit(unitId, config),
            0,
            config.intervalMs,
            TimeUnit.MILLISECONDS
        );
        
        activePolls.put(unitId, future);
        System.out.println("Started polling unit " + unitId + " every " + config.intervalMs + "ms");
    }
    
    /**
     * Stop polling a unit.
     */
    public void stopPolling(String unitId) {
        ScheduledFuture<?> future = activePolls.remove(unitId);
        if (future != null) {
            future.cancel(false);
            System.out.println("Stopped polling unit " + unitId);
        }
    }
    
    /**
     * Stop all polling.
     */
    public void stopAll() {
        activePolls.keySet().forEach(this::stopPolling);
    }
    
    /**
     * Get active polling status.
     */
    public Map<String, String> getPollingStatus() {
        Map<String, String> status = new HashMap<>();
        configs.keySet().forEach(unitId -> {
            status.put(unitId, activePolls.containsKey(unitId) ? "ACTIVE" : "STOPPED");
        });
        return status;
    }
    
    private void pollUnit(String unitId, PollConfig config) {
        ModbusUnit unit = connectionService.getUnit(unitId);
        if (unit == null || unit.getClient() == null) {
            return;
        }
        
        if (unit.getStatus() != ModbusUnit.Status.CONNECTED) {
            return;
        }
        
        try {
            // Read all configured registers
            int[] addresses = config.registers.stream().mapToInt(Integer::intValue).toArray();
            Map<Integer, Integer> values = new HashMap<>();
            
            for (int addr : addresses) {
                try {
                    int[] regs = unit.getClient().readHoldingRegisters(unit.getUnitId(), addr, 1);
                    if (regs.length > 0) {
                        values.put(addr, regs[0]);
                    }
                } catch (Exception e) {
                    System.err.println("Error reading register " + addr + " from unit " + unitId + ": " + e.getMessage());
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
            System.err.println("Polling error for unit " + unitId + ": " + e.getMessage());
        }
    }
    
    /**
     * Shutdown the service.
     */
    public void shutdown() {
        stopAll();
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        
        for (DataLogger logger : loggers) {
            try {
                logger.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }
    
    private static class PollConfig {
        final String unitId;
        final List<Integer> registers;
        final int intervalMs;
        
        PollConfig(String unitId, List<Integer> registers, int intervalMs) {
            this.unitId = unitId;
            this.registers = Collections.unmodifiableList(new ArrayList<>(registers));
            this.intervalMs = Math.max(100, intervalMs); // Minimum 100ms
        }
    }
}
