#!/bin/bash
# AALP Backend restart script
# Kills any existing backend processes and starts fresh

cd /home/accrue/accrue/aalp

echo "Stopping existing AALP backend..."

# Kill any existing processes on port 3000
PORT_PID=$(lsof -t -i:3000 2>/dev/null)
if [ -n "$PORT_PID" ]; then
    echo "Killing process $PORT_PID on port 3000"
    kill $PORT_PID 2>/dev/null
    sleep 2
fi

# Also kill any assertive-app processes
pkill -f "assertive-app.server" 2>/dev/null

# Wait for port to be released
sleep 2

# Verify port is free
if lsof -i:3000 >/dev/null 2>&1; then
    echo "ERROR: Port 3000 still in use. Try: sudo kill \$(lsof -t -i:3000)"
    exit 1
fi

# Use the same password as accrue-backend
export DATOMIC_DB_PASSWORD='ms&MWh@!8@70'

echo "Starting AALP backend on port 3000..."
exec clojure -M -m assertive-app.server
