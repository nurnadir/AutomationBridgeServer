package com.merged.automation.bridge.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.merged.automation.bridge.model.ClientInfo;
import com.merged.automation.bridge.model.RpcMessage;
import com.merged.automation.bridge.service.ClientManager;
import com.merged.automation.bridge.service.RpcProcessor;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.UUID;

/**
 * WebSocket handler for automation clients
 */
@WebSocket
public class AutomationWebSocketHandler extends WebSocketAdapter {
    private static final Logger logger = LoggerFactory.getLogger(AutomationWebSocketHandler.class);
    
    private final ObjectMapper objectMapper;
    private final ClientManager clientManager;
    private final RpcProcessor rpcProcessor;
    private String clientId;
    
    public AutomationWebSocketHandler(ObjectMapper objectMapper, ClientManager clientManager, RpcProcessor rpcProcessor) {
        this.objectMapper = objectMapper;
        this.clientManager = clientManager;
        this.rpcProcessor = rpcProcessor;
    }
    
    @Override
    public void onWebSocketConnect(Session session) {
        super.onWebSocketConnect(session);
        this.clientId = UUID.randomUUID().toString();
        
        logger.info("WebSocket connection established: {}", clientId);
        
        // Register client with temporary info - will be updated on authentication
        ClientInfo clientInfo = new ClientInfo(clientId, ClientInfo.ClientType.AUTOMATION_SERVICE, "Unknown");
        clientManager.registerClient(clientId, session, clientInfo);
    }
    
    @Override
    public void onWebSocketText(String message) {
        try {
            logger.debug("Received message from {}: {}", clientId, message);
            
            // Parse RPC message
            RpcMessage rpcMessage = objectMapper.readValue(message, RpcMessage.class);
            
            // Update client activity
            clientManager.updateClientActivity(clientId);
            
            // Process RPC message
            RpcMessage response = rpcProcessor.processMessage(clientId, rpcMessage);
            
            // Send response if needed
            if (response != null) {
                sendMessage(response);
            }
            
        } catch (Exception e) {
            logger.error("Error processing message from {}: {}", clientId, e.getMessage(), e);
            
            // Send error response
            RpcMessage errorResponse = new RpcMessage(
                UUID.randomUUID().toString(), 
                RpcMessage.MessageType.ERROR
            );
            errorResponse.setError(new RpcMessage.RpcError(
                RpcMessage.ErrorCodes.PARSE_ERROR, 
                "Failed to parse message: " + e.getMessage()
            ));
            
            sendMessage(errorResponse);
        }
    }
    
    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        super.onWebSocketClose(statusCode, reason);
        logger.info("WebSocket connection closed for {}: {} - {}", clientId, statusCode, reason);
        
        if (clientId != null) {
            clientManager.unregisterClient(clientId);
        }
    }
    
    @Override
    public void onWebSocketError(Throwable cause) {
        super.onWebSocketError(cause);
        logger.error("WebSocket error for {}: {}", clientId, cause.getMessage(), cause);
        
        if (clientId != null) {
            clientManager.updateClientStatus(clientId, ClientInfo.ClientStatus.DISCONNECTED);
        }
    }
    
    /**
     * Send RPC message to client
     */
    public void sendMessage(RpcMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            
            if (getSession() != null && getSession().isOpen()) {
                getSession().getRemote().sendString(json);
                logger.debug("Sent message to {}: {}", clientId, json);
            } else {
                logger.warn("Cannot send message to {}: session is not open", clientId);
            }
            
        } catch (IOException e) {
            logger.error("Failed to send message to {}: {}", clientId, e.getMessage(), e);
        }
    }
    
    /**
     * Get client ID
     */
    public String getClientId() {
        return clientId;
    }
}