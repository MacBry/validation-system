#!/usr/bin/env bash
# =============================================================================
# VALIDATION SYSTEM v2.12.0-ENTERPRISE - MySQL Incremental Backup
# =============================================================================
# PURPOSE:  Incremental backup using Percona XtraBackup
# SCHEDULE: Every 6 hours between full backups
# NOTE:     Requires Percona XtraBackup 8.0 installed
# =============================================================================
#
# STRATEGY:
#   - Sunday 03:00:  Full XtraBackup (base)
#   - Mon-Sat 02:00: Incremental based on last backup
#   - Every 6h:      Binary log flush + copy
#
# USAGE:
#   ./mysql-incremental-backup.sh [full|incremental|binlog]
#
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/backup-env.sh"
source "${SCRIPT_DIR}/backup-functions.sh"

BACKUP_MODE="${1:-incremental}"

# ---- Percona XtraBackup Full ----

xtrabackup_full() {
    local target="${XTRABACKUP_TARGET_DIR}/base_${BACKUP_TIMESTAMP}"
    mkdir -p "$target"

    log_info "Starting XtraBackup FULL backup..."
    log_audit "XTRABACKUP_FULL_START" "target=$target"

    "${XTRABACKUP_BIN}" --backup \
        --host="$MYSQL_HOST" \
        --port="$MYSQL_PORT" \
        --user="$MYSQL_USER" \
        --target-dir="$target" \
        --parallel="${PARALLEL_COMPRESSION_THREADS}" \
        --compress \
        --compress-threads="${PARALLEL_COMPRESSION_THREADS}" \
        2>> "${BACKUP_LOG_FILE}"

    if [ $? -eq 0 ]; then
        # Record this as the new base for incremental backups
        echo "$target" > "${XTRABACKUP_TARGET_DIR}/last_full_base.txt"
        local size=$(du -sh "$target" | cut -f1)
        log_info "XtraBackup full complete: $target ($size)"
        log_audit "XTRABACKUP_FULL_COMPLETE" "target=$target size=$size"

        generate_checksum "${target}/xtrabackup_checkpoints"
        write_metadata "$target" "xtrabackup-full"
        upload_to_s3 "$target" "xtrabackup/full"
    else
        log_error "XtraBackup full backup FAILED"
        send_alert "XtraBackup FAILED" "Full backup failed on $(hostname)" "ERROR"
        return 1
    fi
}

# ---- Percona XtraBackup Incremental ----

xtrabackup_incremental() {
    local base_file="${XTRABACKUP_TARGET_DIR}/last_full_base.txt"

    if [ ! -f "$base_file" ]; then
        log_warn "No full base found, running full backup first"
        xtrabackup_full
        return
    fi

    local base_dir=$(cat "$base_file")
    if [ ! -d "$base_dir" ]; then
        log_warn "Base directory missing ($base_dir), running full backup"
        xtrabackup_full
        return
    fi

    local target="${XTRABACKUP_TARGET_DIR}/incr_${BACKUP_TIMESTAMP}"
    mkdir -p "$target"

    log_info "Starting XtraBackup INCREMENTAL backup (base: $base_dir)..."
    log_audit "XTRABACKUP_INCR_START" "target=$target base=$base_dir"

    "${XTRABACKUP_BIN}" --backup \
        --host="$MYSQL_HOST" \
        --port="$MYSQL_PORT" \
        --user="$MYSQL_USER" \
        --target-dir="$target" \
        --incremental-basedir="$base_dir" \
        --parallel="${PARALLEL_COMPRESSION_THREADS}" \
        --compress \
        --compress-threads="${PARALLEL_COMPRESSION_THREADS}" \
        2>> "${BACKUP_LOG_FILE}"

    if [ $? -eq 0 ]; then
        local size=$(du -sh "$target" | cut -f1)
        log_info "XtraBackup incremental complete: $target ($size)"
        log_audit "XTRABACKUP_INCR_COMPLETE" "target=$target size=$size"

        generate_checksum "${target}/xtrabackup_checkpoints"
        write_metadata "$target" "xtrabackup-incremental"
    else
        log_error "XtraBackup incremental backup FAILED"
        send_alert "XtraBackup FAILED" "Incremental backup failed" "ERROR"
        return 1
    fi
}

# ---- Binary Log Backup ----

binlog_backup() {
    log_info "Starting binary log backup..."

    local binlog_target="${BACKUP_BINLOG_DIR}/${BACKUP_TIMESTAMP}"
    mkdir -p "$binlog_target"

    # Flush and get current binary log info
    mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
        -e "FLUSH BINARY LOGS;" 2>/dev/null || true

    # Get binary log file list
    local binlog_list
    binlog_list=$(mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
        -N -e "SHOW BINARY LOGS;" 2>/dev/null || echo "")

    if [ -z "$binlog_list" ]; then
        log_warn "Binary logging not enabled or no logs available"
        log_info "To enable: SET GLOBAL log_bin = ON in MySQL config"
        return 0
    fi

    # Copy binary logs using mysqlbinlog
    local mysql_datadir
    mysql_datadir=$(mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
        -N -e "SELECT @@datadir;" 2>/dev/null || echo "/var/lib/mysql/")

    local binlog_files
    binlog_files=$(mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
        -N -e "SHOW BINARY LOGS;" 2>/dev/null | awk '{print $1}')

    for logfile in $binlog_files; do
        local src="${mysql_datadir}${logfile}"
        if [ -f "$src" ]; then
            cp "$src" "$binlog_target/"
            log_info "Copied binlog: $logfile"
        fi
    done

    local size=$(du -sh "$binlog_target" | cut -f1)
    log_info "Binary log backup complete: $binlog_target ($size)"
    log_audit "BINLOG_BACKUP" "target=$binlog_target size=$size"

    # Cleanup old binlog backups (keep 7 days)
    find "$BACKUP_BINLOG_DIR" -maxdepth 1 -type d -mtime +7 -exec rm -rf {} + 2>/dev/null || true
}

# ---- Main ----

main() {
    log_info "=========================================="
    log_info "MySQL Incremental Backup (mode: ${BACKUP_MODE})"
    log_info "=========================================="

    ensure_directories
    acquire_lock
    trap 'release_lock' EXIT

    check_mysql_connection

    case "$BACKUP_MODE" in
        full)
            xtrabackup_full
            ;;
        incremental)
            xtrabackup_incremental
            ;;
        binlog)
            binlog_backup
            ;;
        *)
            echo "Usage: $0 [full|incremental|binlog]"
            exit 1
            ;;
    esac

    release_lock
}

main "$@"
