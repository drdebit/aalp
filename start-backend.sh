#!/bin/bash
# AALP Backend startup script
# Requires DATOMIC_DB_PASSWORD to be set

cd /home/mdeangelis/clojure/aalp

# Use the same password as accrue-backend
export DATOMIC_DB_PASSWORD='PnHJGWm4FlaajEa'

echo "Starting AALP backend on port 3000..."
exec clojure -M -m assertive-app.server
