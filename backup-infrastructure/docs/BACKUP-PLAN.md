# Validation System v2.12.0 - Backup & Disaster Recovery Plan

## Document Control

| Field               | Value                                    |
|---------------------|------------------------------------------|
| Version             | 1.0                                      |
| Date                | 2026-03-20                               |
| Classification      | GMP Critical / Internal                  |
| Review Frequency    | Annual or after major system changes     |
| Compliance          | GMP Annex 11, FDA 21 CFR Part 11, GAMP5 |

---

## 1. System Overview

### 1.1 What We Are Protecting

| Component             | Location                      | Data Type                        | Criticality |
|-----------------------|-------------------------------|----------------------------------|-------------|
| MySQL Database        | Docker: vcc-database:3306     | All application data             | CRITICAL    |
| Audit Tables (_AUD)   | Same MySQL instance           | Envers audit trail (GMP)         | CRITICAL    |
| Signed PDFs           | /opt/app/uploads/signed/      | Electronically signed validation | CRITICAL    |
| Calibration Certs     | /opt/app/uploads/certificates/| Calibration certificate scans    | HIGH        |
| PKCS12 Keystores      | /opt/app/config/              | Signing keys, SSL certificates   | CRITICAL    |
| SQL Migrations        | src/main/resources/db/migration/ | Schema evolution scripts      | HIGH        |
| Application Config    | docker-compose.yml, .env      | Deployment configuration         | HIGH        |
| Application Logs      | /var/log/validation-system/   | Debug/audit logs                 | MEDIUM      |
| Docker Volumes        | vcc-database-data, vcc-uploads| Persistent container data        | CRITICAL    |

### 1.2 Database Size Estimates

| Table Category         | Estimated Records  | Growth Rate      |
|------------------------|--------------------|------------------|
| MeasurementPoint       | 100,000+           | ~5,000/week      |
| MeasurementSeries      | 1,000+             | ~50/week         |
| Audit Tables (_AUD)    | 50,000+            | ~500/week        |
| Validations            | 500+               | ~10/week         |
| CoolingDevices         | 200+               | ~5/month         |
| Users/Roles            | 50+                | Rare             |

**Estimated total DB size:** 500MB - 2GB (growing ~50MB/month)

---

## 2. Backup Strategy

### 2.1 Backup Schedule

```
TIMELINE (weekly view):
================================================================
Mon  02:00  Daily Full (mysqldump + files)
     08:00  Binlog backup
     14:00  Binlog backup
     20:00  Binlog backup

Tue  02:00  Daily Full
     [binlog every 6h]

Wed  02:00  Daily Full
     [binlog every 6h]

Thu  02:00  Daily Full
     [binlog every 6h]

Fri  02:00  Daily Full
     [binlog every 6h]

Sat  02:00  Daily Full
     [binlog every 6h]

Sun  03:00  Weekly Full (extended retention)
     05:00  DR Test (automated, quarterly manual)
     [binlog every 6h]

1st of Month 04:00  Monthly Archive
================================================================
```

### 2.2 Cron Configuration

```cron
# /etc/cron.d/vcc-backup (or via Docker backup sidecar)

# Daily full backup (Monday-Saturday at 02:00 local time)
0 2 * * 1-6 root /opt/app/backup-infrastructure/scripts/combined-backup.sh daily

# Weekly full backup (Sunday at 03:00)
0 3 * * 0 root /opt/app/backup-infrastructure/scripts/combined-backup.sh weekly

# Monthly archive (1st of each month at 04:00)
0 4 1 * * root /opt/app/backup-infrastructure/scripts/combined-backup.sh monthly

# Binary log backup (every 6 hours)
0 */6 * * * root /opt/app/backup-infrastructure/scripts/mysql-incremental-backup.sh binlog

# Healthcheck (every 4 hours)
0 */4 * * * root /opt/app/backup-infrastructure/scripts/backup-healthcheck.sh

# Percona XtraBackup full (Sunday 01:00, if XtraBackup is installed)
# 0 1 * * 0 root /opt/app/backup-infrastructure/scripts/mysql-incremental-backup.sh full

# Percona XtraBackup incremental (Mon-Sat 01:00)
# 0 1 * * 1-6 root /opt/app/backup-infrastructure/scripts/mysql-incremental-backup.sh incremental
```

### 2.3 Backup Tools

| Tool              | Purpose                              | When to Use                    |
|-------------------|--------------------------------------|--------------------------------|
| mysqldump         | Logical full backup                  | Daily, always (primary tool)   |
| Percona XtraBackup| Physical incremental backup          | When DB exceeds 5GB            |
| Binary Logs       | Point-in-time recovery (PITR)        | Every 6 hours, always          |
| tar + gzip        | Filesystem archival                  | Daily, with DB backup          |
| GPG               | Encryption at rest                   | All backups before storage     |

#### mysqldump Flags Explained

```bash
mysqldump \
  --single-transaction    # InnoDB consistent snapshot (no table locks)
  --routines              # Include stored procedures/functions
  --triggers              # Include triggers
  --events                # Include scheduled events
  --quick                 # Row-by-row retrieval (low memory for large tables)
  --hex-blob              # Binary data as hex (safe for transport)
  --complete-insert       # Full INSERT statements (safer restores)
  --set-gtid-purged=OFF   # Avoid GTID conflicts on restore
  --max_allowed_packet=512M  # Handle large MeasurementPoint rows
```

### 2.4 Retention Policy

| Backup Type     | Local Retention  | S3 Retention      | S3 Storage Class    |
|-----------------|------------------|--------------------|---------------------|
| Daily           | 7 days           | 30 days            | STANDARD_IA         |
| Weekly          | 30 days          | 90 days            | STANDARD_IA         |
| Monthly         | 365 days         | 7 years            | GLACIER             |
| Audit-only dump | 7 years local    | 7 years            | GLACIER_DEEP        |
| Binary logs     | 7 days           | 30 days            | STANDARD_IA         |
| DR test reports | 365 days         | 7 years            | STANDARD_IA         |

**GMP Requirement:** Audit trail data (_AUD tables, revinfo) must be retained for
a minimum of 7 years per GMP Annex 11 and FDA 21 CFR Part 11.

---

## 3. Storage Strategy

### 3.1 Storage Tiers

```
Tier 1 (HOT) - Local Server
  Location: /opt/backups/validation-system/
  Contents: Last 7 daily + last 4 weekly backups
  Access:   Immediate (< 1 minute restore)
  Cost:     Server disk

Tier 2 (WARM) - AWS S3 Standard-IA
  Location: s3://vcc-backups-prod/{daily,weekly}/
  Contents: Last 30 daily + last 12 weekly backups
  Access:   Fast (< 10 minutes download)
  Cost:     ~$0.0125/GB/month
  Encryption: AES-256 server-side (SSE-S3)

Tier 3 (COLD) - AWS S3 Glacier
  Location: s3://vcc-backups-prod/archive/
  Contents: Monthly backups, audit trail archives
  Access:   Slow (3-12 hours retrieval)
  Cost:     ~$0.004/GB/month
  Retention: 7 years (GMP compliance)
```

### 3.2 S3 Bucket Configuration

```json
{
  "Rules": [
    {
      "ID": "DailyToIA",
      "Filter": {"Prefix": "daily/"},
      "Transitions": [
        {"Days": 30, "StorageClass": "STANDARD_IA"}
      ],
      "Expiration": {"Days": 90}
    },
    {
      "ID": "MonthlyToGlacier",
      "Filter": {"Prefix": "monthly/"},
      "Transitions": [
        {"Days": 90, "StorageClass": "GLACIER"},
        {"Days": 365, "StorageClass": "DEEP_ARCHIVE"}
      ],
      "Expiration": {"Days": 2555}
    },
    {
      "ID": "AuditArchive",
      "Filter": {"Prefix": "audit/"},
      "Transitions": [
        {"Days": 30, "StorageClass": "GLACIER_DEEP_ARCHIVE"}
      ],
      "NoncurrentVersionExpiration": {"NoncurrentDays": 2555}
    }
  ]
}
```

### 3.3 Encryption

All backups are encrypted before leaving the server:

1. **GPG Encryption** (backup script level): Asymmetric encryption with company GPG key
2. **S3 SSE-S3** (storage level): AES-256 server-side encryption
3. **TLS in transit**: All S3 uploads use HTTPS

GPG key management:
- Public key stored on backup server: `/opt/app/config/backup-gpg-keyring.gpg`
- Private key stored in offline secure location (e.g., hardware security module)
- Key rotation: Annual
- Emergency access: Two-person rule for private key retrieval

### 3.4 Off-site Replication

Enable S3 Cross-Region Replication (CRR) to a secondary region:

```
Primary:   eu-central-1 (Frankfurt)  -> s3://vcc-backups-prod/
Secondary: eu-west-1 (Ireland)       -> s3://vcc-backups-dr/
```

---

## 4. Automation

### 4.1 Docker Deployment

```bash
# Start backup services alongside the main application
docker compose -f docker-compose.yml \
               -f backup-infrastructure/docker/docker-compose.backup.yml \
               up -d
```

This adds:
- `vcc-backup-mysql`: Sidecar container with cron-scheduled backups
- `vcc-backup-monitor`: Status page at http://localhost:9090

### 4.2 GitHub Actions Integration

Copy `backup-infrastructure/docker/github-actions-backup.yml` to `.github/workflows/backup.yml`.

Required GitHub Secrets:

| Secret                    | Description                          |
|---------------------------|--------------------------------------|
| DB_HOST                   | MySQL host (e.g., RDS endpoint)      |
| DB_PORT                   | MySQL port (3306)                    |
| DB_USERNAME               | Backup user                          |
| DB_PASSWORD               | Backup user password                 |
| AWS_ACCESS_KEY_ID         | AWS IAM for S3 access                |
| AWS_SECRET_ACCESS_KEY     | AWS IAM secret                       |
| AWS_REGION                | AWS region                           |
| BACKUP_S3_BUCKET          | S3 bucket name                       |
| BACKUP_GPG_KEY            | GPG private key (armored)            |
| BACKUP_GPG_RECIPIENT      | GPG recipient email                  |
| SLACK_WEBHOOK_URL         | Slack notifications                  |

### 4.3 MySQL Binary Logging Configuration

For PITR capability, ensure MySQL has binary logging enabled:

```ini
# my.cnf or Docker MySQL config
[mysqld]
log-bin = mysql-bin
binlog-format = ROW
binlog-row-image = FULL
expire_logs_days = 7
max_binlog_size = 100M
server-id = 1
```

For Docker Compose, add to the MySQL service:

```yaml
vcc-database:
  image: mysql:8.0
  command: >
    --log-bin=mysql-bin
    --binlog-format=ROW
    --server-id=1
    --expire-logs-days=7
```

---

## 5. Restore Procedures

### 5.1 Full System Restore (Complete Disaster)

**Scenario:** Server destroyed, need to rebuild from scratch.

**Estimated RTO:** 30-60 minutes (depending on database size and download speed)

```bash
# Step 1: Provision new server with Docker installed

# Step 2: Clone the application repository
git clone https://github.com/company/validation-system.git /opt/app
cd /opt/app

# Step 3: Download latest backup from S3
aws s3 cp s3://vcc-backups-prod/daily/ /opt/backups/latest/ --recursive \
    --exclude "*" --include "$(aws s3 ls s3://vcc-backups-prod/daily/ --recursive | sort | tail -1 | awk '{print $4}')"

# Step 4: Decrypt backup (if encrypted)
gpg --decrypt /opt/backups/latest/*.sql.gz.gpg > /opt/backups/latest/dump.sql.gz

# Step 5: Start MySQL container only
docker compose up -d vcc-database
# Wait for MySQL to be ready
until docker exec vcc-database mysqladmin ping --silent; do sleep 2; done

# Step 6: Restore database
gunzip /opt/backups/latest/dump.sql.gz
docker exec -i vcc-database mysql -u root -p"$DB_PASSWORD" validation_system \
    < /opt/backups/latest/dump.sql

# Step 7: Restore files
tar -xzf /opt/backups/latest/validation_files_*.tar.gz -C /

# Step 8: Start full stack
docker compose up -d

# Step 9: Verify
curl -k https://localhost:8443/actuator/health
# Login and verify data
```

### 5.2 Database-Only Restore

```bash
# Use the restore script
./backup-infrastructure/restore/restore-full.sh /opt/backups/validation-system/daily/vcc_full_2026-03-20_02-00-00 --skip-files
```

### 5.3 Single Table Restore

```bash
# Restore to temporary table first (safe)
./backup-infrastructure/restore/restore-table.sh \
    /opt/backups/validation-system/daily/vcc_full_2026-03-20_02-00-00 \
    measurement_series \
    --to-temp

# Inspect restored data
mysql -u root -p validation_system -e "SELECT COUNT(*) FROM measurement_series_restored;"

# If satisfied, merge back
mysql -u root -p validation_system -e "
    RENAME TABLE measurement_series TO measurement_series_old;
    RENAME TABLE measurement_series_restored TO measurement_series;
    -- Verify, then:
    -- DROP TABLE measurement_series_old;
"
```

### 5.4 Point-in-Time Recovery (PITR)

**Scenario:** Accidental data deletion at 14:35. Restore to 14:34.

```bash
# Step 1: Restore the last full backup (before the incident)
./backup-infrastructure/restore/restore-full.sh \
    /opt/backups/validation-system/daily/vcc_full_2026-03-20_02-00-00 \
    --pitr "2026-03-20 14:34:00" \
    --target-db validation_system_pitr

# Step 2: Verify the PITR database has the correct state
mysql -u root -p validation_system_pitr -e "
    SELECT * FROM validations ORDER BY id DESC LIMIT 5;
"

# Step 3: If correct, swap databases
mysql -u root -p -e "
    -- Stop the application first!
    DROP DATABASE validation_system;
    ALTER DATABASE validation_system_pitr RENAME TO validation_system;
"
```

### 5.5 Docker Volume Restore

```bash
# Stop containers
docker compose down

# Restore MySQL volume
docker run --rm \
    -v vcc-database-data:/target \
    -v /opt/backups/latest:/backup:ro \
    alpine:3.19 \
    sh -c "cd /target && tar -xzf /backup/docker_volume_vcc-database-data_*.tar.gz"

# Restart
docker compose up -d
```

---

## 6. GMP Compliance

### 6.1 Audit Trail for Backup Operations

Every backup operation is logged to an append-only audit trail:

**File:** `/var/log/validation-system/backups/backup_audit_trail.log`

**Format:** `ISO8601_TIMESTAMP|OPERATOR|ACTION|DETAILS|HOSTNAME`

**Example entries:**
```
2026-03-20T02:00:01Z|system-cron|BACKUP_START|type=daily database=validation_system|prod-server-01
2026-03-20T02:03:45Z|system-cron|BACKUP_COMPLETE|type=daily dir=/opt/backups/... size=245M duration=224s|prod-server-01
2026-03-20T02:03:46Z|system-cron|ENCRYPT|file=...sql.gz.gpg recipient=backup@company.com|prod-server-01
2026-03-20T02:03:50Z|system-cron|S3_UPLOAD|file=...sql.gz.gpg s3_path=s3://vcc-backups-prod/daily/...|prod-server-01
2026-03-20T02:04:00Z|system-cron|CLEANUP|directory=/opt/backups/.../daily max_age=7d count=1|prod-server-01
```

### 6.2 Backup Metadata

Each backup includes a `backup_metadata.json` file:

```json
{
    "timestamp": "2026-03-20T02:00:01Z",
    "hostname": "prod-server-01",
    "backup_type": "daily",
    "database": "validation_system",
    "backup_size": "245M",
    "operator": "system-cron",
    "script_version": "2.12.0",
    "encrypted": true,
    "gmp_compliant": true
}
```

### 6.3 Version Control

All backup scripts are version-controlled in the `backup-infrastructure/` directory
of the application repository. Changes require code review via pull request.

### 6.4 Quarterly DR Testing

| Quarter | Test Date   | Test Type        | Report Location                    |
|---------|-------------|------------------|------------------------------------|
| Q1 2026 | 2026-03-29  | Full DR test     | dr_test_report_2026-03-29_*.txt    |
| Q2 2026 | 2026-06-28  | Full DR test     | (scheduled)                        |
| Q3 2026 | 2026-09-27  | Full DR test     | (scheduled)                        |
| Q4 2026 | 2026-12-27  | Full DR test     | (scheduled)                        |

**DR Test Procedure:**

1. Run `test-backup-restore.sh` which:
   - Takes a fresh mysqldump of production
   - Restores to a temporary database
   - Compares row counts across all tables
   - Validates audit table integrity
   - Checksums critical tables
   - Generates a signed test report

2. Review report with QA team
3. Archive report for regulatory inspection

**Automated weekly DR tests** also run via GitHub Actions every Sunday at 05:00 UTC.

---

## 7. Monitoring & Alerting

### 7.1 Healthcheck Script

Runs every 4 hours via cron. Checks:

- Latest backup freshness (must be < 26 hours old)
- Backup file size (must be > 10MB)
- Checksum integrity
- Metadata completeness
- Disk space (must have > 5GB free)
- Audit trail presence

### 7.2 Alert Channels

| Event                  | Severity | Email | Slack |
|------------------------|----------|-------|-------|
| Backup complete        | INFO     | No    | Yes   |
| Backup failed          | ERROR    | Yes   | Yes   |
| Healthcheck warnings   | WARN     | No    | Yes   |
| Healthcheck failure    | ERROR    | Yes   | Yes   |
| DR test passed         | INFO     | Yes   | Yes   |
| DR test failed         | ERROR    | Yes   | Yes   |
| Low disk space         | ERROR    | Yes   | Yes   |

### 7.3 Status Dashboard

Available at `http://server:9090/backup-status` (via Docker backup-monitor service).

Shows: overall status, backup counts, latest backup info, disk usage, recent audit entries.

---

## 8. Recovery Objectives

| Metric | Target          | Notes                                         |
|--------|-----------------|-----------------------------------------------|
| RPO    | < 6 hours       | Binary log backups every 6 hours              |
| RTO    | < 60 minutes    | Full restore from local backup                |
| RTO    | < 120 minutes   | Full restore from S3 (download + restore)     |
| MTPD   | 4 hours         | Maximum Tolerable Period of Disruption         |

---

## 9. File Listing

```
backup-infrastructure/
  scripts/
    backup-env.sh              # Central configuration (paths, credentials, retention)
    backup-functions.sh        # Shared functions (logging, checksums, encryption, S3)
    mysql-full-backup.sh       # mysqldump full database backup
    mysql-incremental-backup.sh # Percona XtraBackup incremental + binlog backup
    filesystem-backup.sh       # tar+gzip archive of uploads, configs, volumes
    combined-backup.sh         # Orchestrator: runs all backups in sequence
    backup-healthcheck.sh      # Verification: freshness, size, checksums, disk
  docker/
    docker-compose.backup.yml  # Docker overlay with backup sidecar + monitor
    .my.cnf                    # MySQL client credentials template
    github-actions-backup.yml  # CI/CD workflow (copy to .github/workflows/)
  restore/
    restore-full.sh            # Full system restore (DB + files + PITR)
    restore-table.sh           # Single table restore (with temp table option)
  monitoring/
    generate-status-page.sh    # HTML status page generator for backup-monitor
  tests/
    test-backup-restore.sh     # Automated DR test (quarterly GMP requirement)
    test-s3-lifecycle.sh       # S3 storage verification
  docs/
    BACKUP-PLAN.md             # This document
```

---

## 10. Quick Start

```bash
# 1. Set environment variables
export DB_PASSWORD="your_secure_password"
export BACKUP_S3_BUCKET="s3://your-bucket"
export BACKUP_GPG_RECIPIENT="backup@yourcompany.com"
export BACKUP_ALERT_EMAIL="devops@yourcompany.com"

# 2. Create backup directories
sudo mkdir -p /opt/backups/validation-system/{daily,weekly,monthly,binlog,tmp}
sudo mkdir -p /var/log/validation-system/backups
sudo chown -R $(whoami) /opt/backups/validation-system /var/log/validation-system/backups

# 3. Make scripts executable
chmod +x backup-infrastructure/scripts/*.sh
chmod +x backup-infrastructure/restore/*.sh
chmod +x backup-infrastructure/tests/*.sh
chmod +x backup-infrastructure/monitoring/*.sh

# 4. Test manually
./backup-infrastructure/scripts/combined-backup.sh daily

# 5. Install cron (or use Docker sidecar)
sudo cp backup-infrastructure/scripts/crontab /etc/cron.d/vcc-backup

# 6. Run DR test
./backup-infrastructure/tests/test-backup-restore.sh

# 7. (Optional) Start Docker backup services
docker compose -f docker-compose.yml \
               -f backup-infrastructure/docker/docker-compose.backup.yml up -d
```
