#!/usr/bin/env bash
# =============================================================================
# VALIDATION SYSTEM v2.12.0-ENTERPRISE - MySQL Full Database Backup
# =============================================================================
# PURPOSE:  Full mysqldump of validation_system database
# SCHEDULE: Daily at 02:00 (cron), Weekly full at Sunday 03:00
# GMP:      Includes audit tables (_AUD), routines, triggers, events
# =============================================================================
#
# USAGE:
#   ./mysql-full-backup.sh [daily|weekly|monthly]
#
# PREREQUISITES:
#   - MySQL credentials in ~/.my.cnf:
#       [mysqldump]
#       user=validation_user
#       password=<secure_password>
#   - Or set DB_PASSWORD environment variable
#
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/backup-env.sh"
source "${SCRIPT_DIR}/backup-functions.sh"

# ---- Configuration ----
BACKUP_TYPE="${1:-daily}"
SNAPSHOT_NAME="vcc_full_${BACKUP_TIMESTAMP}"

case "$BACKUP_TYPE" in
    daily)   TARGET_DIR="${BACKUP_DAILY_DIR}/${SNAPSHOT_NAME}" ;;
    weekly)  TARGET_DIR="${BACKUP_WEEKLY_DIR}/${SNAPSHOT_NAME}" ;;
    monthly) TARGET_DIR="${BACKUP_MONTHLY_DIR}/${SNAPSHOT_NAME}" ;;
    *)
        echo "Usage: $0 [daily|weekly|monthly]"
        exit 1
        ;;
esac

# ---- Main ----

main() {
    log_info "=========================================="
    log_info "STARTING MySQL Full Backup (${BACKUP_TYPE})"
    log_info "=========================================="
    log_audit "BACKUP_START" "type=${BACKUP_TYPE} database=${MYSQL_DATABASE}"

    # Pre-flight checks
    ensure_directories
    acquire_lock
    trap 'release_lock; on_error' ERR
    trap 'release_lock' EXIT

    check_mysql_connection
    check_disk_space "$BACKUP_BASE_DIR" 5

    # Create snapshot directory
    mkdir -p "$TARGET_DIR"
    chmod 700 "$TARGET_DIR"

    local dump_file="${TARGET_DIR}/validation_system_full.sql"
    local start_time=$(date +%s)

    # ---- Phase 1: Full Schema + Data Dump ----
    log_info "Phase 1: mysqldump full database..."

    mysqldump \
        --host="$MYSQL_HOST" \
        --port="$MYSQL_PORT" \
        --user="$MYSQL_USER" \
        --single-transaction \
        --routines \
        --triggers \
        --events \
        --quick \
        --set-gtid-purged=OFF \
        --max_allowed_packet="${MYSQLDUMP_MAX_ALLOWED_PACKET}" \
        --net_buffer_length="${MYSQLDUMP_NET_BUFFER_LENGTH}" \
        --hex-blob \
        --complete-insert \
        --add-drop-table \
        --add-drop-database \
        --databases "$MYSQL_DATABASE" \
        --result-file="$dump_file" \
        2>> "${BACKUP_LOG_FILE}"

    if [ $? -ne 0 ] || [ ! -s "$dump_file" ]; then
        log_error "mysqldump FAILED or produced empty file"
        send_alert "BACKUP FAILED" "mysqldump failed for ${MYSQL_DATABASE}" "ERROR"
        return 1
    fi

    local dump_size
    dump_size=$(du -sh "$dump_file" | cut -f1)
    log_info "Phase 1 complete: ${dump_file} (${dump_size})"

    # ---- Phase 2: Separate Audit Tables Dump (GMP Critical) ----
    log_info "Phase 2: Backup audit tables separately (GMP requirement)..."

    local audit_dump="${TARGET_DIR}/validation_system_audit_tables.sql"

    # Get list of all _AUD tables + revinfo
    local audit_tables
    audit_tables=$(mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
        -N -e "SELECT GROUP_CONCAT(table_name SEPARATOR ' ')
               FROM information_schema.tables
               WHERE table_schema='${MYSQL_DATABASE}'
               AND (table_name LIKE '%_AUD' OR table_name = 'revinfo' OR table_name = 'revchanges' OR table_name = 'audit_logs')" \
        2>/dev/null || echo "")

    if [ -n "$audit_tables" ]; then
        mysqldump \
            --host="$MYSQL_HOST" \
            --port="$MYSQL_PORT" \
            --user="$MYSQL_USER" \
            --single-transaction \
            --quick \
            --hex-blob \
            --complete-insert \
            --max_allowed_packet="${MYSQLDUMP_MAX_ALLOWED_PACKET}" \
            "$MYSQL_DATABASE" $audit_tables \
            > "$audit_dump" 2>> "${BACKUP_LOG_FILE}"

        log_info "Audit tables backup: $(du -sh "$audit_dump" | cut -f1)"
    else
        log_warn "No audit tables found (expected _AUD tables for GMP compliance)"
    fi

    # ---- Phase 3: Record Binary Log Position ----
    log_info "Phase 3: Recording binary log position..."

    local binlog_info="${TARGET_DIR}/binlog_position.txt"
    mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
        -e "SHOW MASTER STATUS\G" > "$binlog_info" 2>/dev/null || \
    mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
        -e "SHOW BINARY LOG STATUS\G" > "$binlog_info" 2>/dev/null || \
    echo "Binary logging not enabled" > "$binlog_info"

    # ---- Phase 4: Record Table Row Counts (Verification Data) ----
    log_info "Phase 4: Recording row counts for verification..."

    local rowcount_file="${TARGET_DIR}/row_counts.txt"
    mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
        -e "SELECT table_name, table_rows
            FROM information_schema.tables
            WHERE table_schema='${MYSQL_DATABASE}'
            ORDER BY table_name" \
        "$MYSQL_DATABASE" > "$rowcount_file" 2>/dev/null

    # ---- Phase 5: Compress ----
    log_info "Phase 5: Compressing dump..."

    gzip -"${COMPRESSION_LEVEL}" "$dump_file"
    [ -f "$audit_dump" ] && gzip -"${COMPRESSION_LEVEL}" "$audit_dump"

    local compressed_file="${dump_file}.gz"
    local compressed_size
    compressed_size=$(du -sh "$compressed_file" | cut -f1)
    log_info "Compressed: ${compressed_size}"

    # ---- Phase 6: Checksums ----
    log_info "Phase 6: Generating checksums..."
    generate_checksum "$compressed_file"
    [ -f "${audit_dump}.gz" ] && generate_checksum "${audit_dump}.gz"

    # ---- Phase 7: Encrypt ----
    log_info "Phase 7: Encrypting backup..."
    encrypt_file "$compressed_file"
    [ -f "${audit_dump}.gz" ] && encrypt_file "${audit_dump}.gz"

    # ---- Phase 8: Write Metadata ----
    write_metadata "$TARGET_DIR" "$BACKUP_TYPE"

    # ---- Phase 9: Upload to S3 ----
    log_info "Phase 9: Uploading to cloud storage..."
    for file in "${TARGET_DIR}"/*.gz.gpg "${TARGET_DIR}"/*.gz; do
        [ -f "$file" ] && upload_to_s3 "$file" "${BACKUP_TYPE}"
    done

    # ---- Phase 10: Cleanup Old Backups ----
    log_info "Phase 10: Retention cleanup..."
    cleanup_old_backups "$BACKUP_DAILY_DIR" "$RETENTION_DAILY_DAYS" "daily"
    cleanup_old_backups "$BACKUP_WEEKLY_DIR" "$RETENTION_WEEKLY_DAYS" "weekly"
    cleanup_old_backups "$BACKUP_MONTHLY_DIR" "$RETENTION_MONTHLY_DAYS" "monthly"

    # ---- Done ----
    local end_time=$(date +%s)
    local duration=$(( end_time - start_time ))

    log_info "=========================================="
    log_info "MySQL Full Backup COMPLETE"
    log_info "Type:     ${BACKUP_TYPE}"
    log_info "Location: ${TARGET_DIR}"
    log_info "Size:     ${compressed_size}"
    log_info "Duration: ${duration}s"
    log_info "=========================================="

    log_audit "BACKUP_COMPLETE" "type=${BACKUP_TYPE} dir=${TARGET_DIR} size=${compressed_size} duration=${duration}s"
    send_alert "Backup Complete" "Type: ${BACKUP_TYPE}\nSize: ${compressed_size}\nDuration: ${duration}s" "INFO"

    release_lock
}

on_error() {
    log_error "Backup FAILED with exit code $?"
    log_audit "BACKUP_FAILED" "type=${BACKUP_TYPE}"
    send_alert "BACKUP FAILED" "MySQL full backup (${BACKUP_TYPE}) failed on $(hostname)" "ERROR"
    release_lock
    exit 1
}

main "$@"
