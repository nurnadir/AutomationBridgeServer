package com.merged.automation.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.merged.automation.bridge.security.SecurityFactory;
import com.merged.automation.bridge.service.ClientManager;
import com.merged.automation.bridge.service.RpcProcessor;
import com.merged.automation.bridge.websocket.AutomationWebSocketHandler;
import org.apache.commons.cli.*;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    private final SecurityFactory.SecurityComponents securityComponents;
    private Server server;
    private int port;
    private String host;
    private boolean sslEnabled;
    private String keystorePath;
    private String keystorePassword;
    private ScheduledExecutorService securityCleanupExecutor;
    
    public AutomationBridgeServer(String host, int port, boolean sslEnabled, String keystorePath, String keystorePassword) {
        this.host = host;
        this.port = port;
        this.sslEnabled = sslEnabled;
        this.keystorePath = keystorePath;
        this.keystorePassword = keystorePassword;
        
        this.objectMapper = new ObjectMapper();
        this.clientManager = new ClientManager();
        this.rpcProcessor = new RpcProcessor(clientManager);
        this.securityComponents = SecurityFactory.createSecurityComponents();
        
        // Security cleanup scheduler
        this.securityCleanupExecutor = Executors.newScheduledThreadPool(1);
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }
    
    public AutomationBridgeServer(String host, int port) {
        this(host, port, false, null, null);
    }
    
    /**
     * Start the server
     */
    public void start() throws Exception {
        logger.info("Starting Automation Bridge Server on {}:{}", host, port);
        
        // Configure Jetty server
        server = new Server();
        
        ServerConnector connector;
        if (sslEnabled && keystorePath != null) {
            // SSL/TLS Configuration
            SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
            sslContextFactory.setKeyStorePath(keystorePath);
            sslContextFactory.setKeyStorePassword(keystorePassword);
            sslContextFactory.setKeyManagerPassword(keystorePassword);
            
            // Security protocols
            sslContextFactory.setIncludeProtocols("TLSv1.2", "TLSv1.3");
            sslContextFactory.setExcludeProtocols("SSLv2", "SSLv3", "TLSv1", "TLSv1.1");
            
            // Security ciphers
            sslContextFactory.setIncludeCipherSuites(
                "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
                "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
                "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256"
            );
            
            HttpConfiguration httpConfig = new HttpConfiguration();
            httpConfig.addCustomizer(new SecureRequestCustomizer());
            
            connector = new ServerConnector(server,
                new SslConnectionFactory(sslContextFactory, "http/1.1"),
                new HttpConnectionFactory(httpConfig));
                
            logger.info("SSL/TLS enabled with keystore: {}", keystorePath);
        } else {
            connector = new ServerConnector(server);
            logger.warn("SSL/TLS not enabled - using unsecured connection");
        }
        
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
                return new AutomationWebSocketHandler(objectMapper, clientManager, rpcProcessor, securityComponents.securityManager);
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
        
        // Start security cleanup scheduler
        securityCleanupExecutor.scheduleAtFixedRate(
            securityComponents.securityManager::cleanupExpiredSessions, 
            5, 5, TimeUnit.MINUTES
        );
        
        // Start server
        server.start();
        logger.info("Automation Bridge Server started successfully on {}:{}", host, port);
        
        String protocol = sslEnabled ? "wss" : "ws";
        logger.info("WebSocket endpoint: {}://{}:{}/ws", protocol, host, port);
        
        // Print security info
        logger.info("Security features enabled:");
        logger.info("- JWT Authentication: {}", securityComponents.securityConfig.isRequireAuth());
        logger.info("- IP Whitelisting: {}", !securityComponents.securityConfig.getAllowedIps().isEmpty());
        logger.info("- Rate Limiting: {} req/{} sec", securityComponents.securityConfig.getRateLimitRequests(), securityComponents.securityConfig.getRateLimitWindow());
        logger.info("- TLS/SSL: {}", sslEnabled);
        
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
                // Stop security cleanup scheduler
                if (securityCleanupExecutor != null) {
                    securityCleanupExecutor.shutdown();
                }
                
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
            boolean sslEnabled = cmd.hasOption("ssl");
            String keystorePath = cmd.getOptionValue("keystore");
            String keystorePassword = cmd.getOptionValue("keystore-password");
            
            // Create and start server
            AutomationBridgeServer server = new AutomationBridgeServer(host, port, sslEnabled, keystorePath, keystorePassword);
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
                .longOpt("ssl")
                .desc("Enable SSL/TLS (requires keystore)")
                .build());
        
        options.addOption(Option.builder()
                .longOpt("keystore")
                .hasArg()
                .argName("PATH")
                .desc("Path to SSL keystore file")
                .build());
        
        options.addOption(Option.builder()
                .longOpt("keystore-password")
                .hasArg()
                .argName("PASSWORD")
                .desc("Password for SSL keystore")
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
                "  java -jar bridge-server.jar --ssl --keystore server.jks --keystore-password mypass\n" +
                "  java -jar bridge-server.jar --help\n" +
                "\nSecurity Configuration (Environment Variables):\n" +
                "  BRIDGE_SECURITY_JWT_SECRET=aquickbrownfoxjumpsoveralazydog1234567891337\n" +
                "  BRIDGE_SECURITY_ALLOWED_IPS=0.0.0.0/0\n" +
                "  BRIDGE_SECURITY_REQUIRE_AUTH=true\n" +
                "  BRIDGE_SECURITY_RATE_LIMIT_REQUESTS=100\n", 
                true);
    }
}