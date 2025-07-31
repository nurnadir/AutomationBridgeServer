package com.merged.automation.bridge.service;

import com.merged.automation.bridge.model.ClientInfo;
import com.merged.automation.bridge.model.RpcMessage;
import com.merged.automation.bridge.websocket.AutomationWebSocketHandler;
import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages WebSocket client connections and routing
 */
public class ClientManager {
    private static final Logger logger = LoggerFactory.getLogger(ClientManager.class);
    
    private final Map<String, ClientSession> clients = new ConcurrentHashMap<>();
    private final List<ClientManagerListener> listeners = new CopyOnWriteArrayList<>();
    
    /**
     * Register a new client
     */
    public void registerClient(String clientId, Session session, ClientInfo clientInfo) {
        ClientSession clientSession = new ClientSession(session, clientInfo);
        clients.put(clientId, clientSession);
        
        logger.info("Client registered: {} ({})", clientId, clientInfo.getType());
        notifyClientConnected(clientId, clientInfo);
    }
    
    /**
     * Unregister a client
     */
    public void unregisterClient(String clientId) {
        ClientSession clientSession = clients.remove(clientId);
        if (clientSession != null) {
            clientSession.getClientInfo().setStatus(ClientInfo.ClientStatus.DISCONNECTED);
            logger.info("Client unregistered: {}", clientId);
            notifyClientDisconnected(clientId, clientSession.getClientInfo());
        }
    }
    
    /**
     * Update client information
     */
    public void updateClientInfo(String clientId, ClientInfo clientInfo) {
        ClientSession clientSession = clients.get(clientId);
        if (clientSession != null) {
            clientSession.setClientInfo(clientInfo);
            logger.debug("Client info updated for: {}", clientId);
        }
    }
    
    /**
     * Update client activity timestamp
     */
    public void updateClientActivity(String clientId) {
        ClientSession clientSession = clients.get(clientId);
        if (clientSession != null) {
            clientSession.getClientInfo().updateLastActivity();
        }
    }
    
    /**
     * Update client status
     */
    public void updateClientStatus(String clientId, ClientInfo.ClientStatus status) {
        ClientSession clientSession = clients.get(clientId);
        if (clientSession != null) {
            clientSession.getClientInfo().setStatus(status);
            logger.debug("Client status updated for {}: {}", clientId, status);
        }
    }
    
    /**
     * Get client information
     */
    public ClientInfo getClientInfo(String clientId) {
        ClientSession clientSession = clients.get(clientId);
        return clientSession != null ? clientSession.getClientInfo() : null;
    }
    
    /**
     * Get client session
     */
    public Session getClientSession(String clientId) {
        ClientSession clientSession = clients.get(clientId);
        return clientSession != null ? clientSession.getSession() : null;
    }
    
    /**
     * Get all connected clients
     */
    public Map<String, ClientInfo> getAllClients() {
        Map<String, ClientInfo> result = new HashMap<>();
        clients.forEach((id, session) -> result.put(id, session.getClientInfo()));
        return result;
    }
    
    /**
     * Get clients by type
     */
    public List<String> getClientsByType(ClientInfo.ClientType type) {
        List<String> result = new ArrayList<>();
        clients.forEach((id, session) -> {
            if (session.getClientInfo().getType() == type) {
                result.add(id);
            }
        });
        return result;
    }
    
    /**
     * Send message to specific client
     */
    public boolean sendMessageToClient(String clientId, RpcMessage message) {
        ClientSession clientSession = clients.get(clientId);
        if (clientSession != null && clientSession.getSession().isOpen()) {
            try {
                // This would need to be implemented with proper WebSocket handler reference
                logger.debug("Sending message to client {}: {}", clientId, message.getMethod());
                return true;
            } catch (Exception e) {
                logger.error("Failed to send message to client {}: {}", clientId, e.getMessage(), e);
                return false;
            }
        }
        return false;
    }
    
    /**
     * Broadcast message to all clients of specific type
     */
    public void broadcastToType(ClientInfo.ClientType type, RpcMessage message) {
        getClientsByType(type).forEach(clientId -> sendMessageToClient(clientId, message));
    }
    
    /**
     * Broadcast message to all clients
     */
    public void broadcastToAll(RpcMessage message) {
        clients.keySet().forEach(clientId -> sendMessageToClient(clientId, message));
    }
    
    /**
     * Check if client is connected
     */
    public boolean isClientConnected(String clientId) {
        ClientSession clientSession = clients.get(clientId);
        return clientSession != null && 
               clientSession.getSession().isOpen() && 
               clientSession.getClientInfo().getStatus() == ClientInfo.ClientStatus.CONNECTED;
    }
    
    /**
     * Get automation service client ID
     */
    public String getAutomationServiceClient() {
        return getClientsByType(ClientInfo.ClientType.AUTOMATION_SERVICE)
                .stream()
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Get automation scheduler clients
     */
    public List<String> getAutomationSchedulerClients() {
        return getClientsByType(ClientInfo.ClientType.AUTOMATION_SCHEDULER);
    }
    
    /**
     * Add listener for client events
     */
    public void addListener(ClientManagerListener listener) {
        listeners.add(listener);
    }
    
    /**
     * Remove listener
     */
    public void removeListener(ClientManagerListener listener) {
        listeners.remove(listener);
    }
    
    // Notification methods
    private void notifyClientConnected(String clientId, ClientInfo clientInfo) {
        listeners.forEach(listener -> {
            try {
                listener.onClientConnected(clientId, clientInfo);
            } catch (Exception e) {
                logger.error("Error notifying listener of client connection", e);
            }
        });
    }
    
    private void notifyClientDisconnected(String clientId, ClientInfo clientInfo) {
        listeners.forEach(listener -> {
            try {
                listener.onClientDisconnected(clientId, clientInfo);
            } catch (Exception e) {
                logger.error("Error notifying listener of client disconnection", e);
            }
        });
    }
    
    /**
     * Client session wrapper
     */
    private static class ClientSession {
        private final Session session;
        private ClientInfo clientInfo;
        
        public ClientSession(Session session, ClientInfo clientInfo) {
            this.session = session;
            this.clientInfo = clientInfo;
        }
        
        public Session getSession() { return session; }
        public ClientInfo getClientInfo() { return clientInfo; }
        public void setClientInfo(ClientInfo clientInfo) { this.clientInfo = clientInfo; }
    }
    
    /**
     * Listener interface for client events
     */
    public interface ClientManagerListener {
        void onClientConnected(String clientId, ClientInfo clientInfo);
        void onClientDisconnected(String clientId, ClientInfo clientInfo);
    }
}