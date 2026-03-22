package com.example.application.datalog;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * Supabase REST API logger for cloud database storage.
 * Uses Supabase PostgREST API for direct table inserts.
 */
@Component
public class SupabaseLogger implements DataLogger {
    
    @Value("${datalog.supabase.url:}")
    private String supabaseUrl;
    
    @Value("${datalog.supabase.apikey:}")
    private String apiKey;
    
    @Value("${datalog.supabase.table:plc_data}")
    private String tableName;
    
    @Value("${datalog.supabase.batchsize:50}")
    private int batchSize;
    
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final BlockingQueue<LogEntry> buffer = new LinkedBlockingQueue<>(10000);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile LoggerStatus status = LoggerStatus.INITIALIZING;
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_INSTANT;
    
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
    
    /**
     * Initialize and verify Supabase connection.
     */
    public void initialize() {
        if (supabaseUrl == null || supabaseUrl.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            System.out.println("Supabase not configured, SupabaseLogger disabled");
            status = LoggerStatus.ERROR;
            return;
        }
        
        try {
            // Verify connection with a simple query
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(supabaseUrl + "/rest/v1/" + tableName + "?limit=1"))
                .header("apikey", apiKey)
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200 || response.statusCode() == 206) {
                status = LoggerStatus.READY;
                executor.submit(this::processBuffer);
                System.out.println("SupabaseLogger initialized: " + supabaseUrl);
            } else if (response.statusCode() == 404) {
                // Table doesn't exist - try to create it
                createTable();
                status = LoggerStatus.READY;
                executor.submit(this::processBuffer);
            } else {
                System.err.println("Supabase connection failed: " + response.statusCode());
                status = LoggerStatus.ERROR;
            }
        } catch (Exception e) {
            System.err.println("Supabase initialization error: " + e.getMessage());
            status = LoggerStatus.ERROR;
        }
    }
    
    private void createTable() {
        // Note: This requires Supabase SQL API or manual table creation
        System.out.println("Please create table in Supabase SQL editor:");
        System.out.println("""
            CREATE TABLE plc_data (
                id BIGSERIAL PRIMARY KEY,
                unit_id VARCHAR(64) NOT NULL,
                register_addr INTEGER NOT NULL,
                value INTEGER NOT NULL,
                timestamp TIMESTAMPTZ NOT NULL,
                created_at TIMESTAMPTZ DEFAULT NOW()
            );
            CREATE INDEX idx_plc_data_unit_time ON plc_data(unit_id, timestamp);
            """);
    }
    
    private void processBuffer() {
        List<LogEntry> batch = new ArrayList<>(batchSize);
        
        while (!Thread.currentThread().isInterrupted()) {
            try {
                LogEntry entry = buffer.poll(1, TimeUnit.SECONDS);
                
                if (entry != null) {
                    batch.add(entry);
                }
                
                if (batch.size() >= batchSize || (!batch.isEmpty() && entry == null)) {
                    sendBatch(batch);
                    batch.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (!batch.isEmpty()) {
                    sendBatch(batch);
                }
                break;
            } catch (Exception e) {
                System.err.println("Error sending to Supabase: " + e.getMessage());
            }
        }
    }
    
    private void sendBatch(List<LogEntry> batch) {
        if (batch.isEmpty()) return;
        
        try {
            // Build JSON array
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < batch.size(); i++) {
                LogEntry e = batch.get(i);
                if (i > 0) json.append(",");
                json.append(String.format(
                    "{\"unit_id\":\"%s\",\"register_addr\":%d,\"value\":%d,\"timestamp\":\"%s\"}",
                    e.unitId, e.register, e.value, ISO_FORMATTER.format(e.timestamp)
                ));
            }
            json.append("]");
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(supabaseUrl + "/rest/v1/" + tableName))
                .header("apikey", apiKey)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Prefer", "return=minimal")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 400) {
                System.err.println("Supabase insert failed: " + response.statusCode() + " - " + response.body());
            }
        } catch (Exception e) {
            System.err.println("Error sending batch to Supabase: " + e.getMessage());
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
        while (!buffer.isEmpty()) {
            try {
                Thread.sleep(50);
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
}
