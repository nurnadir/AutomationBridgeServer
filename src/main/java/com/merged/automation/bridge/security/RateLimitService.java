package com.merged.automation.bridge.security;

import com.google.common.util.concurrent.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class RateLimitService {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimitService.class);
    
    @Autowired
    private SecurityConfig securityConfig;
    
    private final ConcurrentMap<String, RateLimiter> clientLimiters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, RateLimiter> ipLimiters = new ConcurrentHashMap<>();
    
    public boolean isAllowed(String clientId, String remoteIp) {
        return isClientAllowed(clientId) && isIpAllowed(remoteIp);
    }
    
    public boolean isClientAllowed(String clientId) {
        RateLimiter limiter = clientLimiters.computeIfAbsent(clientId, this::createRateLimiter);
        boolean allowed = limiter.tryAcquire();
        
        if (!allowed) {
            logger.warn("Rate limit exceeded for client: {}", clientId);
        }
        
        return allowed;
    }
    
    public boolean isIpAllowed(String remoteIp) {
        RateLimiter limiter = ipLimiters.computeIfAbsent(remoteIp, this::createRateLimiter);
        boolean allowed = limiter.tryAcquire();
        
        if (!allowed) {
            logger.warn("Rate limit exceeded for IP: {}", remoteIp);
        }
        
        return allowed;
    }
    
    private RateLimiter createRateLimiter(String key) {
        double permitsPerSecond = (double) securityConfig.getRateLimitRequests() / securityConfig.getRateLimitWindow();
        return RateLimiter.create(permitsPerSecond);
    }
    
    public void resetClientLimits(String clientId) {
        clientLimiters.remove(clientId);
        logger.debug("Reset rate limits for client: {}", clientId);
    }
    
    public void resetIpLimits(String remoteIp) {
        ipLimiters.remove(remoteIp);
        logger.debug("Reset rate limits for IP: {}", remoteIp);
    }
    
    public double getAvailablePermits(String clientId) {
        RateLimiter limiter = clientLimiters.get(clientId);
        // Guava RateLimiter doesn't expose available permits, so we estimate
        return limiter != null ? 1.0 : 0.0;
    }
    
    public void cleanupOldLimiters() {
        // Simple cleanup strategy - remove some limiters periodically
        // In a production system, you might want a more sophisticated approach
        int initialClientSize = clientLimiters.size();
        int initialIpSize = ipLimiters.size();
        
        // Remove every 10th limiter to prevent unbounded growth
        if (initialClientSize > 1000) {
            clientLimiters.entrySet().removeIf(entry -> 
                entry.getKey().hashCode() % 10 == 0
            );
        }
        
        if (initialIpSize > 1000) {
            ipLimiters.entrySet().removeIf(entry -> 
                entry.getKey().hashCode() % 10 == 0
            );
        }
        
        int removedClients = initialClientSize - clientLimiters.size();
        int removedIps = initialIpSize - ipLimiters.size();
        
        if (removedClients > 0 || removedIps > 0) {
            logger.debug("Cleaned up {} client limiters and {} IP limiters", removedClients, removedIps);
        }
    }
}