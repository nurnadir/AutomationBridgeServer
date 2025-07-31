package com.merged.automation.bridge.security;

import com.merged.automation.bridge.model.RpcMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ValidationService {
    
    private static final Logger logger = LoggerFactory.getLogger(ValidationService.class);
    
    private static final int MAX_MESSAGE_LENGTH = 64 * 1024; // 64KB
    private static final int MAX_METHOD_NAME_LENGTH = 100;
    private static final int MAX_CLIENT_ID_LENGTH = 100;
    private static final int MAX_PARAM_VALUE_LENGTH = 10 * 1024; // 10KB per parameter
    
    private static final Pattern VALID_METHOD_NAME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9._-]{0,99}$");
    private static final Pattern VALID_CLIENT_ID = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,99}$");
    
    private static final Set<String> ALLOWED_RPC_METHODS = Set.of(
        "client.authenticate",
        "client.heartbeat",
        "automation.get_status",
        "automation.list",
        "automation.get",
        "automation.execute",
        "vnc.get_status",
        "vnc.start",
        "vnc.stop",
        "server.status",
        "server.list_clients",
        "server.ping"
    );
    
    public ValidationResult validateRpcMessage(RpcMessage message, String rawMessage) {
        if (rawMessage != null && rawMessage.length() > MAX_MESSAGE_LENGTH) {
            return ValidationResult.error("Message too large: " + rawMessage.length() + " bytes");
        }
        
        if (message == null) {
            return ValidationResult.error("Message is null");
        }
        
        if (message.getId() == null || message.getId().trim().isEmpty()) {
            return ValidationResult.error("Message ID is required");
        }
        
        if (message.getId().length() > MAX_CLIENT_ID_LENGTH) {
            return ValidationResult.error("Message ID too long");
        }
        
        if (message.getMethod() == null || message.getMethod().trim().isEmpty()) {
            return ValidationResult.error("Method is required");
        }
        
        if (message.getMethod().length() > MAX_METHOD_NAME_LENGTH) {
            return ValidationResult.error("Method name too long");
        }
        
        if (!VALID_METHOD_NAME.matcher(message.getMethod()).matches()) {
            return ValidationResult.error("Invalid method name format");
        }
        
        if (!ALLOWED_RPC_METHODS.contains(message.getMethod())) {
            return ValidationResult.error("Method not allowed: " + message.getMethod());
        }
        
        ValidationResult paramResult = validateParameters(message);
        if (!paramResult.isValid()) {
            return paramResult;
        }
        
        return ValidationResult.success();
    }
    
    public ValidationResult validateClientId(String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            return ValidationResult.error("Client ID is required");
        }
        
        if (clientId.length() > MAX_CLIENT_ID_LENGTH) {
            return ValidationResult.error("Client ID too long");
        }
        
        if (!VALID_CLIENT_ID.matcher(clientId).matches()) {
            return ValidationResult.error("Invalid client ID format");
        }
        
        return ValidationResult.success();
    }
    
    public ValidationResult validateClientType(String clientType) {
        if (clientType == null || clientType.trim().isEmpty()) {
            return ValidationResult.error("Client type is required");
        }
        
        Set<String> validTypes = Set.of("automation_service", "automation_scheduler");
        if (!validTypes.contains(clientType.toLowerCase())) {
            return ValidationResult.error("Invalid client type: " + clientType);
        }
        
        return ValidationResult.success();
    }
    
    private ValidationResult validateParameters(RpcMessage message) {
        Object params = message.getParams();
        if (params == null) {
            return ValidationResult.success();
        }
        
        String paramStr = params.toString();
        if (paramStr.length() > MAX_PARAM_VALUE_LENGTH) {
            return ValidationResult.error("Parameters too large");
        }
        
        // Additional parameter validation based on method
        return validateMethodSpecificParams(message.getMethod(), params);
    }
    
    private ValidationResult validateMethodSpecificParams(String method, Object params) {
        switch (method) {
            case "client.authenticate":
                return validateAuthParams(params);
            case "automation.execute":
                return validateExecuteParams(params);
            case "automation.get":
                return validateGetParams(params);
            default:
                return ValidationResult.success();
        }
    }
    
    private ValidationResult validateAuthParams(Object params) {
        // Additional validation for authentication parameters
        return ValidationResult.success();
    }
    
    private ValidationResult validateExecuteParams(Object params) {
        // Additional validation for execution parameters
        return ValidationResult.success();
    }
    
    private ValidationResult validateGetParams(Object params) {
        // Additional validation for get parameters
        return ValidationResult.success();
    }
    
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        
        private ValidationResult(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }
        
        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult error(String message) {
            return new ValidationResult(false, message);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
    }
}