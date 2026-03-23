package com.example.application.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;
import java.util.UUID;

/**
 * Manages the admin account for local installation.
 * Admin credentials are stored in a local file.
 */
@Component
public class AdminAccountManager {
    
    private static final String CONFIG_DIR = System.getProperty("user.home", ".") + "/.relacit";
    private static final String ADMIN_FILE = CONFIG_DIR + "/admin.properties";
    private static final String SALT_FILE = CONFIG_DIR + "/salt.properties";
    
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    private String username;
    private String passwordHash;
    private String salt;
    private boolean initialized = false;
    
    public AdminAccountManager() {
        loadAdminConfig();
    }
    
    /**
     * Check if admin account exists.
     */
    public boolean isAdminCreated() {
        return initialized && username != null && passwordHash != null;
    }
    
    /**
     * Create admin account (only allowed if none exists).
     */
    public synchronized boolean createAdminAccount(String username, String password) {
        if (isAdminCreated()) {
            throw new IllegalStateException("Admin account already exists. Cannot create another.");
        }
        
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        
        // Generate salt
        this.salt = UUID.randomUUID().toString();
        this.username = username.trim();
        this.passwordHash = passwordEncoder.encode(password + salt);
        
        // Save to file
        saveAdminConfig();
        this.initialized = true;
        
        System.out.println("Admin account created: " + username);
        return true;
    }
    
    /**
     * Validate login credentials.
     */
    public boolean validateLogin(String username, String password) {
        if (!isAdminCreated()) {
            return false;
        }
        
        if (!this.username.equals(username)) {
            return false;
        }
        
        return passwordEncoder.matches(password + salt, passwordHash);
    }
    
    /**
     * Get admin username.
     */
    public String getUsername() {
        return username;
    }
    
    /**
     * Export configuration (for backup).
     */
    public String exportConfig() {
        if (!isAdminCreated()) {
            throw new IllegalStateException("No admin account to export");
        }
        
        Properties props = new Properties();
        props.setProperty("username", username);
        props.setProperty("passwordHash", passwordHash);
        props.setProperty("salt", salt);
        props.setProperty("version", "1.0");
        props.setProperty("exportDate", java.time.Instant.now().toString());
        
        StringWriter writer = new StringWriter();
        props.store(writer, "RelacIT Admin Configuration Backup");
        return writer.toString();
    }
    
    /**
     * Import configuration (for restore).
     */
    public void importConfig(String configData) throws IOException {
        Properties props = new Properties();
        props.load(new StringReader(configData));
        
        String importedUsername = props.getProperty("username");
        String importedPasswordHash = props.getProperty("passwordHash");
        String importedSalt = props.getProperty("salt");
        
        if (importedUsername == null || importedPasswordHash == null || importedSalt == null) {
            throw new IOException("Invalid configuration file - missing required fields");
        }
        
        // Overwrite existing
        this.username = importedUsername;
        this.passwordHash = importedPasswordHash;
        this.salt = importedSalt;
        this.initialized = true;
        
        saveAdminConfig();
        System.out.println("Admin configuration imported: " + username);
    }
    
    /**
     * Get config file location (for user information).
     */
    public static String getConfigLocation() {
        return ADMIN_FILE;
    }
    
    private void loadAdminConfig() {
        try {
            Path adminPath = Paths.get(ADMIN_FILE);
            Path saltPath = Paths.get(SALT_FILE);
            
            if (Files.exists(adminPath) && Files.exists(saltPath)) {
                Properties props = new Properties();
                try (InputStream is = Files.newInputStream(adminPath)) {
                    props.load(is);
                    this.username = props.getProperty("username");
                    this.passwordHash = props.getProperty("passwordHash");
                }
                
                Properties saltProps = new Properties();
                try (InputStream is = Files.newInputStream(saltPath)) {
                    saltProps.load(is);
                    this.salt = saltProps.getProperty("salt");
                }
                
                if (username != null && passwordHash != null && salt != null) {
                    this.initialized = true;
                    System.out.println("Admin account loaded: " + username);
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading admin config: " + e.getMessage());
            this.initialized = false;
        }
    }
    
    private void saveAdminConfig() {
        try {
            Files.createDirectories(Paths.get(CONFIG_DIR));
            
            Properties props = new Properties();
            props.setProperty("username", username);
            props.setProperty("passwordHash", passwordHash);
            try (OutputStream os = Files.newOutputStream(Paths.get(ADMIN_FILE))) {
                props.store(os, "RelacIT Admin Account - DO NOT SHARE");
            }
            
            Properties saltProps = new Properties();
            saltProps.setProperty("salt", salt);
            try (OutputStream os = Files.newOutputStream(Paths.get(SALT_FILE))) {
                saltProps.store(os, "RelacIT Salt - DO NOT SHARE");
            }
            
            // Set file permissions (readable only by owner)
            Paths.get(ADMIN_FILE).toFile().setReadable(false, false);
            Paths.get(ADMIN_FILE).toFile().setReadable(true, true);
            Paths.get(SALT_FILE).toFile().setReadable(false, false);
            Paths.get(SALT_FILE).toFile().setReadable(true, true);
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to save admin config: " + e.getMessage(), e);
        }
    }
}
