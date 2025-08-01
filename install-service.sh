#!/bin/bash

# Install Automation Bridge Server as systemd service

SERVICE_NAME="automation-bridge-server"
SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"
INSTALL_DIR="/opt/automation-bridge-server"
CURRENT_DIR=$(pwd)

# Check if running as root
if [ "$EUID" -ne 0 ]; then
    echo "Please run as root (use sudo)"
    exit 1
fi

echo "Installing Automation Bridge Server as systemd service..."

# Create installation directory
mkdir -p "$INSTALL_DIR"
mkdir -p "$INSTALL_DIR/logs"

# Copy files
cp target/bridge-server-1.0.0-shaded.jar "$INSTALL_DIR/"
cp start-server.sh "$INSTALL_DIR/"
cp stop-server.sh "$INSTALL_DIR/"
chmod +x "$INSTALL_DIR/start-server.sh"
chmod +x "$INSTALL_DIR/stop-server.sh"

# Create service user
if ! id "automation" &>/dev/null; then
    useradd -r -s /bin/false automation
    echo "Created service user: automation"
fi

# Set ownership
chown -R automation:automation "$INSTALL_DIR"

# Create systemd service file
cat > "$SERVICE_FILE" << EOF
[Unit]
Description=Automation Bridge Server
After=network.target

[Service]
Type=simple
User=automation
Group=automation
WorkingDirectory=$INSTALL_DIR
ExecStart=/usr/bin/java -Xms256m -Xmx512m -jar bridge-server-1.0.0-shaded.jar --host 0.0.0.0 --port 9090
ExecStop=/bin/kill -TERM \$MAINPID
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal
SyslogIdentifier=$SERVICE_NAME

# Security settings
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=$INSTALL_DIR

[Install]
WantedBy=multi-user.target
EOF

# Reload systemd and enable service
systemctl daemon-reload
systemctl enable "$SERVICE_NAME"

echo "Service installed successfully!"
echo ""
echo "Commands:"
echo "  Start:   sudo systemctl start $SERVICE_NAME"
echo "  Stop:    sudo systemctl stop $SERVICE_NAME"
echo "  Status:  sudo systemctl status $SERVICE_NAME"
echo "  Logs:    sudo journalctl -u $SERVICE_NAME -f"
echo ""
echo "The service will start automatically on boot."
echo "To start now: sudo systemctl start $SERVICE_NAME"