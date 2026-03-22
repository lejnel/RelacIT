package com.example.application.datalog;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JDBC-based logger for relational databases (PostgreSQL, MySQL, etc.).
 * Uses batch inserts for efficient high-frequency logging.
 */
@Component
public class JdbcLogger implements DataLogger {
    
    @Value("${datalog.jdbc.url:}")
    private String jdbcUrl;
    
    @Value("${datalog.jdbc.username:}")
    private String username;
    
    @Value("${datalog.jdbc.password:}")
    private String password;
    
    @Value("${datalog.jdbc.batchsize:100}")
    private int batchSize;
    
    @Value("${datalog.jdbc.flushinterval:5000}")
    private int flushIntervalMs;
    
    private Connection connection;
    private PreparedStatement insertStatement;
    private final BlockingQueue<LogEntry> buffer = new LinkedBlockingQueue<>(50000);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile LoggerStatus status = LoggerStatus.INITIALIZING;
    private final AtomicInteger pendingCount = new AtomicInteger(0);
    
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
     * Initialize the database connection and create tables if needed.
     */
    public synchronized void initialize() throws SQLException {
        if (jdbcUrl == null || jdbcUrl.isEmpty()) {
            System.out.println("JDBC URL not configured, JdbcLogger disabled");
            status = LoggerStatus.ERROR;
            return;
        }
        
        try {
            connection = DriverManager.getConnection(jdbcUrl, username, password);
            connection.setAutoCommit(false);
            
            createTableIfNotExists();
            
            String insertSql = "INSERT INTO plc_data (unit_id, register_addr, value, timestamp) VALUES (?, ?, ?, ?)";
            insertStatement = connection.prepareStatement(insertSql);
            
            status = LoggerStatus.READY;
            executor.submit(this::processBuffer);
            
            System.out.println("JdbcLogger initialized: " + jdbcUrl);
        } catch (SQLException e) {
            status = LoggerStatus.ERROR;
            throw e;
        }
    }
    
    private void createTableIfNotExists() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS plc_data (
                id BIGSERIAL PRIMARY KEY,
                unit_id VARCHAR(64) NOT NULL,
                register_addr INTEGER NOT NULL,
                value INTEGER NOT NULL,
                timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
                created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
            );
            CREATE INDEX IF NOT EXISTS idx_plc_data_unit_time ON plc_data(unit_id, timestamp);
            CREATE INDEX IF NOT EXISTS idx_plc_data_register ON plc_data(unit_id, register_addr);
            """;
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            connection.commit();
        }
    }
    
    private void processBuffer() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                LogEntry entry = buffer.poll(flushIntervalMs, TimeUnit.MILLISECONDS);
                
                if (entry != null) {
                    addToBatch(entry);
                }
                
                if (pendingCount.get() >= batchSize || (entry == null && pendingCount.get() > 0)) {
                    executeBatch();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                flush();
                break;
            } catch (Exception e) {
                System.err.println("Error processing log buffer: " + e.getMessage());
            }
        }
    }
    
    private void addToBatch(LogEntry entry) throws SQLException {
        insertStatement.setString(1, entry.unitId);
        insertStatement.setInt(2, entry.register);
        insertStatement.setInt(3, entry.value);
        insertStatement.setTimestamp(4, Timestamp.from(entry.timestamp));
        insertStatement.addBatch();
        pendingCount.incrementAndGet();
    }
    
    private void executeBatch() throws SQLException {
        if (pendingCount.get() == 0) return;
        
        try {
            insertStatement.executeBatch();
            connection.commit();
            pendingCount.set(0);
        } catch (SQLException e) {
            connection.rollback();
            throw e;
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
        try {
            while (!buffer.isEmpty()) {
                Thread.sleep(10);
            }
            executeBatch();
        } catch (Exception e) {
            System.err.println("Error flushing: " + e.getMessage());
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
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        
        try {
            if (insertStatement != null) insertStatement.close();
            if (connection != null) connection.close();
        } catch (SQLException e) {
            // Ignore
        }
    }
}
