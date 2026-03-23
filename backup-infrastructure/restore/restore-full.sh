#!/usr/bin/env bash
# =============================================================================
# VALIDATION SYSTEM v2.12.0-ENTERPRISE - Full System Restore
# =============================================================================
# PURPOSE:  Restore complete system from backup (database + files)
# GMP:      Audited, verified, point-in-time capable
# =============================================================================
#
# USAGE:
#   ./restore-full.sh <backup_directory>
#   ./restore-full.sh /opt/backups/validation-system/daily/vcc_full_2026-03-20_02-00-00
#
# OPTIONS:
#   --dry-run        Show what would be restored without executing
#   --skip-files     Restore only database
#   --skip-db        Restore only files
#   --verify-only    Verify backup integrity without restoring
#   --target-db      Target database name (default: validation_system)
#   --pitr           Point-in-time: apply binlogs up to specific timestamp
#
# PREREQUISITES:
#   - MySQL running and accessible
#   - Sufficient disk space
#   - GPG key available (if backup encrypted)
#   - Application STOPPED (to prevent data conflicts)
#
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../scripts/backup-env.sh"
source "${SCRIPT_DIR}/../scripts/backup-functions.sh"

# ---- Parse Arguments ----
BACKUP_DIR=""
DRY_RUN=false
SKIP_FILES=false
SKIP_DB=false
VERIFY_ONLY=false
TARGET_DB="$MYSQL_DATABASE"
PITR_TIMESTAMP=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --dry-run)      DRY_RUN=true; shift ;;
        --skip-files)   SKIP_FILES=true; shift ;;
        --skip-db)      SKIP_DB=true; shift ;;
        --verify-only)  VERIFY_ONLY=true; shift ;;
        --target-db)    TARGET_DB="$2"; shift 2 ;;
        --pitr)         PITR_TIMESTAMP="$2"; shift 2 ;;
        -*)             echo "Unknown option: $1"; exit 1 ;;
        *)              BACKUP_DIR="$1"; shift ;;
    esac
done

if [ -z "$BACKUP_DIR" ]; then
    echo "ERROR: Backup directory required"
    echo "Usage: $0 <backup_directory> [options]"
    echo ""
    echo "Available backups:"
    echo "  Daily:   $(ls -d ${BACKUP_DAILY_DIR}/vcc_* 2>/dev/null | tail -5 || echo 'none')"
    echo "  Weekly:  $(ls -d ${BACKUP_WEEKLY_DIR}/vcc_* 2>/dev/null | tail -3 || echo 'none')"
    echo "  Monthly: $(ls -d ${BACKUP_MONTHLY_DIR}/vcc_* 2>/dev/null | tail -3 || echo 'none')"
    exit 1
fi

if [ ! -d "$BACKUP_DIR" ]; then
    echo "ERROR: Backup directory not found: $BACKUP_DIR"
    exit 1
fi

# ---- Main Restore ----

main() {
    log_info "============================================================"
    log_info "FULL SYSTEM RESTORE"
    log_info "Source:    $BACKUP_DIR"
    log_info "Target DB: $TARGET_DB"
    log_info "Dry Run:   $DRY_RUN"
    log_info "============================================================"
    log_audit "RESTORE_START" "source=$BACKUP_DIR target_db=$TARGET_DB dry_run=$DRY_RUN"

    # ---- Step 1: Verify Backup Integrity ----
    echo ""
    echo "================================================================"
    echo "  STEP 1: Verifying backup integrity"
    echo "================================================================"

    # Check metadata
    if [ -f "${BACKUP_DIR}/backup_metadata.json" ]; then
        echo "Backup metadata:"
        cat "${BACKUP_DIR}/backup_metadata.json"
        echo ""
    else
        log_warn "No metadata file found"
    fi

    # Verify checksums
    local checksum_ok=true
    for chk in "${BACKUP_DIR}"/*.sha256; do
        [ -f "$chk" ] || continue
        echo "Verifying: $(basename "$chk")"
        if ! sha256sum -c "$chk" --quiet 2>/dev/null; then
            log_error "CHECKSUM FAILED: $chk"
            checksum_ok=false
        else
            echo "  -> OK"
        fi
    done

    if ! $checksum_ok; then
        log_error "Checksum verification FAILED - backup may be corrupted"
        echo "ABORT: Restore cancelled due to checksum failure"
        echo "If you trust this backup, remove .sha256 files and retry"
        exit 1
    fi

    if $VERIFY_ONLY; then
        echo ""
        echo "Verification complete. Backup appears intact."
        exit 0
    fi

    # ---- Step 2: Pre-restore Safety ----
    echo ""
    echo "================================================================"
    echo "  STEP 2: Pre-restore safety checks"
    echo "================================================================"

    if ! $DRY_RUN; then
        echo ""
        echo "WARNING: This will OVERWRITE the current database and files!"
        echo "Target database: $TARGET_DB"
        echo "Target files:    $APP_UPLOADS_DIR"
        echo ""
        read -p "Type 'RESTORE' to confirm: " confirmation
        if [ "$confirmation" != "RESTORE" ]; then
            echo "Restore cancelled by operator."
            log_audit "RESTORE_CANCELLED" "operator_cancelled"
            exit 0
        fi
    fi

    # ---- Step 3: Decrypt (if encrypted) ----
    echo ""
    echo "================================================================"
    echo "  STEP 3: Decrypting backup files (if needed)"
    echo "================================================================"

    for gpg_file in "${BACKUP_DIR}"/*.gpg; do
        [ -f "$gpg_file" ] || continue
        echo "Decrypting: $(basename "$gpg_file")"
        if ! $DRY_RUN; then
            decrypt_file "$gpg_file"
        fi
    done

    # ---- Step 4: Decompress ----
    echo ""
    echo "================================================================"
    echo "  STEP 4: Decompressing backup files"
    echo "================================================================"

    local db_dump=""
    for gz_file in "${BACKUP_DIR}"/validation_system_full.sql.gz; do
        [ -f "$gz_file" ] || continue
        echo "Decompressing: $(basename "$gz_file")"
        if ! $DRY_RUN; then
            gunzip -k "$gz_file"
            db_dump="${gz_file%.gz}"
        fi
    done

    if [ -z "$db_dump" ] && ! $SKIP_DB; then
        # Try uncompressed
        db_dump=$(find "$BACKUP_DIR" -name "validation_system_full.sql" -type f | head -1)
        if [ -z "$db_dump" ]; then
            log_error "No database dump found in backup"
            if ! $SKIP_DB; then
                exit 1
            fi
        fi
    fi

    # ---- Step 5: Restore Database ----
    if ! $SKIP_DB && [ -n "$db_dump" ]; then
        echo ""
        echo "================================================================"
        echo "  STEP 5: Restoring MySQL database"
        echo "================================================================"

        echo "Dump file:    $db_dump"
        echo "Target DB:    $TARGET_DB"
        echo "Dump size:    $(du -sh "$db_dump" | cut -f1)"

        if ! $DRY_RUN; then
            # Create database if not exists
            mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
                -e "CREATE DATABASE IF NOT EXISTS \`${TARGET_DB}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

            # Restore
            local restore_start=$(date +%s)

            mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
                --max_allowed_packet=512M \
                "$TARGET_DB" < "$db_dump"

            local restore_end=$(date +%s)
            local restore_duration=$(( restore_end - restore_start ))

            log_info "Database restored in ${restore_duration}s"
            log_audit "DB_RESTORE_COMPLETE" "target=$TARGET_DB duration=${restore_duration}s"
        else
            echo "[DRY RUN] Would restore $db_dump -> $TARGET_DB"
        fi
    fi

    # ---- Step 5b: Point-in-Time Recovery (if requested) ----
    if [ -n "$PITR_TIMESTAMP" ]; then
        echo ""
        echo "================================================================"
        echo "  STEP 5b: Point-in-Time Recovery up to $PITR_TIMESTAMP"
        echo "================================================================"

        local binlog_dir="${BACKUP_DIR}"
        # Also check dedicated binlog backup dir
        local latest_binlog_backup
        latest_binlog_backup=$(ls -td "${BACKUP_BINLOG_DIR}"/* 2>/dev/null | head -1)

        if [ -n "$latest_binlog_backup" ]; then
            binlog_dir="$latest_binlog_backup"
        fi

        local binlog_files
        binlog_files=$(find "$binlog_dir" -name "mysql-bin.*" -o -name "binlog.*" 2>/dev/null | sort)

        if [ -n "$binlog_files" ]; then
            echo "Applying binary logs up to: $PITR_TIMESTAMP"

            if ! $DRY_RUN; then
                mysqlbinlog \
                    --stop-datetime="$PITR_TIMESTAMP" \
                    $binlog_files \
                    | mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" "$TARGET_DB"

                log_info "PITR applied up to $PITR_TIMESTAMP"
                log_audit "PITR_COMPLETE" "stop_datetime=$PITR_TIMESTAMP"
            else
                echo "[DRY RUN] Would apply binlogs up to $PITR_TIMESTAMP"
            fi
        else
            log_warn "No binary log files found for PITR"
        fi
    fi

    # ---- Step 6: Restore Files ----
    if ! $SKIP_FILES; then
        echo ""
        echo "================================================================"
        echo "  STEP 6: Restoring filesystem"
        echo "================================================================"

        local files_archive
        files_archive=$(find "$BACKUP_DIR" -name "validation_files_*.tar.gz" -type f | head -1)

        if [ -n "$files_archive" ]; then
            echo "Archive: $files_archive"
            echo "Size:    $(du -sh "$files_archive" | cut -f1)"

            if ! $DRY_RUN; then
                # Restore with absolute paths (tar preserves them)
                tar -xzf "$files_archive" -C / \
                    --preserve-permissions \
                    --numeric-owner

                log_info "Files restored from $files_archive"
                log_audit "FILES_RESTORE_COMPLETE" "archive=$(basename "$files_archive")"
            else
                echo "[DRY RUN] Would extract $files_archive to /"
                echo "Contents:"
                tar -tzf "$files_archive" | head -20
                echo "..."
            fi
        else
            log_warn "No filesystem archive found in backup"
        fi

        # Restore Docker volumes if present
        for vol_archive in "${BACKUP_DIR}"/docker_volume_*.tar.gz; do
            [ -f "$vol_archive" ] || continue
            local vol_name
            vol_name=$(basename "$vol_archive" | sed 's/docker_volume_\(.*\)_[0-9-]*.tar.gz/\1/')
            echo "Docker volume: $vol_name ($(du -sh "$vol_archive" | cut -f1))"

            if ! $DRY_RUN; then
                docker volume create "$vol_name" 2>/dev/null || true
                docker run --rm \
                    -v "${vol_name}:/target" \
                    -v "$(dirname "$vol_archive"):/backup:ro" \
                    alpine:3.19 \
                    sh -c "cd /target && tar -xzf /backup/$(basename "$vol_archive")"

                log_info "Docker volume '$vol_name' restored"
            fi
        done
    fi

    # ---- Step 7: Post-Restore Verification ----
    echo ""
    echo "================================================================"
    echo "  STEP 7: Post-restore verification"
    echo "================================================================"

    if ! $DRY_RUN && ! $SKIP_DB; then
        # Compare row counts
        local original_counts="${BACKUP_DIR}/row_counts.txt"
        if [ -f "$original_counts" ]; then
            echo "Comparing row counts with backup..."

            local current_counts="${BACKUP_TEMP_DIR}/current_row_counts.txt"
            mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
                -e "SELECT table_name, table_rows
                    FROM information_schema.tables
                    WHERE table_schema='${TARGET_DB}'
                    ORDER BY table_name" \
                "$TARGET_DB" > "$current_counts" 2>/dev/null

            if diff -q "$original_counts" "$current_counts" > /dev/null 2>&1; then
                echo "Row counts MATCH - restore verified"
                log_info "Post-restore verification: row counts match"
            else
                echo "Row count DIFFERENCES detected (may be normal for approximate InnoDB counts):"
                diff --side-by-side "$original_counts" "$current_counts" 2>/dev/null | head -20 || true
                log_warn "Row count differences detected after restore"
            fi
        fi

        # Quick data integrity checks
        echo ""
        echo "Critical table checks:"

        local tables=("cooling_devices" "validations" "measurement_series" "thermo_recorders" "audit_logs")
        for table in "${tables[@]}"; do
            local count
            count=$(mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
                -N -e "SELECT COUNT(*) FROM \`${table}\`" "$TARGET_DB" 2>/dev/null || echo "TABLE_MISSING")
            echo "  ${table}: ${count} rows"
        done
    fi

    # ---- Done ----
    echo ""
    echo "================================================================"
    echo "  RESTORE COMPLETE"
    echo "================================================================"
    echo ""
    echo "NEXT STEPS:"
    echo "  1. Start the application: docker compose up -d"
    echo "  2. Verify login with admin credentials"
    echo "  3. Check /health endpoint: curl -k https://localhost:8443/actuator/health"
    echo "  4. Verify audit trail in admin panel"
    echo "  5. Spot-check a few validations and measurement series"
    echo ""

    log_audit "RESTORE_COMPLETE" "source=$BACKUP_DIR target_db=$TARGET_DB"
}

main "$@"
