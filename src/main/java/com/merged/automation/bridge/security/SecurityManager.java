package com.merged.automation.bridge.security;

import com.merged.automation.bridge.model.RpcMessage;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SecurityManager {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityManager.class);
    
    @Autowired
    private SecurityConfig securityConfig;
    
    @Autowired
    private JwtService jwtService;
    
    @Autowired
    private IpWhitelistService ipWhitelistService;
    
    @Autowired
    private RateLimitService rateLimitService;
    
    @Autowired
    private ValidationService validationService;
    
    private final Map<String, AuthenticatedClient> authenticatedClients = new ConcurrentHashMap<>();
    
    public SecurityCheckResult checkConnectionSecurity(Session session) {
        InetSocketAddress remoteAddress = (InetSocketAddress) session.getRemoteAddress();
        String remoteIp = remoteAddress.getAddress().getHostAddress();
        
        // Check IP whitelist
        if (!ipWhitelistService.isIpAllowed(remoteIp)) {
            ipWhitelistService.logConnectionAttempt(remoteIp, false);
            return SecurityCheckResult.blocked("IP not in whitelist");
        }
        
        // Check rate limiting for IP
        if (!rateLimitService.isIpAllowed(remoteIp)) {
            return SecurityCheckResult.blocked("Rate limit exceeded for IP");
        }
        
        ipWhitelistService.logConnectionAttempt(remoteIp, true);
        return SecurityCheckResult.allowed();
    }
    
    public SecurityCheckResult checkMessageSecurity(String clientId, RpcMessage message, String rawMessage, Session session) {
        InetSocketAddress remoteAddress = (InetSocketAddress) session.getRemoteAddress();
        String remoteIp = remoteAddress.getAddress().getHostAddress();
        
        // Rate limiting check
        if (!rateLimitService.isAllowed(clientId, remoteIp)) {
            logSecurityEvent("RATE_LIMIT_EXCEEDED", clientId, remoteIp, message.getMethod());
            return SecurityCheckResult.blocked("Rate limit exceeded");
        }
        
        // Message validation
        ValidationService.ValidationResult validation = validationService.validateRpcMessage(message, rawMessage);
        if (!validation.isValid()) {
            logSecurityEvent("INVALID_MESSAGE", clientId, remoteIp, message.getMethod(), validation.getErrorMessage());
            return SecurityCheckResult.blocked("Invalid message: " + validation.getErrorMessage());
        }
        
        // Authentication check (except for authentication requests)
        if (securityConfig.isRequireAuth() && !"client.authenticate".equals(message.getMethod())) {
            AuthenticatedClient client = authenticatedClients.get(clientId);
            if (client == null || !client.isValid()) {
                logSecurityEvent("UNAUTHENTICATED_REQUEST", clientId, remoteIp, message.getMethod());
                return SecurityCheckResult.blocked("Authentication required");
            }
            
            // Update last activity
            client.updateLastActivity();
        }
        
        logSecurityEvent("MESSAGE_ALLOWED", clientId, remoteIp, message.getMethod());
        return SecurityCheckResult.allowed();
    }
    
    public AuthenticationResult authenticateClient(String clientId, String clientType, String token, Session session) {
        InetSocketAddress remoteAddress = (InetSocketAddress) session.getRemoteAddress();
        String remoteIp = remoteAddress.getAddress().getHostAddress();
        
        // Validate client ID and type
        ValidationService.ValidationResult clientIdValidation = validationService.validateClientId(clientId);
        if (!clientIdValidation.isValid()) {
            logSecurityEvent("INVALID_CLIENT_ID", clientId, remoteIp, "client.authenticate", clientIdValidation.getErrorMessage());
            return AuthenticationResult.failed("Invalid client ID");
        }
        
        ValidationService.ValidationResult clientTypeValidation = validationService.validateClientType(clientType);
        if (!clientTypeValidation.isValid()) {
            logSecurityEvent("INVALID_CLIENT_TYPE", clientId, remoteIp, "client.authenticate", clientTypeValidation.getErrorMessage());
            return AuthenticationResult.failed("Invalid client type");
        }
        
        // If authentication is not required, create a simple token
        if (!securityConfig.isRequireAuth()) {
            String newToken = jwtService.generateToken(clientId, clientType);
            AuthenticatedClient client = new AuthenticatedClient(clientId, clientType, newToken, remoteIp);
            authenticatedClients.put(clientId, client);
            
            logSecurityEvent("CLIENT_AUTHENTICATED_NO_AUTH", clientId, remoteIp, "client.authenticate");
            return AuthenticationResult.success(newToken);
        }
        
        // Validate provided token
        if (token != null && !token.trim().isEmpty()) {
            if (jwtService.validateTokenForClient(token, clientId)) {
                AuthenticatedClient client = new AuthenticatedClient(clientId, clientType, token, remoteIp);
                authenticatedClients.put(clientId, client);
                
                logSecurityEvent("CLIENT_AUTHENTICATED", clientId, remoteIp, "client.authenticate");
                return AuthenticationResult.success(token);
            } else {
                logSecurityEvent("INVALID_TOKEN", clientId, remoteIp, "client.authenticate");
                return AuthenticationResult.failed("Invalid token");
            }
        }
        
        // Generate new token
        String newToken = jwtService.generateToken(clientId, clientType);
        AuthenticatedClient client = new AuthenticatedClient(clientId, clientType, newToken, remoteIp);
        authenticatedClients.put(clientId, client);
        
        logSecurityEvent("NEW_TOKEN_GENERATED", clientId, remoteIp, "client.authenticate");
        return AuthenticationResult.success(newToken);
    }
    
    public void disconnectClient(String clientId) {
        AuthenticatedClient client = authenticatedClients.remove(clientId);
        if (client != null) {
            logSecurityEvent("CLIENT_DISCONNECTED", clientId, client.getRemoteIp(), "disconnect");
        }
    }
    
    public boolean isClientAuthenticated(String clientId) {
        AuthenticatedClient client = authenticatedClients.get(clientId);
        return client != null && client.isValid();
    }
    
    private void logSecurityEvent(String event, String clientId, String remoteIp, String method) {
        logSecurityEvent(event, clientId, remoteIp, method, null);
    }
    
    private void logSecurityEvent(String event, String clientId, String remoteIp, String method, String details) {
        String message = String.format("SECURITY [%s] Client: %s, IP: %s, Method: %s", 
                event, clientId, remoteIp, method);
        if (details != null) {
            message += ", Details: " + details;
        }
        
        if (event.contains("BLOCKED") || event.contains("INVALID") || event.contains("EXCEEDED")) {
            logger.warn(message);
        } else {
            logger.info(message);
        }
    }
    
    public void cleanupExpiredSessions() {
        authenticatedClients.entrySet().removeIf(entry -> {
            AuthenticatedClient client = entry.getValue();
            if (!client.isValid()) {
                logSecurityEvent("SESSION_EXPIRED", entry.getKey(), client.getRemoteIp(), "cleanup");
                return true;
            }
            return false;
        });
        
        rateLimitService.cleanupOldLimiters();
    }
    
    private static class AuthenticatedClient {
        private final String clientId;
        private final String clientType;
        private final String token;
        private final String remoteIp;
        private final LocalDateTime authenticatedAt;
        private LocalDateTime lastActivity;
        
        public AuthenticatedClient(String clientId, String clientType, String token, String remoteIp) {
            this.clientId = clientId;
            this.clientType = clientType;
            this.token = token;
            this.remoteIp = remoteIp;
            this.authenticatedAt = LocalDateTime.now();
            this.lastActivity = LocalDateTime.now();
        }
        
        public void updateLastActivity() {
            this.lastActivity = LocalDateTime.now();
        }
        
        public boolean isValid() {
            // Session expires after 1 hour of inactivity
            return lastActivity.isAfter(LocalDateTime.now().minusHours(1));
        }
        
        public String getRemoteIp() {
            return remoteIp;
        }
    }
    
    public static class SecurityCheckResult {
        private final boolean allowed;
        private final String reason;
        
        private SecurityCheckResult(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }
        
        public static SecurityCheckResult allowed() {
            return new SecurityCheckResult(true, null);
        }
        
        public static SecurityCheckResult blocked(String reason) {
            return new SecurityCheckResult(false, reason);
        }
        
        public boolean isAllowed() {
            return allowed;
        }
        
        public String getReason() {
            return reason;
        }
    }
    
    public static class AuthenticationResult {
        private final boolean success;
        private final String token;
        private final String errorMessage;
        
        private AuthenticationResult(boolean success, String token, String errorMessage) {
            this.success = success;
            this.token = token;
            this.errorMessage = errorMessage;
        }
        
        public static AuthenticationResult success(String token) {
            return new AuthenticationResult(true, token, null);
        }
        
        public static AuthenticationResult failed(String errorMessage) {
            return new AuthenticationResult(false, null, errorMessage);
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getToken() {
            return token;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
    }
}