package com.merged.automation.bridge.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class IpWhitelistService {
    
    private static final Logger logger = LoggerFactory.getLogger(IpWhitelistService.class);
    
    @Autowired
    private SecurityConfig securityConfig;
    
    private final Pattern IP_PATTERN = Pattern.compile(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );
    
    private final Pattern CIDR_PATTERN = Pattern.compile(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)/([0-9]|[1-2][0-9]|3[0-2])$"
    );
    
    public boolean isIpAllowed(String remoteAddress) {
        Set<String> allowedIps = securityConfig.getAllowedIps();
        
        // If no IP restrictions configured, allow all (not recommended for production)
        if (allowedIps.isEmpty()) {
            logger.warn("No IP whitelist configured. All IPs are allowed!");
            return true;
        }
        
        // Always allow localhost
        if (isLocalhost(remoteAddress)) {
            return true;
        }
        
        try {
            InetAddress clientAddress = InetAddress.getByName(remoteAddress);
            
            for (String allowedIp : allowedIps) {
                if (matchesIpRule(clientAddress, allowedIp.trim())) {
                    logger.debug("IP {} allowed by rule: {}", remoteAddress, allowedIp);
                    return true;
                }
            }
            
            logger.warn("IP {} not in whitelist", remoteAddress);
            return false;
            
        } catch (UnknownHostException e) {
            logger.error("Invalid IP address: {}", remoteAddress);
            return false;
        }
    }
    
    public boolean isIpAllowed(InetSocketAddress remoteAddress) {
        return isIpAllowed(remoteAddress.getAddress().getHostAddress());
    }
    
    private boolean isLocalhost(String ip) {
        return "127.0.0.1".equals(ip) || 
               "::1".equals(ip) || 
               "localhost".equals(ip) ||
               "0:0:0:0:0:0:0:1".equals(ip);
    }
    
    private boolean matchesIpRule(InetAddress clientAddress, String rule) throws UnknownHostException {
        if (rule.equals("*")) {
            return true;
        }
        
        // Direct IP match
        if (IP_PATTERN.matcher(rule).matches()) {
            return clientAddress.equals(InetAddress.getByName(rule));
        }
        
        // CIDR notation
        if (CIDR_PATTERN.matcher(rule).matches()) {
            return matchesCidr(clientAddress, rule);
        }
        
        // Hostname match
        try {
            InetAddress ruleAddress = InetAddress.getByName(rule);
            return clientAddress.equals(ruleAddress);
        } catch (UnknownHostException e) {
            logger.warn("Invalid IP rule: {}", rule);
            return false;
        }
    }
    
    private boolean matchesCidr(InetAddress clientAddress, String cidr) throws UnknownHostException {
        String[] parts = cidr.split("/");
        InetAddress networkAddress = InetAddress.getByName(parts[0]);
        int prefixLength = Integer.parseInt(parts[1]);
        
        byte[] clientBytes = clientAddress.getAddress();
        byte[] networkBytes = networkAddress.getAddress();
        
        if (clientBytes.length != networkBytes.length) {
            return false;
        }
        
        int bytesToCheck = prefixLength / 8;
        int bitsToCheck = prefixLength % 8;
        
        // Check full bytes
        for (int i = 0; i < bytesToCheck; i++) {
            if (clientBytes[i] != networkBytes[i]) {
                return false;
            }
        }
        
        // Check remaining bits
        if (bitsToCheck > 0) {
            int mask = 0xFF << (8 - bitsToCheck);
            return (clientBytes[bytesToCheck] & mask) == (networkBytes[bytesToCheck] & mask);
        }
        
        return true;
    }
    
    public void logConnectionAttempt(String remoteAddress, boolean allowed) {
        if (allowed) {
            logger.info("Connection allowed from IP: {}", remoteAddress);
        } else {
            logger.warn("Connection blocked from IP: {} (not in whitelist)", remoteAddress);
        }
    }
}