#!/bin/bash

# Script to generate SSL certificate for Automation Bridge Server

KEYSTORE_FILE="server.jks"
KEYSTORE_PASS="automation2024"
VALIDITY_DAYS=365
KEY_SIZE=2048

echo "Generating SSL certificate for Automation Bridge Server..."

# Get server hostname/IP
read -p "Enter server hostname or IP (default: localhost): " SERVER_HOST
SERVER_HOST=${SERVER_HOST:-localhost}

# Generate keystore with self-signed certificate
keytool -genkeypair \
    -alias automation-bridge \
    -keyalg RSA \
    -keysize $KEY_SIZE \
    -validity $VALIDITY_DAYS \
    -keystore $KEYSTORE_FILE \
    -storepass $KEYSTORE_PASS \
    -keypass $KEYSTORE_PASS \
    -dname "CN=$SERVER_HOST, OU=Automation Bridge, O=Merged Automation, L=Unknown, ST=Unknown, C=US" \
    -ext SAN=dns:$SERVER_HOST,dns:localhost,ip:127.0.0.1

if [ $? -eq 0 ]; then
    echo ""
    echo "SSL certificate generated successfully!"
    echo "Keystore file: $KEYSTORE_FILE"
    echo "Password: $KEYSTORE_PASS"
    echo ""
    echo "To start server with SSL:"
    echo "java -jar target/bridge-server-1.0.0-shaded.jar --ssl --keystore $KEYSTORE_FILE --keystore-password $KEYSTORE_PASS"
    echo ""
    echo "WebSocket URL will be: wss://$SERVER_HOST:9090/ws"
    
    # Set appropriate permissions
    chmod 600 $KEYSTORE_FILE
    echo "Keystore permissions set to 600 for security"
else
    echo "Error generating SSL certificate"
    exit 1
fi