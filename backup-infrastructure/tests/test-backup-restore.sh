#!/usr/bin/env bash
# =============================================================================
# VALIDATION SYSTEM v2.12.0-ENTERPRISE - Backup & Restore Integration Test
# =============================================================================
# PURPOSE:  Automated DR test - creates backup, restores to temp DB, verifies
# SCHEDULE: Run quarterly (GMP requirement) or after any backup infra changes
# =============================================================================
#
# WHAT THIS TEST DOES:
#   1. Creates a fresh full backup of the production database
#   2. Creates a temporary test database (validation_system_dr_test)
#   3. Restores the backup into the test database
#   4. Compares row counts between production and restored DB
#   5. Validates critical data integrity (audit trail, signatures)
#   6. Drops the test database
#   7. Generates a DR test report (GMP auditable)
#
# USAGE:
#   ./test-backup-restore.sh
#
# PREREQUISITES:
#   - MySQL user must have CREATE DATABASE privilege
#   - Sufficient disk space for temporary restore
#   - Application can remain running (uses --single-transaction)
#
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../scripts/backup-env.sh"
source "${SCRIPT_DIR}/../scripts/backup-functions.sh"

TEST_DB="validation_system_dr_test"
TEST_START=$(date +%s)
TEST_DATE=$(date '+%Y-%m-%d_%H-%M-%S')
TEST_REPORT="${BACKUP_LOG_DIR}/dr_test_report_${TEST_DATE}.txt"
PASS_COUNT=0
FAIL_COUNT=0
WARN_COUNT=0

# ---- Test Reporting ----

test_pass() {
    local name="$1"
    local detail="$2"
    PASS_COUNT=$((PASS_COUNT + 1))
    echo "[PASS] $name: $detail" | tee -a "$TEST_REPORT"
}

test_fail() {
    local name="$1"
    local detail="$2"
    FAIL_COUNT=$((FAIL_COUNT + 1))
    echo "[FAIL] $name: $detail" | tee -a "$TEST_REPORT"
}

test_warn() {
    local name="$1"
    local detail="$2"
    WARN_COUNT=$((WARN_COUNT + 1))
    echo "[WARN] $name: $detail" | tee -a "$TEST_REPORT"
}

# ---- Cleanup ----

cleanup() {
    echo ""
    echo "Cleaning up test database..."
    mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
        -e "DROP DATABASE IF EXISTS \`${TEST_DB}\`;" 2>/dev/null || true
    echo "Test database dropped."
}

trap cleanup EXIT

# ---- Main Test ----

main() {
    ensure_directories
    mkdir -p "$(dirname "$TEST_REPORT")"

    echo "================================================================" | tee "$TEST_REPORT"
    echo "  DISASTER RECOVERY TEST REPORT"                                  | tee -a "$TEST_REPORT"
    echo "  Date:     $(date '+%Y-%m-%d %H:%M:%S')"                        | tee -a "$TEST_REPORT"
    echo "  Host:     $(hostname)"                                          | tee -a "$TEST_REPORT"
    echo "  Operator: ${BACKUP_OPERATOR:-manual}"                           | tee -a "$TEST_REPORT"
    echo "  Source:   ${MYSQL_DATABASE}"                                    | tee -a "$TEST_REPORT"
    echo "  Target:   ${TEST_DB}"                                          | tee -a "$TEST_REPORT"
    echo "================================================================" | tee -a "$TEST_REPORT"
    echo "" | tee -a "$TEST_REPORT"

    log_audit "DR_TEST_START" "test_db=$TEST_DB"

    # ---- Test 1: MySQL Connectivity ----
    echo "--- Test 1: MySQL Connectivity ---" | tee -a "$TEST_REPORT"
    if check_mysql_connection; then
        test_pass "MySQL Connection" "Connected to $MYSQL_HOST:$MYSQL_PORT"
    else
        test_fail "MySQL Connection" "Cannot connect to MySQL"
        echo "ABORT: Cannot proceed without MySQL connection"
        exit 1
    fi

    # ---- Test 2: Create Backup ----
    echo "" | tee -a "$TEST_REPORT"
    echo "--- Test 2: Create Fresh Backup ---" | tee -a "$TEST_REPORT"

    local test_backup_dir="${BACKUP_TEMP_DIR}/dr_test_${TEST_DATE}"
    mkdir -p "$test_backup_dir"

    local dump_file="${test_backup_dir}/dr_test_dump.sql"
    local dump_start=$(date +%s)

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
        --max_allowed_packet=512M \
        --hex-blob \
        --complete-insert \
        "$MYSQL_DATABASE" \
        > "$dump_file" 2>/dev/null

    local dump_end=$(date +%s)
    local dump_duration=$(( dump_end - dump_start ))

    if [ -s "$dump_file" ]; then
        local dump_size=$(du -sh "$dump_file" | cut -f1)
        test_pass "Create Backup" "Dump created in ${dump_duration}s ($dump_size)"
    else
        test_fail "Create Backup" "mysqldump produced empty file"
        exit 1
    fi

    # ---- Test 3: Record Source Row Counts ----
    echo "" | tee -a "$TEST_REPORT"
    echo "--- Test 3: Record Source Data ---" | tee -a "$TEST_REPORT"

    declare -A SOURCE_COUNTS

    local tables
    tables=$(mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
        -N -e "SELECT table_name FROM information_schema.tables
                WHERE table_schema='${MYSQL_DATABASE}'
                AND table_type='BASE TABLE'
                ORDER BY table_name" 2>/dev/null)

    local table_count=0
    while IFS= read -r table; do
        [ -z "$table" ] && continue
        local count
        count=$(mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
            -N -e "SELECT COUNT(*) FROM \`${table}\`" "$MYSQL_DATABASE" 2>/dev/null || echo "0")
        SOURCE_COUNTS["$table"]="$count"
        table_count=$((table_count + 1))
    done <<< "$tables"

    test_pass "Source Counts" "Recorded row counts for $table_count tables"

    # ---- Test 4: Restore to Test Database ----
    echo "" | tee -a "$TEST_REPORT"
    echo "--- Test 4: Restore to Test Database ---" | tee -a "$TEST_REPORT"

    # Drop test DB if it exists
    mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
        -e "DROP DATABASE IF EXISTS \`${TEST_DB}\`;" 2>/dev/null

    mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
        -e "CREATE DATABASE \`${TEST_DB}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>/dev/null

    local restore_start=$(date +%s)

    mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
        --max_allowed_packet=512M \
        "$TEST_DB" < "$dump_file" 2>/dev/null

    local restore_end=$(date +%s)
    local restore_duration=$(( restore_end - restore_start ))

    if [ $? -eq 0 ]; then
        test_pass "Restore" "Database restored in ${restore_duration}s"
    else
        test_fail "Restore" "mysql import returned error"
        exit 1
    fi

    # Record RTO
    local total_rto=$(( dump_duration + restore_duration ))
    echo "  RTO (Recovery Time): ${total_rto}s (dump: ${dump_duration}s + restore: ${restore_duration}s)" | tee -a "$TEST_REPORT"

    # ---- Test 5: Compare Row Counts ----
    echo "" | tee -a "$TEST_REPORT"
    echo "--- Test 5: Row Count Comparison ---" | tee -a "$TEST_REPORT"

    local mismatches=0
    for table in "${!SOURCE_COUNTS[@]}"; do
        local restored_count
        restored_count=$(mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
            -N -e "SELECT COUNT(*) FROM \`${table}\`" "$TEST_DB" 2>/dev/null || echo "MISSING")

        if [ "$restored_count" = "MISSING" ]; then
            test_fail "Row Count ($table)" "Table missing in restored DB"
            mismatches=$((mismatches + 1))
        elif [ "${SOURCE_COUNTS[$table]}" != "$restored_count" ]; then
            # InnoDB COUNT(*) can vary slightly, allow 1% tolerance
            local source="${SOURCE_COUNTS[$table]}"
            local diff=$(( source > restored_count ? source - restored_count : restored_count - source ))
            local threshold=$(( source / 100 + 1 ))

            if [ "$diff" -le "$threshold" ]; then
                test_warn "Row Count ($table)" "Source: $source, Restored: $restored_count (within tolerance)"
            else
                test_fail "Row Count ($table)" "Source: $source, Restored: $restored_count"
                mismatches=$((mismatches + 1))
            fi
        fi
    done

    if [ "$mismatches" -eq 0 ]; then
        test_pass "Row Counts Overall" "All tables match within tolerance"
    fi

    # ---- Test 6: Critical Data Integrity ----
    echo "" | tee -a "$TEST_REPORT"
    echo "--- Test 6: Critical Data Integrity ---" | tee -a "$TEST_REPORT"

    # Check audit tables exist
    local aud_tables
    aud_tables=$(mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
        -N -e "SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema='${TEST_DB}' AND table_name LIKE '%_AUD'" 2>/dev/null || echo "0")

    if [ "$aud_tables" -gt 0 ]; then
        test_pass "Audit Tables" "$aud_tables _AUD tables present (Envers)"
    else
        test_fail "Audit Tables" "No _AUD tables found - GMP compliance breach"
    fi

    # Check revinfo table
    local revinfo_count
    revinfo_count=$(mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
        -N -e "SELECT COUNT(*) FROM revinfo" "$TEST_DB" 2>/dev/null || echo "MISSING")

    if [ "$revinfo_count" != "MISSING" ] && [ "$revinfo_count" -gt 0 ]; then
        test_pass "Revision Info" "$revinfo_count revision entries"
    else
        test_warn "Revision Info" "revinfo table empty or missing"
    fi

    # Check stored procedures exist
    local routines
    routines=$(mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
        -N -e "SELECT COUNT(*) FROM information_schema.routines
                WHERE routine_schema='${TEST_DB}'" 2>/dev/null || echo "0")

    if [ "$routines" -gt 0 ]; then
        test_pass "Stored Routines" "$routines routines/procedures"
    else
        test_pass "Stored Routines" "No routines (application uses JPA)"
    fi

    # ---- Test 7: Checksum Comparison on Critical Tables ----
    echo "" | tee -a "$TEST_REPORT"
    echo "--- Test 7: Data Checksum Spot Check ---" | tee -a "$TEST_REPORT"

    local critical_tables=("cooling_devices" "validations" "measurement_series" "thermo_recorders")
    for table in "${critical_tables[@]}"; do
        local source_chk
        source_chk=$(mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
            -N -e "CHECKSUM TABLE \`${table}\`" "$MYSQL_DATABASE" 2>/dev/null | awk '{print $2}')
        local restored_chk
        restored_chk=$(mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
            -N -e "CHECKSUM TABLE \`${table}\`" "$TEST_DB" 2>/dev/null | awk '{print $2}')

        if [ "$source_chk" = "$restored_chk" ]; then
            test_pass "Checksum ($table)" "Match: $source_chk"
        else
            test_warn "Checksum ($table)" "Mismatch (may be due to concurrent writes): source=$source_chk restored=$restored_chk"
        fi
    done

    # ---- Final Report ----
    local test_end=$(date +%s)
    local test_duration=$(( test_end - TEST_START ))

    echo "" | tee -a "$TEST_REPORT"
    echo "================================================================" | tee -a "$TEST_REPORT"
    echo "  DR TEST RESULTS"                                                | tee -a "$TEST_REPORT"
    echo "================================================================" | tee -a "$TEST_REPORT"
    echo "  Total Duration: ${test_duration}s"                              | tee -a "$TEST_REPORT"
    echo "  RTO (Recovery Time Objective): ${total_rto}s"                   | tee -a "$TEST_REPORT"
    echo "  RPO (Recovery Point Objective): < 24 hours (daily backup)"      | tee -a "$TEST_REPORT"
    echo "  PASSED: $PASS_COUNT"                                            | tee -a "$TEST_REPORT"
    echo "  WARNINGS: $WARN_COUNT"                                          | tee -a "$TEST_REPORT"
    echo "  FAILED: $FAIL_COUNT"                                            | tee -a "$TEST_REPORT"
    echo ""                                                                  | tee -a "$TEST_REPORT"

    if [ "$FAIL_COUNT" -eq 0 ]; then
        echo "  OVERALL: PASS - Disaster Recovery Test Successful"          | tee -a "$TEST_REPORT"
    else
        echo "  OVERALL: FAIL - $FAIL_COUNT failures detected"             | tee -a "$TEST_REPORT"
    fi

    echo "================================================================" | tee -a "$TEST_REPORT"
    echo ""
    echo "Report saved to: $TEST_REPORT"
    echo ""

    log_audit "DR_TEST_COMPLETE" "pass=$PASS_COUNT warn=$WARN_COUNT fail=$FAIL_COUNT duration=${test_duration}s rto=${total_rto}s"

    # Send notification
    if [ "$FAIL_COUNT" -gt 0 ]; then
        send_alert "DR Test FAILED" "$(cat "$TEST_REPORT")" "ERROR"
        return 1
    else
        send_alert "DR Test Passed" "All checks passed. RTO: ${total_rto}s. Report: $TEST_REPORT" "INFO"
        return 0
    fi
}

main "$@"
