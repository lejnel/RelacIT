package com.example.application.datalog;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Flow;

/**
 * Interface for data logging implementations.
 * Supports both local file-based and database logging.
 */
public interface DataLogger extends AutoCloseable {
    
    /**
     * Log a single data point from a PLC register.
     * 
     * @param unitId The Modbus unit identifier
     * @param registerAddress The register address
     * @param value The register value
     * @param timestamp When the value was read
     */
    void logDataPoint(String unitId, int registerAddress, int value, Instant timestamp);
    
    /**
     * Log multiple data points in a batch.
     * More efficient for high-frequency logging.
     * 
     * @param dataPoints Map of register addresses to values
     * @param unitId The Modbus unit identifier
     * @param timestamp When the values were read
     */
    void logBatch(Map<Integer, Integer> dataPoints, String unitId, Instant timestamp);
    
    /**
     * Flush any buffered data to storage.
     */
    void flush();
    
    /**
     * Get the logger status.
     */
    LoggerStatus getStatus();
    
    /**
     * Logger operational status.
     */
    enum LoggerStatus {
        INITIALIZING,
        READY,
        LOGGING,
        ERROR,
        CLOSED
    }
}
