#!/bin/bash

# Automation Bridge Server startup script for Linux

SERVER_JAR="target/bridge-server-1.0.0-shaded.jar"
PID_FILE="server.pid"
LOG_DIR="logs"

# Default configuration
DEFAULT_HOST="0.0.0.0"
DEFAULT_PORT="9090"
DEFAULT_JAVA_OPTS="-Xms256m -Xmx512m"

# Parse command line arguments
HOST=${1:-$DEFAULT_HOST}
PORT=${2:-$DEFAULT_PORT}
JAVA_OPTS=${3:-$DEFAULT_JAVA_OPTS}

# Create logs directory
mkdir -p "$LOG_DIR"

# Check if server is already running
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
        echo "Server is already running with PID $PID"
        exit 1
    else
        echo "Removing stale PID file"
        rm -f "$PID_FILE"
    fi
fi

# Check if JAR file exists
if [ ! -f "$SERVER_JAR" ]; then
    echo "Server JAR file not found: $SERVER_JAR"
    echo "Please run 'mvn clean package' to build the server"
    exit 1
fi

echo "Starting Automation Bridge Server..."
echo "Host: $HOST"
echo "Port: $PORT"
echo "Java Options: $JAVA_OPTS"

# Start server in background
nohup java $JAVA_OPTS -jar "$SERVER_JAR" --host "$HOST" --port "$PORT" > "$LOG_DIR/server.out" 2>&1 &
SERVER_PID=$!

# Save PID
echo $SERVER_PID > "$PID_FILE"

echo "Server started with PID $SERVER_PID"
echo "Logs: tail -f $LOG_DIR/server.out"
echo "Stop: ./stop-server.sh"

# Wait a moment and check if server started successfully
sleep 2
if kill -0 "$SERVER_PID" 2>/dev/null; then
    echo "Server started successfully"
else
    echo "Server failed to start, check logs"
    rm -f "$PID_FILE"
    exit 1
fi