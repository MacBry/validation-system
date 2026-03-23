#!/usr/bin/env bash
# =============================================================================
# VALIDATION SYSTEM v2.12.0-ENTERPRISE - Shared Backup Functions
# =============================================================================
# PURPOSE: Reusable functions for logging, alerting, checksums, encryption
# =============================================================================

set -euo pipefail

# ---- Logging ----

log_info() {
    local msg="[$(date '+%Y-%m-%d %H:%M:%S')] [INFO]  $1"
    echo "$msg" | tee -a "${BACKUP_LOG_FILE:-/dev/null}"
}

log_warn() {
    local msg="[$(date '+%Y-%m-%d %H:%M:%S')] [WARN]  $1"
    echo "$msg" | tee -a "${BACKUP_LOG_FILE:-/dev/null}" >&2
}

log_error() {
    local msg="[$(date '+%Y-%m-%d %H:%M:%S')] [ERROR] $1"
    echo "$msg" | tee -a "${BACKUP_LOG_FILE:-/dev/null}" >&2
}

log_audit() {
    # GMP-compliant audit log entry (append-only, timestamped, with operator)
    local action="$1"
    local details="$2"
    local audit_file="${BACKUP_LOG_DIR}/backup_audit_trail.log"
    local operator="${BACKUP_OPERATOR:-system-cron}"
    local entry="$(date -u '+%Y-%m-%dT%H:%M:%SZ')|${operator}|${action}|${details}|$(hostname)"
    echo "$entry" >> "$audit_file"
    chmod 440 "$audit_file" 2>/dev/null || true
}

# ---- Directory Setup ----

ensure_directories() {
    local dirs=(
        "$BACKUP_DAILY_DIR"
        "$BACKUP_WEEKLY_DIR"
        "$BACKUP_MONTHLY_DIR"
        "$BACKUP_BINLOG_DIR"
        "$BACKUP_TEMP_DIR"
        "$BACKUP_LOG_DIR"
    )
    for dir in "${dirs[@]}"; do
        if [ ! -d "$dir" ]; then
            mkdir -p "$dir"
            chmod 700 "$dir"
            log_info "Created directory: $dir"
        fi
    done
}

# ---- Checksums ----

generate_checksum() {
    local file="$1"
    local checksum_file="${file}.sha256"
    sha256sum "$file" > "$checksum_file"
    chmod 440 "$checksum_file"
    log_info "Checksum generated: $checksum_file"
}

verify_checksum() {
    local file="$1"
    local checksum_file="${file}.sha256"
    if [ ! -f "$checksum_file" ]; then
        log_error "Checksum file not found: $checksum_file"
        return 1
    fi
    if sha256sum -c "$checksum_file" --quiet 2>/dev/null; then
        log_info "Checksum verified OK: $file"
        return 0
    else
        log_error "Checksum MISMATCH: $file"
        return 1
    fi
}

# ---- Encryption ----

encrypt_file() {
    local input_file="$1"
    local output_file="${input_file}.gpg"

    if [ -z "${GPG_RECIPIENT:-}" ]; then
        log_warn "GPG_RECIPIENT not set, skipping encryption for: $input_file"
        return 0
    fi

    gpg --batch --yes --trust-model always \
        --recipient "$GPG_RECIPIENT" \
        --output "$output_file" \
        --encrypt "$input_file"

    if [ $? -eq 0 ]; then
        log_info "Encrypted: $output_file"
        rm -f "$input_file"
        log_audit "ENCRYPT" "file=$output_file recipient=$GPG_RECIPIENT"
    else
        log_error "Encryption failed: $input_file"
        return 1
    fi
}

decrypt_file() {
    local input_file="$1"
    local output_file="${input_file%.gpg}"

    gpg --batch --yes \
        --output "$output_file" \
        --decrypt "$input_file"

    if [ $? -eq 0 ]; then
        log_info "Decrypted: $output_file"
        return 0
    else
        log_error "Decryption failed: $input_file"
        return 1
    fi
}

# ---- S3 Upload ----

upload_to_s3() {
    local local_file="$1"
    local s3_prefix="$2"
    local filename=$(basename "$local_file")
    local s3_path="${S3_BUCKET}/${s3_prefix}/${BACKUP_YEAR}/${BACKUP_MONTH}/${filename}"

    if [ -z "${S3_BUCKET:-}" ] || [ "$S3_BUCKET" = "s3://" ]; then
        log_warn "S3_BUCKET not configured, skipping upload: $local_file"
        return 0
    fi

    aws s3 cp "$local_file" "$s3_path" \
        --storage-class STANDARD_IA \
        --sse AES256 \
        --region "$S3_REGION" \
        2>&1 | tee -a "${BACKUP_LOG_FILE:-/dev/null}"

    if [ ${PIPESTATUS[0]} -eq 0 ]; then
        log_info "Uploaded to S3: $s3_path"
        log_audit "S3_UPLOAD" "file=$filename s3_path=$s3_path"
    else
        log_error "S3 upload failed: $local_file -> $s3_path"
        return 1
    fi
}

# ---- Alerting ----

send_alert() {
    local subject="$1"
    local body="$2"
    local severity="${3:-INFO}"

    # Email alert
    if [ -n "${ALERT_EMAIL:-}" ]; then
        echo "$body" | mail -s "[VCC-BACKUP] [$severity] $subject" "$ALERT_EMAIL" 2>/dev/null || true
    fi

    # Slack alert
    if [ -n "${SLACK_WEBHOOK_URL:-}" ]; then
        local color="#36a64f"
        [ "$severity" = "ERROR" ] && color="#ff0000"
        [ "$severity" = "WARN" ] && color="#ffcc00"

        curl -s -X POST "$SLACK_WEBHOOK_URL" \
            -H 'Content-Type: application/json' \
            -d "{
                \"attachments\": [{
                    \"color\": \"$color\",
                    \"title\": \"[VCC-BACKUP] $subject\",
                    \"text\": \"$body\",
                    \"ts\": $(date +%s)
                }]
            }" 2>/dev/null || true
    fi
}

# ---- Metadata ----

write_metadata() {
    local backup_dir="$1"
    local backup_type="$2"

    local db_size
    db_size=$(du -sh "${backup_dir}" 2>/dev/null | cut -f1 || echo "unknown")

    cat > "${backup_dir}/backup_metadata.json" <<METADATA
{
    "timestamp": "$(date -u '+%Y-%m-%dT%H:%M:%SZ')",
    "hostname": "$(hostname)",
    "backup_type": "$backup_type",
    "database": "$MYSQL_DATABASE",
    "mysql_host": "$MYSQL_HOST",
    "mysql_port": "$MYSQL_PORT",
    "backup_size": "$db_size",
    "operator": "${BACKUP_OPERATOR:-system-cron}",
    "script_version": "2.12.0",
    "encrypted": $([ -n "${GPG_RECIPIENT:-}" ] && echo "true" || echo "false"),
    "gmp_compliant": true
}
METADATA
    chmod 440 "${backup_dir}/backup_metadata.json"
    log_info "Metadata written: ${backup_dir}/backup_metadata.json"
}

# ---- Retention / Cleanup ----

cleanup_old_backups() {
    local directory="$1"
    local max_age_days="$2"
    local description="$3"

    if [ ! -d "$directory" ]; then
        return 0
    fi

    local count
    count=$(find "$directory" -maxdepth 1 -type d -mtime +"$max_age_days" | wc -l)

    if [ "$count" -gt 0 ]; then
        log_info "Cleaning up $count ${description} backups older than ${max_age_days} days"
        find "$directory" -maxdepth 1 -type d -mtime +"$max_age_days" -exec rm -rf {} +
        log_audit "CLEANUP" "directory=$directory max_age=${max_age_days}d count=$count"
    fi
}

# ---- Lock File ----

acquire_lock() {
    local lock_file="${BACKUP_TEMP_DIR}/backup.lock"
    if [ -f "$lock_file" ]; then
        local lock_pid
        lock_pid=$(cat "$lock_file" 2>/dev/null)
        if kill -0 "$lock_pid" 2>/dev/null; then
            log_error "Another backup process is running (PID: $lock_pid)"
            return 1
        else
            log_warn "Stale lock file found, removing"
            rm -f "$lock_file"
        fi
    fi
    echo $$ > "$lock_file"
}

release_lock() {
    rm -f "${BACKUP_TEMP_DIR}/backup.lock"
}

# ---- Pre-flight Checks ----

check_mysql_connection() {
    if mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
        -e "SELECT 1" "$MYSQL_DATABASE" > /dev/null 2>&1; then
        log_info "MySQL connection OK"
        return 0
    else
        log_error "MySQL connection FAILED ($MYSQL_HOST:$MYSQL_PORT/$MYSQL_DATABASE)"
        return 1
    fi
}

check_disk_space() {
    local directory="$1"
    local min_free_gb="${2:-5}"

    local free_kb
    free_kb=$(df -k "$directory" | awk 'NR==2 {print $4}')
    local free_gb=$((free_kb / 1024 / 1024))

    if [ "$free_gb" -lt "$min_free_gb" ]; then
        log_error "Insufficient disk space: ${free_gb}GB free < ${min_free_gb}GB required"
        send_alert "LOW DISK SPACE" "Only ${free_gb}GB free on $directory" "ERROR"
        return 1
    fi
    log_info "Disk space OK: ${free_gb}GB free on $directory"
}
