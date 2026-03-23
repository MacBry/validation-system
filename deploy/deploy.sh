#!/bin/bash
# ============================================================
# Validation System v2.12.0-ENTERPRISE - VPS Deployment Script
# Target: ${TSA_IP} (Debian 13)
# ============================================================
set -e

APP_DIR="/opt/app"
BACKUP_DIR="/opt/app/backup_$(date +%Y%m%d_%H%M%S)"

echo "============================================"
echo " Validation System - Production Deployment"
echo " $(date)"
echo "============================================"

# Step 1: Backup current deployment
echo ""
echo "[1/8] Backing up current deployment..."
mkdir -p "$BACKUP_DIR"
cp -f "$APP_DIR/docker-compose.yml" "$BACKUP_DIR/" 2>/dev/null || true
cp -f "$APP_DIR/Dockerfile.prod" "$BACKUP_DIR/" 2>/dev/null || true
echo "  Backup saved to: $BACKUP_DIR"

# Step 2: Stop current containers
echo ""
echo "[2/8] Stopping current containers..."
cd "$APP_DIR"
docker compose down --timeout 30 2>/dev/null || docker-compose down --timeout 30 2>/dev/null || true
echo "  Containers stopped."

# Step 3: Verify new files are in place
echo ""
echo "[3/8] Verifying deployment files..."
for f in docker-compose.yml Dockerfile.prod validation-system.jar; do
    if [ ! -f "$APP_DIR/$f" ]; then
        echo "  ERROR: Missing $APP_DIR/$f"
        exit 1
    fi
    echo "  OK: $f ($(du -h $APP_DIR/$f | cut -f1))"
done

# Verify nginx config
for f in nginx/nginx.conf nginx/conf.d/default.conf; do
    if [ ! -f "$APP_DIR/$f" ]; then
        echo "  ERROR: Missing $APP_DIR/$f"
        exit 1
    fi
    echo "  OK: $f"
done

# Step 4: Create required directories
echo ""
echo "[4/8] Creating directories..."
mkdir -p "$APP_DIR/uploads/certificates"
mkdir -p "$APP_DIR/nginx/conf.d"
echo "  Directories ready."

# Step 5: Build Docker image
echo ""
echo "[5/8] Building Docker image..."
cd "$APP_DIR"
docker compose build --no-cache vcc-app 2>&1 || docker-compose build --no-cache vcc-app 2>&1
echo "  Image built successfully."

# Step 6: Start all services
echo ""
echo "[6/8] Starting services..."
docker compose up -d 2>&1 || docker-compose up -d 2>&1
echo "  Services started."

# Step 7: Wait for application to be ready
echo ""
echo "[7/8] Waiting for application startup (max 120s)..."
TIMEOUT=120
ELAPSED=0
while [ $ELAPSED -lt $TIMEOUT ]; do
    # Check if container is still running
    STATUS=$(docker inspect --format='{{.State.Status}}' vcc-application 2>/dev/null || echo "missing")
    if [ "$STATUS" = "exited" ]; then
        echo "  ERROR: Container exited! Checking logs..."
        docker logs vcc-application --tail 30 2>&1 | tr -cd '[:print:]\n'
        exit 1
    fi

    # Try health check
    HEALTH=$(docker exec vcc-application wget -q -O - http://localhost:8080/actuator/health 2>/dev/null || echo "")
    if echo "$HEALTH" | grep -q '"UP"' 2>/dev/null; then
        echo "  Application is UP! (${ELAPSED}s)"
        break
    fi

    sleep 5
    ELAPSED=$((ELAPSED + 5))
    echo "  Waiting... (${ELAPSED}s)"
done

if [ $ELAPSED -ge $TIMEOUT ]; then
    echo "  WARNING: Timeout waiting for health check."
    echo "  Checking container status..."
    docker ps -a --format 'table {{.Names}}\t{{.Status}}'
    echo ""
    echo "  Last 20 log lines:"
    docker logs vcc-application --tail 20 2>&1 | tr -cd '[:print:]\n'
fi

# Step 8: Verification
echo ""
echo "[8/8] Verification..."
echo ""
echo "--- Container Status ---"
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
echo ""
echo "--- Disk Usage ---"
df -h /
echo ""
echo "--- HTTP Test ---"
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost/ 2>/dev/null || echo "000")
echo "  HTTP response code: $HTTP_CODE"
if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "302" ]; then
    echo "  SUCCESS: Application is accessible!"
else
    echo "  WARNING: Unexpected HTTP code. Check logs with: docker logs vcc-application"
fi

echo ""
echo "============================================"
echo " Deployment complete!"
echo " Access: http://${TSA_IP}/"
echo " Login:  admin / Admin123!"
echo "============================================"
