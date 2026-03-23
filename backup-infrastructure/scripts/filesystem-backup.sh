#!/usr/bin/env bash
# =============================================================================
# VALIDATION SYSTEM v2.12.0-ENTERPRISE - Filesystem Backup
# =============================================================================
# PURPOSE:  Backup application files (uploads, configs, keystores, certificates)
# SCHEDULE: Daily at 02:30 (after MySQL backup)
# GMP:      Preserves file permissions, timestamps, signed PDF integrity
# =============================================================================
#
# BACKED UP PATHS:
#   /opt/app/uploads/          - Signed PDFs, certificates, measurement files
#   /opt/app/uploads/signed/   - Electronically signed validation documents
#   /opt/app/uploads/certificates/ - Calibration certificates
#   /opt/app/config/           - Keystores, SSL certs, application configs
#   docker-compose.yml         - Docker orchestration config
#   src/main/resources/db/migration/ - SQL migration files
#
# USAGE:
#   ./filesystem-backup.sh [daily|weekly|monthly]
#
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/backup-env.sh"
source "${SCRIPT_DIR}/backup-functions.sh"

BACKUP_TYPE="${1:-daily}"
SNAPSHOT_NAME="vcc_files_${BACKUP_TIMESTAMP}"

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
    log_info "STARTING Filesystem Backup (${BACKUP_TYPE})"
    log_info "=========================================="
    log_audit "FILES_BACKUP_START" "type=${BACKUP_TYPE}"

    ensure_directories
    acquire_lock
    trap 'release_lock' EXIT

    check_disk_space "$BACKUP_BASE_DIR" 5

    mkdir -p "$TARGET_DIR"
    chmod 700 "$TARGET_DIR"

    local start_time=$(date +%s)
    local archive_name="validation_files_${BACKUP_TIMESTAMP}.tar.gz"
    local archive_path="${TARGET_DIR}/${archive_name}"

    # ---- Build list of paths to backup ----
    local backup_paths=()
    local skipped_paths=()

    # Uploads directory (signed PDFs, certs, measurements)
    if [ -d "$APP_UPLOADS_DIR" ]; then
        backup_paths+=("$APP_UPLOADS_DIR")
        log_info "Including: $APP_UPLOADS_DIR"
    else
        skipped_paths+=("$APP_UPLOADS_DIR")
        log_warn "Skipping (not found): $APP_UPLOADS_DIR"
    fi

    # Config directory (keystores, SSL, properties)
    if [ -d "$APP_CONFIG_DIR" ]; then
        backup_paths+=("$APP_CONFIG_DIR")
        log_info "Including: $APP_CONFIG_DIR"
    else
        skipped_paths+=("$APP_CONFIG_DIR")
        log_warn "Skipping (not found): $APP_CONFIG_DIR"
    fi

    # Application logs (optional, for forensics)
    if [ -d "$APP_LOG_DIR" ]; then
        backup_paths+=("$APP_LOG_DIR")
        log_info "Including: $APP_LOG_DIR"
    fi

    # Docker Compose file
    local compose_file="/opt/app/docker-compose.yml"
    if [ -f "$compose_file" ]; then
        backup_paths+=("$compose_file")
        log_info "Including: $compose_file"
    fi

    # SQL Migration files (version-controlled, but belt-and-suspenders)
    local migrations_dir="/opt/app/src/main/resources/db/migration"
    if [ -d "$migrations_dir" ]; then
        backup_paths+=("$migrations_dir")
        log_info "Including: $migrations_dir"
    fi

    if [ ${#backup_paths[@]} -eq 0 ]; then
        log_error "No paths found to backup!"
        send_alert "FILES BACKUP FAILED" "No backup paths exist on $(hostname)" "ERROR"
        return 1
    fi

    # ---- Create Archive ----
    log_info "Creating archive: ${archive_name}"

    tar -czf "$archive_path" \
        --preserve-permissions \
        --numeric-owner \
        --warning=no-file-changed \
        "${backup_paths[@]}" \
        2>> "${BACKUP_LOG_FILE}" || {
            # tar returns 1 if files changed during archive (acceptable for logs)
            if [ $? -eq 1 ]; then
                log_warn "Some files changed during archiving (non-critical)"
            else
                log_error "tar failed with exit code $?"
                return 1
            fi
        }

    local archive_size
    archive_size=$(du -sh "$archive_path" | cut -f1)
    log_info "Archive created: ${archive_size}"

    # ---- File Inventory ----
    log_info "Recording file inventory..."
    local inventory_file="${TARGET_DIR}/file_inventory.txt"

    tar -tzf "$archive_path" > "$inventory_file" 2>/dev/null
    local file_count=$(wc -l < "$inventory_file")
    log_info "Files in archive: ${file_count}"

    # ---- Docker Volume Backup (if Docker is running) ----
    if command -v docker &> /dev/null; then
        log_info "Backing up Docker volumes..."

        for volume in "$DOCKER_MYSQL_VOLUME" "$DOCKER_UPLOADS_VOLUME"; do
            if docker volume inspect "$volume" &> /dev/null; then
                local vol_archive="${TARGET_DIR}/docker_volume_${volume}_${BACKUP_TIMESTAMP}.tar.gz"

                docker run --rm \
                    -v "${volume}:/source:ro" \
                    -v "${TARGET_DIR}:/backup" \
                    alpine:3.19 \
                    tar -czf "/backup/docker_volume_${volume}_${BACKUP_TIMESTAMP}.tar.gz" \
                        -C /source . \
                    2>> "${BACKUP_LOG_FILE}"

                if [ -f "$vol_archive" ]; then
                    local vol_size=$(du -sh "$vol_archive" | cut -f1)
                    log_info "Docker volume '$volume' backed up: $vol_size"
                    generate_checksum "$vol_archive"
                fi
            else
                log_warn "Docker volume '$volume' not found, skipping"
            fi
        done
    else
        log_info "Docker not available, skipping volume backups"
    fi

    # ---- Checksum ----
    log_info "Generating checksums..."
    generate_checksum "$archive_path"

    # ---- Encrypt ----
    log_info "Encrypting archive..."
    encrypt_file "$archive_path"

    # ---- Metadata ----
    write_metadata "$TARGET_DIR" "filesystem-${BACKUP_TYPE}"

    # ---- Upload to S3 ----
    log_info "Uploading to cloud storage..."
    for file in "${TARGET_DIR}"/*.tar.gz.gpg "${TARGET_DIR}"/*.tar.gz; do
        [ -f "$file" ] && upload_to_s3 "$file" "files/${BACKUP_TYPE}"
    done

    # ---- Cleanup ----
    cleanup_old_backups "$BACKUP_DAILY_DIR" "$RETENTION_DAILY_DAYS" "daily-files"

    # ---- Summary ----
    local end_time=$(date +%s)
    local duration=$(( end_time - start_time ))

    log_info "=========================================="
    log_info "Filesystem Backup COMPLETE"
    log_info "Type:       ${BACKUP_TYPE}"
    log_info "Location:   ${TARGET_DIR}"
    log_info "Size:       ${archive_size}"
    log_info "Files:      ${file_count}"
    log_info "Duration:   ${duration}s"
    log_info "Skipped:    ${skipped_paths[*]:-none}"
    log_info "=========================================="

    log_audit "FILES_BACKUP_COMPLETE" "type=${BACKUP_TYPE} size=${archive_size} files=${file_count} duration=${duration}s"
    send_alert "Files Backup Complete" "Type: ${BACKUP_TYPE}\nSize: ${archive_size}\nFiles: ${file_count}" "INFO"

    release_lock
}

main "$@"
