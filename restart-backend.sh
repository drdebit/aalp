#!/bin/bash
# AALP backend: stop whatever listens on 3000, start fresh.
#
# The database password is NOT in this file. It is read from
# ~/.config/aalp/env (a line like: export DATOMIC_DB_PASSWORD='...'),
# so this script can live in git. With no password at all the backend
# runs on an in-memory Datomic, which is what you want on a machine
# that cannot reach the shared transactor.
#
# -sTCP:LISTEN matters: without it lsof also matches processes holding
# CLIENT connections to port 3000, and shadow-cljs proxies /api there.
# Restarting the backend was therefore killing the frontend dev server
# every time, which looked like the whole app going down.
set -u
cd "$(dirname "$0")"

[ -f "$HOME/.config/aalp/env" ] && . "$HOME/.config/aalp/env"

echo "Stopping existing AALP backend..."
PORT_PID=$(lsof -t -i:3000 -sTCP:LISTEN 2>/dev/null)
if [ -n "$PORT_PID" ]; then
    echo "Killing process $PORT_PID on port 3000"
    kill $PORT_PID 2>/dev/null
    sleep 2
fi
pkill -f "assertive-app.server" 2>/dev/null
sleep 2
if lsof -i:3000 -sTCP:LISTEN >/dev/null 2>&1; then
    echo "ERROR: Port 3000 still in use. Try: sudo kill \$(lsof -t -i:3000)"
    exit 1
fi

if [ -z "${DATOMIC_DB_PASSWORD:-}" ]; then
    echo "No DATOMIC_DB_PASSWORD (no ~/.config/aalp/env): starting on an in-memory database."
fi
echo "Starting AALP backend on port 3000..."
exec clojure -M -m assertive-app.server
