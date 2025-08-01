#!/bin/bash

# Automation Bridge Server stop script

PID_FILE="server.pid"

if [ ! -f "$PID_FILE" ]; then
    echo "Server PID file not found. Server may not be running."
    exit 1
fi

PID=$(cat "$PID_FILE")

if kill -0 "$PID" 2>/dev/null; then
    echo "Stopping server with PID $PID..."
    kill -TERM "$PID"
    
    # Wait for graceful shutdown
    for i in {1..10}; do
        if ! kill -0 "$PID" 2>/dev/null; then
            echo "Server stopped successfully"
            rm -f "$PID_FILE"
            exit 0
        fi
        sleep 1
    done
    
    # Force kill if graceful shutdown failed
    echo "Server did not stop gracefully, forcing shutdown..."
    kill -KILL "$PID"
    rm -f "$PID_FILE"
    echo "Server force stopped"
else
    echo "Server with PID $PID is not running"
    rm -f "$PID_FILE"
fi