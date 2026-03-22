package com.example.application.datalog;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Local file-based data logger for short-term logging.
 * Writes CSV files organized by date and unit.
 * 
 * File structure:
 *   ./logs/{unitId}/{date}.csv
 * 
 * CSV format:
 *   timestamp,register,value
 */
@Component
public class LocalFileLogger implements DataLogger {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    
    @Value("${datalog.local.basepath:./logs}")
    private String basePath;
    
    @Value("${datalog.local.maxsizemb:100}")
    private int maxSizeMB;
    
    @Value("${datalog.local.retentiondays:30}")
    private int retentionDays;
    
    private final BlockingQueue<LogEntry> buffer = new LinkedBlockingQueue<>(10000);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile LoggerStatus status = LoggerStatus.INITIALIZING;
    private final AtomicLong bytesWritten = new AtomicLong(0);
    
    private static class LogEntry {
        final String unitId;
        final int register;
        final int value;
        final Instant timestamp;
        
        LogEntry(String unitId, int register, int value, Instant timestamp) {
            this.unitId = unitId;
            this.register = register;
            this.value = value;
            this.timestamp = timestamp;
        }
    }
    
    public void start() {
        status = LoggerStatus.READY;
        executor.submit(this::processBuffer);
        cleanOldLogs();
    }
    
    private void processBuffer() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                LogEntry entry = buffer.poll(1, TimeUnit.SECONDS);
                if (entry != null) {
                    writeEntry(entry);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Error writing log entry: " + e.getMessage());
            }
        }
    }
    
    private void writeEntry(LogEntry entry) {
        try {
            Path filePath = getLogFilePath(entry.unitId, entry.timestamp);
            Files.createDirectories(filePath.getParent());
            
            boolean fileExists = Files.exists(filePath);
            String line = formatLine(entry);
            
            try (BufferedWriter writer = Files.newBufferedWriter(filePath, 
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                if (!fileExists) {
                    writer.write("timestamp,register,value\n");
                }
                writer.write(line);
                writer.newLine();
            }
            
            bytesWritten.addAndGet(line.length() + 1);
        } catch (IOException e) {
            System.err.println("Failed to write log entry: " + e.getMessage());
        }
    }
    
    private Path getLogFilePath(String unitId, Instant timestamp) {
        String date = LocalDate.ofInstant(timestamp, ZoneId.systemDefault()).format(DATE_FORMATTER);
        String safeUnitId = unitId.replaceAll("[^a-zA-Z0-9_-]", "_");
        return Paths.get(basePath, safeUnitId, date + ".csv");
    }
    
    private String formatLine(LogEntry entry) {
        String time = TIME_FORMATTER.format(entry.timestamp.atZone(ZoneId.systemDefault()));
        return String.format("%s,%d,%d", time, entry.register, entry.value);
    }
    
    private void cleanOldLogs() {
        try {
            Path logDir = Paths.get(basePath);
            if (!Files.exists(logDir)) return;
            
            LocalDate cutoff = LocalDate.now().minusDays(retentionDays);
            
            Files.walk(logDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".csv"))
                .forEach(p -> {
                    try {
                        String filename = p.getFileName().toString().replace(".csv", "");
                        LocalDate fileDate = LocalDate.parse(filename, DATE_FORMATTER);
                        if (fileDate.isBefore(cutoff)) {
                            Files.delete(p);
                            System.out.println("Deleted old log file: " + p);
                        }
                    } catch (Exception e) {
                        // Ignore parse errors
                    }
                });
        } catch (IOException e) {
            System.err.println("Error cleaning old logs: " + e.getMessage());
        }
    }
    
    @Override
    public void logDataPoint(String unitId, int registerAddress, int value, Instant timestamp) {
        if (status != LoggerStatus.READY && status != LoggerStatus.LOGGING) {
            return;
        }
        status = LoggerStatus.LOGGING;
        buffer.offer(new LogEntry(unitId, registerAddress, value, timestamp));
    }
    
    @Override
    public void logBatch(Map<Integer, Integer> dataPoints, String unitId, Instant timestamp) {
        dataPoints.forEach((reg, val) -> 
            logDataPoint(unitId, reg, val, timestamp));
    }
    
    @Override
    public void flush() {
        // Wait for buffer to empty
        while (!buffer.isEmpty()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    @Override
    public LoggerStatus getStatus() {
        return status;
    }
    
    @Override
    public void close() {
        status = LoggerStatus.CLOSED;
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
    
    public long getBytesWritten() {
        return bytesWritten.get();
    }
    
    public int getBufferSize() {
        return buffer.size();
    }
}
