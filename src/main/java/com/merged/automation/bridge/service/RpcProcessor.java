package com.merged.automation.bridge.service;

import com.merged.automation.bridge.model.ClientInfo;
import com.merged.automation.bridge.model.RpcMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Processes RPC messages and routes them between clients
 */
public class RpcProcessor {
    private static final Logger logger = LoggerFactory.getLogger(RpcProcessor.class);
    
    private final ClientManager clientManager;
    private final Map<String, RpcMethod> methods = new HashMap<>();
    
    public RpcProcessor(ClientManager clientManager) {
        this.clientManager = clientManager;
        registerBuiltinMethods();
    }
    
    /**
     * Process incoming RPC message
     */
    public RpcMessage processMessage(String fromClientId, RpcMessage message) {
        logger.debug("Processing message from {}: {}", fromClientId, message.getMethod());
        
        try {
            switch (message.getType()) {
                case REQUEST:
                    return handleRequest(fromClientId, message);
                case RESPONSE:
                    return handleResponse(fromClientId, message);
                case NOTIFICATION:
                    return handleNotification(fromClientId, message);
                default:
                    return createErrorResponse(message.getId(), 
                        RpcMessage.ErrorCodes.INVALID_REQUEST, 
                        "Unknown message type: " + message.getType());
            }
        } catch (Exception e) {
            logger.error("Error processing message: {}", e.getMessage(), e);
            return createErrorResponse(message.getId(), 
                RpcMessage.ErrorCodes.INTERNAL_ERROR, 
                "Internal server error: " + e.getMessage());
        }
    }
    
    /**
     * Handle RPC request
     */
    private RpcMessage handleRequest(String fromClientId, RpcMessage request) {
        String method = request.getMethod();
        
        if (method == null) {
            return createErrorResponse(request.getId(), 
                RpcMessage.ErrorCodes.INVALID_REQUEST, 
                "Missing method name");
        }
        
        // Check for built-in methods
        RpcMethod rpcMethod = methods.get(method);
        if (rpcMethod != null) {
            try {
                Object result = rpcMethod.invoke(fromClientId, request.getParams());
                return createSuccessResponse(request.getId(), result);
            } catch (Exception e) {
                logger.error("Error executing method {}: {}", method, e.getMessage(), e);
                return createErrorResponse(request.getId(), 
                    RpcMessage.ErrorCodes.INTERNAL_ERROR, 
                    "Method execution failed: " + e.getMessage());
            }
        }
        
        // Route to appropriate service
        return routeToService(fromClientId, request);
    }
    
    /**
     * Handle RPC response
     */
    private RpcMessage handleResponse(String fromClientId, RpcMessage response) {
        // For now, just log responses
        logger.debug("Received response from {}: {}", fromClientId, response.getId());
        return null; // No response needed for responses
    }
    
    /**
     * Handle notifications
     */
    private RpcMessage handleNotification(String fromClientId, RpcMessage notification) {
        String method = notification.getMethod();
        
        // Handle specific notifications
        switch (method) {
            case "client.authenticate":
                return handleClientAuthentication(fromClientId, notification);
            case "automation.status_update":
                return handleAutomationStatusUpdate(fromClientId, notification);
            default:
                // Broadcast notification to relevant clients
                broadcastNotification(fromClientId, notification);
                return null;
        }
    }
    
    /**
     * Route request to appropriate service
     */
    private RpcMessage routeToService(String fromClientId, RpcMessage request) {
        String method = request.getMethod();
        
        // Determine target service based on method
        if (method.startsWith("automation.")) {
            // Route to AutomationService
            String serviceClientId = clientManager.getAutomationServiceClient();
            if (serviceClientId != null) {
                clientManager.sendMessageToClient(serviceClientId, request);
                // Response will come back asynchronously
                return null;
            } else {
                return createErrorResponse(request.getId(), 
                    RpcMessage.ErrorCodes.CLIENT_NOT_FOUND, 
                    "AutomationService not connected");
            }
        } else if (method.startsWith("scheduler.")) {
            // Route to AutomationScheduler
            List<String> schedulerClients = clientManager.getAutomationSchedulerClients();
            if (!schedulerClients.isEmpty()) {
                // Send to first available scheduler
                clientManager.sendMessageToClient(schedulerClients.get(0), request);
                return null;
            } else {
                return createErrorResponse(request.getId(), 
                    RpcMessage.ErrorCodes.CLIENT_NOT_FOUND, 
                    "AutomationScheduler not connected");
            }
        }
        
        return createErrorResponse(request.getId(), 
            RpcMessage.ErrorCodes.METHOD_NOT_FOUND, 
            "Unknown method: " + method);
    }
    
    /**
     * Handle client authentication
     */
    private RpcMessage handleClientAuthentication(String clientId, RpcMessage notification) {
        Map<String, Object> params = notification.getParams();
        if (params == null) {
            return null;
        }
        
        String clientType = (String) params.get("type");
        String clientName = (String) params.get("name");
        String version = (String) params.get("version");
        
        // Update client info
        ClientInfo clientInfo = clientManager.getClientInfo(clientId);
        if (clientInfo != null) {
            clientInfo.setType(ClientInfo.ClientType.valueOf(clientType.toUpperCase()));
            clientInfo.setName(clientName);
            clientInfo.setVersion(version);
            clientInfo.setStatus(ClientInfo.ClientStatus.CONNECTED);
            
            clientManager.updateClientInfo(clientId, clientInfo);
            
            logger.info("Client authenticated: {} - {} v{}", clientId, clientName, version);
        }
        
        return null;
    }
    
    /**
     * Handle automation status updates
     */
    private RpcMessage handleAutomationStatusUpdate(String fromClientId, RpcMessage notification) {
        // Broadcast status update to all scheduler clients
        clientManager.broadcastToType(ClientInfo.ClientType.AUTOMATION_SCHEDULER, notification);
        return null;
    }
    
    /**
     * Broadcast notification to relevant clients
     */
    private void broadcastNotification(String fromClientId, RpcMessage notification) {
        ClientInfo fromClient = clientManager.getClientInfo(fromClientId);
        if (fromClient == null) return;
        
        // Determine broadcast targets based on source client type
        switch (fromClient.getType()) {
            case AUTOMATION_SERVICE:
                // Broadcast to all schedulers
                clientManager.broadcastToType(ClientInfo.ClientType.AUTOMATION_SCHEDULER, notification);
                break;
            case AUTOMATION_SCHEDULER:
                // Broadcast to service and other schedulers
                clientManager.broadcastToType(ClientInfo.ClientType.AUTOMATION_SERVICE, notification);
                clientManager.broadcastToType(ClientInfo.ClientType.AUTOMATION_SCHEDULER, notification);
                break;
            default:
                // Broadcast to all
                clientManager.broadcastToAll(notification);
                break;
        }
    }
    
    /**
     * Register built-in RPC methods
     */
    private void registerBuiltinMethods() {
        // Server status
        methods.put("server.status", (clientId, params) -> {
            Map<String, Object> status = new HashMap<>();
            status.put("uptime", System.currentTimeMillis());
            status.put("clients", clientManager.getAllClients().size());
            status.put("version", "1.0.0");
            return status;
        });
        
        // List clients
        methods.put("server.list_clients", (clientId, params) -> {
            return clientManager.getAllClients();
        });
        
        // Ping
        methods.put("server.ping", (clientId, params) -> {
            return Map.of("pong", System.currentTimeMillis());
        });
    }
    
    /**
     * Create success response
     */
    private RpcMessage createSuccessResponse(String requestId, Object result) {
        RpcMessage response = new RpcMessage(requestId, RpcMessage.MessageType.RESPONSE);
        response.setResult(result);
        return response;
    }
    
    /**
     * Create error response
     */
    private RpcMessage createErrorResponse(String requestId, int errorCode, String errorMessage) {
        RpcMessage response = new RpcMessage(requestId, RpcMessage.MessageType.ERROR);
        response.setError(new RpcMessage.RpcError(errorCode, errorMessage));
        return response;
    }
    
    /**
     * RPC method interface
     */
    @FunctionalInterface
    private interface RpcMethod {
        Object invoke(String clientId, Map<String, Object> params) throws Exception;
    }
}