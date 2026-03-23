#!/usr/bin/env bash
# =============================================================================
# VALIDATION SYSTEM v2.12.0-ENTERPRISE - Backup Status Page Generator
# =============================================================================
# Generates a simple HTML status page for backup monitoring
# =============================================================================

BACKUP_BASE="/backups"
LOG_DIR="/var/log/validation-system/backups"

# Gather stats
DAILY_COUNT=$(ls -d ${BACKUP_BASE}/daily/vcc_* 2>/dev/null | wc -l)
WEEKLY_COUNT=$(ls -d ${BACKUP_BASE}/weekly/vcc_* 2>/dev/null | wc -l)
MONTHLY_COUNT=$(ls -d ${BACKUP_BASE}/monthly/vcc_* 2>/dev/null | wc -l)

LATEST_DAILY=$(ls -td ${BACKUP_BASE}/daily/vcc_* 2>/dev/null | head -1)
LATEST_DAILY_NAME=$(basename "$LATEST_DAILY" 2>/dev/null || echo "none")
LATEST_DAILY_SIZE=$(du -sh "$LATEST_DAILY" 2>/dev/null | cut -f1 || echo "N/A")

DISK_USAGE=$(df -h "$BACKUP_BASE" 2>/dev/null | awk 'NR==2 {print $3 " / " $2 " (" $5 " used)"}')

# Check if latest backup is fresh (< 26 hours)
STATUS_COLOR="green"
STATUS_TEXT="HEALTHY"
if [ -n "$LATEST_DAILY" ]; then
    AGE_SEC=$(( $(date +%s) - $(stat -c %Y "$LATEST_DAILY" 2>/dev/null || echo 0) ))
    AGE_HOURS=$(( AGE_SEC / 3600 ))
    if [ "$AGE_HOURS" -gt 26 ]; then
        STATUS_COLOR="red"
        STATUS_TEXT="STALE (${AGE_HOURS}h old)"
    fi
else
    STATUS_COLOR="red"
    STATUS_TEXT="NO BACKUPS FOUND"
fi

# Last 10 audit entries
AUDIT_ENTRIES=$(tail -10 "${LOG_DIR}/backup_audit_trail.log" 2>/dev/null || echo "No audit trail")

cat <<HTML
<!DOCTYPE html>
<html>
<head>
    <title>VCC Backup Status</title>
    <meta http-equiv="refresh" content="60">
    <style>
        body { font-family: system-ui, sans-serif; max-width: 900px; margin: 40px auto; padding: 20px; background: #f5f5f5; }
        h1 { color: #333; border-bottom: 2px solid #0066cc; padding-bottom: 10px; }
        .status { display: inline-block; padding: 8px 16px; border-radius: 4px; color: white; font-weight: bold; }
        .green { background: #28a745; }
        .red { background: #dc3545; }
        .yellow { background: #ffc107; color: #333; }
        table { width: 100%; border-collapse: collapse; margin: 20px 0; background: white; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
        th, td { padding: 12px 16px; text-align: left; border-bottom: 1px solid #eee; }
        th { background: #f8f9fa; font-weight: 600; }
        .card { background: white; padding: 20px; margin: 16px 0; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
        pre { background: #1e1e1e; color: #d4d4d4; padding: 16px; border-radius: 4px; overflow-x: auto; font-size: 13px; }
        .grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 16px; }
        .metric { text-align: center; }
        .metric .value { font-size: 2em; font-weight: bold; color: #0066cc; }
        .metric .label { color: #666; font-size: 0.9em; }
    </style>
</head>
<body>
    <h1>Validation System - Backup Status</h1>
    <p>Generated: $(date '+%Y-%m-%d %H:%M:%S') | Host: $(hostname)</p>

    <div class="card">
        <h2>Overall Status: <span class="status ${STATUS_COLOR}">${STATUS_TEXT}</span></h2>
    </div>

    <div class="grid">
        <div class="card metric">
            <div class="value">${DAILY_COUNT}</div>
            <div class="label">Daily Backups</div>
        </div>
        <div class="card metric">
            <div class="value">${WEEKLY_COUNT}</div>
            <div class="label">Weekly Backups</div>
        </div>
        <div class="card metric">
            <div class="value">${MONTHLY_COUNT}</div>
            <div class="label">Monthly Archives</div>
        </div>
    </div>

    <div class="card">
        <h3>Latest Backup</h3>
        <table>
            <tr><th>Name</th><td>${LATEST_DAILY_NAME}</td></tr>
            <tr><th>Size</th><td>${LATEST_DAILY_SIZE}</td></tr>
            <tr><th>Age</th><td>${AGE_HOURS:-N/A} hours</td></tr>
            <tr><th>Disk Usage</th><td>${DISK_USAGE}</td></tr>
        </table>
    </div>

    <div class="card">
        <h3>Recent Audit Trail (last 10 entries)</h3>
        <pre>${AUDIT_ENTRIES}</pre>
    </div>

    <div class="card" style="font-size: 0.85em; color: #888;">
        <p>GMP Compliance: All backup operations logged to immutable audit trail.</p>
        <p>Retention: Daily 7d / Weekly 30d / Monthly 365d / Audit 7 years</p>
    </div>
</body>
</html>
HTML
