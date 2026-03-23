#!/usr/bin/env bash
# =============================================================================
# VALIDATION SYSTEM v2.12.0-ENTERPRISE - Combined Backup Orchestrator
# =============================================================================
# PURPOSE:  Run MySQL dump + filesystem backup as a single atomic operation
# SCHEDULE: This is the main entry point called by cron
# =============================================================================
#
# USAGE:
#   ./combined-backup.sh [daily|weekly|monthly]
#
# CRON EXAMPLES:
#   0 2 * * 1-6  /opt/app/backup-infrastructure/scripts/combined-backup.sh daily
#   0 3 * * 0    /opt/app/backup-infrastructure/scripts/combined-backup.sh weekly
#   0 4 1 * *    /opt/app/backup-infrastructure/scripts/combined-backup.sh monthly
#
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/backup-env.sh"
source "${SCRIPT_DIR}/backup-functions.sh"

BACKUP_TYPE="${1:-daily}"
OVERALL_START=$(date +%s)
EXIT_CODE=0

log_info "============================================================"
log_info "COMBINED BACKUP ORCHESTRATOR - ${BACKUP_TYPE} - $(date)"
log_info "============================================================"
log_audit "ORCHESTRATOR_START" "type=${BACKUP_TYPE}"

# ---- Step 1: MySQL Full Backup ----
log_info "[1/4] Running MySQL full backup..."
if "${SCRIPT_DIR}/mysql-full-backup.sh" "$BACKUP_TYPE"; then
    log_info "[1/4] MySQL backup: SUCCESS"
else
    log_error "[1/4] MySQL backup: FAILED"
    EXIT_CODE=1
fi

# ---- Step 2: Filesystem Backup ----
log_info "[2/4] Running filesystem backup..."
if "${SCRIPT_DIR}/filesystem-backup.sh" "$BACKUP_TYPE"; then
    log_info "[2/4] Filesystem backup: SUCCESS"
else
    log_error "[2/4] Filesystem backup: FAILED"
    EXIT_CODE=1
fi

# ---- Step 3: Binary Log Backup ----
log_info "[3/4] Running binary log backup..."
if "${SCRIPT_DIR}/mysql-incremental-backup.sh" binlog; then
    log_info "[3/4] Binary log backup: SUCCESS"
else
    log_warn "[3/4] Binary log backup: SKIPPED (binary logging may not be enabled)"
fi

# ---- Step 4: Healthcheck ----
log_info "[4/4] Running backup healthcheck..."
if "${SCRIPT_DIR}/backup-healthcheck.sh"; then
    log_info "[4/4] Healthcheck: PASSED"
else
    log_warn "[4/4] Healthcheck: WARNINGS detected"
fi

# ---- Summary ----
OVERALL_END=$(date +%s)
OVERALL_DURATION=$(( OVERALL_END - OVERALL_START ))

log_info "============================================================"
log_info "COMBINED BACKUP COMPLETE"
log_info "Type:     ${BACKUP_TYPE}"
log_info "Duration: ${OVERALL_DURATION}s ($(( OVERALL_DURATION / 60 ))m $(( OVERALL_DURATION % 60 ))s)"
log_info "Status:   $([ $EXIT_CODE -eq 0 ] && echo 'ALL OK' || echo 'PARTIAL FAILURE')"
log_info "============================================================"

log_audit "ORCHESTRATOR_COMPLETE" "type=${BACKUP_TYPE} duration=${OVERALL_DURATION}s exit=${EXIT_CODE}"

if [ $EXIT_CODE -ne 0 ]; then
    send_alert "BACKUP PARTIAL FAILURE" \
        "Combined ${BACKUP_TYPE} backup completed with errors on $(hostname). Check logs: ${BACKUP_LOG_FILE}" \
        "ERROR"
else
    send_alert "Backup Complete" \
        "Combined ${BACKUP_TYPE} backup completed successfully in ${OVERALL_DURATION}s on $(hostname)" \
        "INFO"
fi

exit $EXIT_CODE
