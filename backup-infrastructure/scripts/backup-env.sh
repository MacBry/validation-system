#!/usr/bin/env bash
# =============================================================================
# VALIDATION SYSTEM v2.12.0-ENTERPRISE - Backup Environment Configuration
# =============================================================================
# PURPOSE: Central configuration for all backup scripts
# GMP:     All values auditable, no secrets in plain text
# =============================================================================

# ---- Timestamps ----
export BACKUP_TIMESTAMP=$(date +%Y-%m-%d_%H-%M-%S)
export BACKUP_DATE=$(date +%Y-%m-%d)
export BACKUP_YEAR=$(date +%Y)
export BACKUP_MONTH=$(date +%m)

# ---- MySQL Connection ----
# NEVER store passwords here in production. Use .my.cnf or environment variables.
export MYSQL_HOST="${DB_HOST:-localhost}"
export MYSQL_PORT="${DB_PORT:-3306}"
export MYSQL_USER="${DB_USERNAME:-validation_user}"
export MYSQL_DATABASE="validation_system"
# Password read from ~/.my.cnf [mysqldump] section or DB_PASSWORD env var

# ---- Backup Directories ----
export BACKUP_BASE_DIR="/opt/backups/validation-system"
export BACKUP_DAILY_DIR="${BACKUP_BASE_DIR}/daily"
export BACKUP_WEEKLY_DIR="${BACKUP_BASE_DIR}/weekly"
export BACKUP_MONTHLY_DIR="${BACKUP_BASE_DIR}/monthly"
export BACKUP_BINLOG_DIR="${BACKUP_BASE_DIR}/binlog"
export BACKUP_TEMP_DIR="${BACKUP_BASE_DIR}/tmp"

# ---- Application Paths ----
export APP_UPLOADS_DIR="/opt/app/uploads"
export APP_CONFIG_DIR="/opt/app/config"
export APP_SIGNED_DOCS_DIR="/opt/app/uploads/signed"
export APP_CERTIFICATES_DIR="/opt/app/uploads/certificates"
export APP_LOG_DIR="/var/log/validation-system"

# ---- Docker Volumes ----
export DOCKER_MYSQL_VOLUME="vcc-database-data"
export DOCKER_REDIS_VOLUME="vcc-redis-data"
export DOCKER_UPLOADS_VOLUME="vcc-uploads-data"

# ---- S3 / Cloud Storage ----
export S3_BUCKET="${BACKUP_S3_BUCKET:-s3://vcc-backups-prod}"
export S3_REGION="${BACKUP_S3_REGION:-eu-central-1}"
export S3_DAILY_PREFIX="daily"
export S3_WEEKLY_PREFIX="weekly"
export S3_MONTHLY_PREFIX="monthly"

# ---- Encryption ----
export GPG_RECIPIENT="${BACKUP_GPG_RECIPIENT:-backup@validation-system.company.com}"
export GPG_KEYRING="/opt/app/config/backup-gpg-keyring.gpg"

# ---- Retention Policy (GMP: 7 years for audit, 1 year for operational) ----
export RETENTION_DAILY_DAYS=7
export RETENTION_WEEKLY_DAYS=30
export RETENTION_MONTHLY_DAYS=365
export RETENTION_AUDIT_YEARS=7       # GMP Annex 11 requires minimum 7 years

# ---- Alerting ----
export ALERT_EMAIL="${BACKUP_ALERT_EMAIL:-devops@company.com}"
export SLACK_WEBHOOK_URL="${BACKUP_SLACK_WEBHOOK:-}"

# ---- Logging ----
export BACKUP_LOG_DIR="/var/log/validation-system/backups"
export BACKUP_LOG_FILE="${BACKUP_LOG_DIR}/backup_${BACKUP_DATE}.log"

# ---- Percona XtraBackup (for large DB incremental) ----
export XTRABACKUP_BIN="/usr/bin/xtrabackup"
export XTRABACKUP_TARGET_DIR="${BACKUP_BASE_DIR}/xtrabackup"
export XTRABACKUP_INCREMENTAL_BASEDIR="${XTRABACKUP_TARGET_DIR}/base"

# ---- Performance ----
export MYSQLDUMP_MAX_ALLOWED_PACKET="512M"
export MYSQLDUMP_NET_BUFFER_LENGTH="32768"
export COMPRESSION_LEVEL=6      # gzip 1-9, 6 is good balance
export PARALLEL_COMPRESSION_THREADS=4

# ---- Healthcheck ----
export HEALTHCHECK_MAX_AGE_HOURS=26   # Alert if latest backup older than 26h
export HEALTHCHECK_MIN_SIZE_MB=10     # Alert if backup smaller than 10MB
