# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a WebSocket/RPC bridge server written in Java that facilitates communication between Android automation applications:
- **AutomationService** - executes automation on Android devices
- **AutomationScheduler** - manages and schedules automation tasks

The server uses Jetty WebSocket server with a custom RPC protocol for message routing between clients.

## Architecture

### Core Components
- `AutomationBridgeServer.java` - Main server class and entry point
- `ClientManager.java` - Manages WebSocket client connections and routing at `/src/main/java/com/merged/automation/bridge/service/ClientManager.java:17`
- `RpcProcessor.java` - Handles RPC message processing and routing
- `AutomationWebSocketHandler.java` - WebSocket message handler
- `RpcMessage.java` / `ClientInfo.java` - Data models for RPC communication

### Client Types
The system expects two types of clients:
- `AUTOMATION_SERVICE` - Android service that executes automation
- `AUTOMATION_SCHEDULER` - Android app that manages automation scheduling

## Common Development Commands

### Build
```bash
mvn clean package
```
Creates `target/bridge-server-1.0.0-shaded.jar`

### Compile Only
```bash
mvn compile
```

### Run Tests
```bash
mvn test
```

### Development Run
```bash
mvn exec:java -Dexec.mainClass="com.merged.automation.bridge.AutomationBridgeServer" -Dexec.args="--host 127.0.0.1 --port 9090"
```

### Production Run
```bash
java -jar target/bridge-server-1.0.0-shaded.jar --host 0.0.0.0 --port 9090
```

## Service Management

The project includes shell scripts for service management:
- `./start-server.sh [host] [port] [java_opts]` - Start server
- `./stop-server.sh` - Stop server  
- `./install-service.sh` - Install as systemd service

## WebSocket API

### Endpoint
```
ws://host:port/ws
```

### RPC Message Format
Messages use JSON-RPC-like protocol with fields:
- `id` - Request UUID
- `type` - MESSAGE_TYPE (REQUEST/RESPONSE/NOTIFICATION)
- `method` - Method name (e.g., "automation.execute")
- `params` - Method parameters object

### Client Authentication
Clients authenticate using:
```json
{
  "method": "client.authenticate",
  "params": {
    "type": "automation_service|automation_scheduler",
    "name": "ClientName",
    "version": "1.0.0"
  }
}
```

## Technology Stack

- **Java 11** - Minimum version required
- **Maven** - Build system
- **Jetty WebSocket** - WebSocket server implementation
- **Jackson** - JSON processing
- **Logback** - Logging framework
- **Apache Commons CLI** - Command line argument parsing

## Security Features

### SSL/TLS Support
- WSS (WebSocket Secure) with TLS 1.2/1.3
- Self-signed certificate generation: `./generate-ssl-cert.sh`
- Start with SSL: `java -jar target/bridge-server-1.0.0-shaded.jar --ssl --keystore server.jks --keystore-password automation2024`

### Authentication & Authorization
- **JWT-based authentication** for all RPC calls (except `client.authenticate`)
- **IP whitelisting** with CIDR block support
- **Rate limiting** per client and per IP address
- **Input validation** for all RPC messages and parameters

### Configuration
Set security options via environment variables (see `security.env.example`):
```bash
BRIDGE_SECURITY_JWT_SECRET=your-secret-key
BRIDGE_SECURITY_ALLOWED_IPS=192.168.1.0/24,10.0.0.0/8
BRIDGE_SECURITY_REQUIRE_AUTH=true
BRIDGE_SECURITY_RATE_LIMIT_REQUESTS=100
BRIDGE_SECURITY_RATE_LIMIT_WINDOW=60
```

### Security Logging
All security events are logged including:
- Connection attempts (allowed/blocked)
- Authentication failures
- Rate limit violations  
- Invalid message attempts
- Client session management

## Development Notes

- Server binds to `0.0.0.0:9090` by default (configurable)
- WebSocket idle timeout: 5 minutes
- Max text message size: 64KB
- Uses thread-safe collections (`ConcurrentHashMap`, `CopyOnWriteArrayList`)
- Logging configuration in `src/main/resources/logback.xml`
- Security cleanup runs every 5 minutes to prevent memory leaks