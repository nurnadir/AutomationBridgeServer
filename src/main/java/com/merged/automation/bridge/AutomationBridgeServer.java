package com.merged.automation.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.merged.automation.bridge.service.ClientManager;
import com.merged.automation.bridge.service.RpcProcessor;
import com.merged.automation.bridge.websocket.AutomationWebSocketHandler;
import org.apache.commons.cli.*;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Main server class for Automation Bridge Server
 * Provides WebSocket/RPC communication between AutomationService and AutomationScheduler
 */
public class AutomationBridgeServer {
    private static final Logger logger = LoggerFactory.getLogger(AutomationBridgeServer.class);
    
    private static final int DEFAULT_PORT = 9090;
    private static final String DEFAULT_HOST = "0.0.0.0";
    
    private final ObjectMapper objectMapper;
    private final ClientManager clientManager;
    private final RpcProcessor rpcProcessor;
    private Server server;
    private int port;
    private String host;
    
    public AutomationBridgeServer(String host, int port) {
        this.host = host;
        this.port = port;
        this.objectMapper = new ObjectMapper();
        this.clientManager = new ClientManager();
        this.rpcProcessor = new RpcProcessor(clientManager);
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }
    
    /**
     * Start the server
     */
    public void start() throws Exception {
        logger.info("Starting Automation Bridge Server on {}:{}", host, port);
        
        // Configure Jetty server
        server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setHost(host);
        connector.setPort(port);
        server.addConnector(connector);
        
        // Configure WebSocket context
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);
        
        // Configure WebSocket
        JettyWebSocketServletContainerInitializer.configure(context, (servletContext, wsContainer) -> {
            // Configure WebSocket parameters
            wsContainer.setMaxTextMessageSize(65536);
            wsContainer.setIdleTimeout(Duration.ofMinutes(5));
            
            // Add WebSocket endpoint
            wsContainer.addMapping("/ws", (upgradeRequest, upgradeResponse) -> {
                return new AutomationWebSocketHandler(objectMapper, clientManager, rpcProcessor);
            });
        });
        
        // Add client manager listener for logging
        clientManager.addListener(new ClientManager.ClientManagerListener() {
            @Override
            public void onClientConnected(String clientId, com.merged.automation.bridge.model.ClientInfo clientInfo) {
                logger.info("Client connected: {} - {} ({})", clientId, clientInfo.getName(), clientInfo.getType());
            }
            
            @Override
            public void onClientDisconnected(String clientId, com.merged.automation.bridge.model.ClientInfo clientInfo) {
                logger.info("Client disconnected: {} - {} ({})", clientId, clientInfo.getName(), clientInfo.getType());
            }
        });
        
        // Start server
        server.start();
        logger.info("Automation Bridge Server started successfully on {}:{}", host, port);
        logger.info("WebSocket endpoint: ws://{}:{}/ws", host, port);
        
        // Print client connection info
        logger.info("Waiting for client connections...");
        logger.info("AutomationService should connect as: AUTOMATION_SERVICE");
        logger.info("AutomationScheduler should connect as: AUTOMATION_SCHEDULER");
    }
    
    /**
     * Stop the server
     */
    public void stop() {
        if (server != null) {
            logger.info("Stopping Automation Bridge Server...");
            try {
                server.stop();
                logger.info("Server stopped successfully");
            } catch (Exception e) {
                logger.error("Error stopping server", e);
            }
        }
    }
    
    /**
     * Wait for server to finish
     */
    public void join() throws InterruptedException {
        if (server != null) {
            server.join();
        }
    }
    
    /**
     * Get client manager
     */
    public ClientManager getClientManager() {
        return clientManager;
    }
    
    /**
     * Get RPC processor
     */
    public RpcProcessor getRpcProcessor() {
        return rpcProcessor;
    }
    
    /**
     * Main method
     */
    public static void main(String[] args) {
        try {
            // Parse command line arguments
            Options options = createOptions();
            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args);
            
            if (cmd.hasOption("help")) {
                printHelp(options);
                return;
            }
            
            String host = cmd.getOptionValue("host", DEFAULT_HOST);
            int port = Integer.parseInt(cmd.getOptionValue("port", String.valueOf(DEFAULT_PORT)));
            
            // Create and start server
            AutomationBridgeServer server = new AutomationBridgeServer(host, port);
            server.start();
            
            // Wait for server to finish
            server.join();
            
        } catch (ParseException e) {
            System.err.println("Error parsing command line arguments: " + e.getMessage());
            printHelp(createOptions());
            System.exit(1);
        } catch (Exception e) {
            logger.error("Failed to start server", e);
            System.exit(1);
        }
    }
    
    /**
     * Create command line options
     */
    private static Options createOptions() {
        Options options = new Options();
        
        options.addOption(Option.builder("h")
                .longOpt("host")
                .hasArg()
                .argName("HOST")
                .desc("Server host address (default: " + DEFAULT_HOST + ")")
                .build());
        
        options.addOption(Option.builder("p")
                .longOpt("port")
                .hasArg()
                .argName("PORT")
                .desc("Server port (default: " + DEFAULT_PORT + ")")
                .build());
        
        options.addOption(Option.builder()
                .longOpt("help")
                .desc("Show this help message")
                .build());
        
        return options;
    }
    
    /**
     * Print help message
     */
    private static void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("automation-bridge-server", 
                "WebSocket/RPC bridge server for Android automation system", 
                options, 
                "\nExamples:\n" +
                "  java -jar bridge-server.jar\n" +
                "  java -jar bridge-server.jar --host 127.0.0.1 --port 9090\n" +
                "  java -jar bridge-server.jar --help\n", 
                true);
    }
}