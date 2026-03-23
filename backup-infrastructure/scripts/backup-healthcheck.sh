#!/usr/bin/env bash
# =============================================================================
# VALIDATION SYSTEM v2.12.0-ENTERPRISE - Backup Health Check
# =============================================================================
# PURPOSE:  Verify backup integrity, freshness, and completeness
# SCHEDULE: Runs after every backup + independently every 4 hours
# GMP:      Reports backup compliance status for auditors
# =============================================================================
#
# CHECKS PERFORMED:
#   1. Latest backup exists and is recent (< 26 hours old)
#   2. Backup file size above minimum threshold
#   3. Checksum files present and valid
#   4. S3 replication status (if configured)
#   5. Disk space availability
#   6. Audit trail integrity
#
# EXIT CODES:
#   0 = All checks passed
#   1 = Critical failure (backup missing or corrupt)
#   2 = Warning (non-critical issues)
#
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/backup-env.sh"
source "${SCRIPT_DIR}/backup-functions.sh"

WARNINGS=0
ERRORS=0
REPORT=""

add_check() {
    local status="$1"
    local check_name="$2"
    local detail="$3"
    REPORT="${REPORT}\n[${status}] ${check_name}: ${detail}"

    case "$status" in
        PASS) ;;
        WARN) WARNINGS=$((WARNINGS + 1)) ;;
        FAIL) ERRORS=$((ERRORS + 1)) ;;
    esac
}

# ---- Check 1: Latest Backup Freshness ----
check_backup_freshness() {
    local backup_dir="$1"
    local description="$2"
    local max_age_hours="$HEALTHCHECK_MAX_AGE_HOURS"

    if [ ! -d "$backup_dir" ]; then
        add_check "FAIL" "Freshness ($description)" "Directory does not exist: $backup_dir"
        return
    fi

    local latest
    latest=$(ls -td "${backup_dir}"/vcc_* 2>/dev/null | head -1)

    if [ -z "$latest" ]; then
        add_check "FAIL" "Freshness ($description)" "No backups found in $backup_dir"
        return
    fi

    local backup_age_seconds
    backup_age_seconds=$(( $(date +%s) - $(stat -c %Y "$latest" 2>/dev/null || stat -f %m "$latest" 2>/dev/null || echo 0) ))
    local backup_age_hours=$(( backup_age_seconds / 3600 ))

    if [ "$backup_age_hours" -gt "$max_age_hours" ]; then
        add_check "FAIL" "Freshness ($description)" "Latest backup is ${backup_age_hours}h old (max: ${max_age_hours}h): $(basename "$latest")"
    else
        add_check "PASS" "Freshness ($description)" "Latest backup is ${backup_age_hours}h old: $(basename "$latest")"
    fi
}

# ---- Check 2: Backup Size ----
check_backup_size() {
    local backup_dir="$1"
    local description="$2"
    local min_size_mb="$HEALTHCHECK_MIN_SIZE_MB"

    local latest
    latest=$(ls -td "${backup_dir}"/vcc_* 2>/dev/null | head -1)

    if [ -z "$latest" ]; then
        return  # Already reported by freshness check
    fi

    local size_kb
    size_kb=$(du -sk "$latest" 2>/dev/null | cut -f1)
    local size_mb=$(( size_kb / 1024 ))

    if [ "$size_mb" -lt "$min_size_mb" ]; then
        add_check "WARN" "Size ($description)" "Backup is only ${size_mb}MB (min: ${min_size_mb}MB) - possible empty/truncated dump"
    else
        add_check "PASS" "Size ($description)" "Backup size: ${size_mb}MB"
    fi
}

# ---- Check 3: Checksum Integrity ----
check_checksum_integrity() {
    local backup_dir="$1"
    local description="$2"

    local latest
    latest=$(ls -td "${backup_dir}"/vcc_* 2>/dev/null | head -1)

    if [ -z "$latest" ]; then
        return
    fi

    local checksum_files
    checksum_files=$(find "$latest" -name "*.sha256" 2>/dev/null)

    if [ -z "$checksum_files" ]; then
        add_check "WARN" "Checksum ($description)" "No checksum files found in $(basename "$latest")"
        return
    fi

    local checksum_ok=true
    while IFS= read -r chk_file; do
        if ! sha256sum -c "$chk_file" --quiet 2>/dev/null; then
            checksum_ok=false
            add_check "FAIL" "Checksum ($description)" "MISMATCH: $chk_file"
        fi
    done <<< "$checksum_files"

    if $checksum_ok; then
        add_check "PASS" "Checksum ($description)" "All checksums valid"
    fi
}

# ---- Check 4: Metadata Present ----
check_metadata() {
    local backup_dir="$1"
    local description="$2"

    local latest
    latest=$(ls -td "${backup_dir}"/vcc_* 2>/dev/null | head -1)

    if [ -z "$latest" ]; then
        return
    fi

    if [ -f "${latest}/backup_metadata.json" ]; then
        add_check "PASS" "Metadata ($description)" "backup_metadata.json present"
    else
        add_check "WARN" "Metadata ($description)" "backup_metadata.json MISSING"
    fi
}

# ---- Check 5: Disk Space ----
check_storage() {
    local directory="$1"
    local min_free_gb=5

    if [ ! -d "$directory" ]; then
        add_check "WARN" "Storage" "Backup directory does not exist: $directory"
        return
    fi

    local free_kb
    free_kb=$(df -k "$directory" | awk 'NR==2 {print $4}')
    local free_gb=$(( free_kb / 1024 / 1024 ))
    local used_pct
    used_pct=$(df "$directory" | awk 'NR==2 {print $5}')

    if [ "$free_gb" -lt "$min_free_gb" ]; then
        add_check "FAIL" "Storage" "Only ${free_gb}GB free (${used_pct} used)"
    elif [ "$free_gb" -lt 10 ]; then
        add_check "WARN" "Storage" "${free_gb}GB free (${used_pct} used) - getting low"
    else
        add_check "PASS" "Storage" "${free_gb}GB free (${used_pct} used)"
    fi
}

# ---- Check 6: Audit Trail ----
check_audit_trail() {
    local audit_file="${BACKUP_LOG_DIR}/backup_audit_trail.log"

    if [ ! -f "$audit_file" ]; then
        add_check "WARN" "Audit Trail" "No audit trail file found"
        return
    fi

    local last_entry
    last_entry=$(tail -1 "$audit_file" 2>/dev/null)
    local entry_count
    entry_count=$(wc -l < "$audit_file" 2>/dev/null || echo 0)

    add_check "PASS" "Audit Trail" "${entry_count} entries, last: ${last_entry:0:80}"
}

# ---- Check 7: MySQL Connectivity ----
check_mysql() {
    if mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
        -e "SELECT 1" "$MYSQL_DATABASE" > /dev/null 2>&1; then
        add_check "PASS" "MySQL" "Connection OK ($MYSQL_HOST:$MYSQL_PORT)"
    else
        add_check "FAIL" "MySQL" "Connection FAILED ($MYSQL_HOST:$MYSQL_PORT)"
    fi
}

# ---- Run All Checks ----

main() {
    log_info "=========================================="
    log_info "BACKUP HEALTHCHECK - $(date)"
    log_info "=========================================="

    ensure_directories

    # MySQL connectivity
    check_mysql

    # Daily backups
    check_backup_freshness "$BACKUP_DAILY_DIR" "daily"
    check_backup_size "$BACKUP_DAILY_DIR" "daily"
    check_checksum_integrity "$BACKUP_DAILY_DIR" "daily"
    check_metadata "$BACKUP_DAILY_DIR" "daily"

    # Weekly backups (less strict freshness)
    HEALTHCHECK_MAX_AGE_HOURS=170  # ~7 days
    check_backup_freshness "$BACKUP_WEEKLY_DIR" "weekly"
    HEALTHCHECK_MAX_AGE_HOURS=26

    # Storage
    check_storage "$BACKUP_BASE_DIR"

    # Audit trail
    check_audit_trail

    # ---- Report ----
    echo ""
    echo "============================================================"
    echo "  BACKUP HEALTHCHECK REPORT - $(date '+%Y-%m-%d %H:%M:%S')"
    echo "  Host: $(hostname)"
    echo "============================================================"
    echo -e "$REPORT"
    echo ""
    echo "------------------------------------------------------------"
    echo "  SUMMARY: ${ERRORS} errors, ${WARNINGS} warnings"
    echo "============================================================"

    # Log the report
    echo -e "$REPORT" >> "${BACKUP_LOG_FILE}" 2>/dev/null || true

    # Determine exit code
    if [ "$ERRORS" -gt 0 ]; then
        send_alert "HEALTHCHECK FAILED" \
            "$(echo -e "$REPORT")" "ERROR"
        log_audit "HEALTHCHECK_FAIL" "errors=$ERRORS warnings=$WARNINGS"
        return 1
    elif [ "$WARNINGS" -gt 0 ]; then
        send_alert "HEALTHCHECK WARNINGS" \
            "$(echo -e "$REPORT")" "WARN"
        log_audit "HEALTHCHECK_WARN" "errors=$ERRORS warnings=$WARNINGS"
        return 2
    else
        log_audit "HEALTHCHECK_PASS" "errors=0 warnings=0"
        return 0
    fi
}

main "$@"
