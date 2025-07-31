package com.merged.automation.bridge.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class SecurityConfig {
    
    @Value("${bridge.security.jwt.secret:}")
    private String jwtSecret;
    
    @Value("${bridge.security.jwt.expiration:3600000}")
    private long jwtExpiration;
    
    @Value("${bridge.security.allowed-ips:}")
    private List<String> allowedIps;
    
    @Value("${bridge.security.require-auth:true}")
    private boolean requireAuth;
    
    @Value("${bridge.security.rate-limit.requests:100}")
    private int rateLimitRequests;
    
    @Value("${bridge.security.rate-limit.window:60}")
    private int rateLimitWindow;
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
    
    @Bean
    public SecureRandom secureRandom() {
        return new SecureRandom();
    }
    
    public String getJwtSecret() {
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            try {
                KeyGenerator keyGen = KeyGenerator.getInstance("HmacSHA256");
                keyGen.init(256);
                SecretKey secretKey = keyGen.generateKey();
                jwtSecret = Base64.getEncoder().encodeToString(secretKey.getEncoded());
                System.out.println("Generated JWT secret. Please add to your configuration:");
                System.out.println("bridge.security.jwt.secret=" + jwtSecret);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("Failed to generate JWT secret", e);
            }
        }
        return jwtSecret;
    }
    
    public long getJwtExpiration() {
        return jwtExpiration;
    }
    
    public Set<String> getAllowedIps() {
        return allowedIps != null ? new HashSet<>(allowedIps) : new HashSet<>();
    }
    
    public boolean isRequireAuth() {
        return requireAuth;
    }
    
    public int getRateLimitRequests() {
        return rateLimitRequests;
    }
    
    public int getRateLimitWindow() {
        return rateLimitWindow;
    }
}