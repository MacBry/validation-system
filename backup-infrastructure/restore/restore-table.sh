#!/usr/bin/env bash
# =============================================================================
# VALIDATION SYSTEM v2.12.0-ENTERPRISE - Partial Table Restore
# =============================================================================
# PURPOSE:  Restore specific tables from a full backup dump
# USE CASE: Recover accidentally deleted/corrupted data in one table
#           without affecting the rest of the database
# =============================================================================
#
# USAGE:
#   ./restore-table.sh <backup_dir> <table_name> [--to-temp]
#
# EXAMPLES:
#   ./restore-table.sh /opt/backups/.../vcc_full_2026-03-20 measurement_series
#   ./restore-table.sh /opt/backups/.../vcc_full_2026-03-20 validations --to-temp
#
# The --to-temp flag restores to a temporary table (table_name_restored)
# so you can inspect data before merging it into the live table.
#
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../scripts/backup-env.sh"
source "${SCRIPT_DIR}/../scripts/backup-functions.sh"

BACKUP_DIR="${1:-}"
TABLE_NAME="${2:-}"
TO_TEMP=false

[ "${3:-}" = "--to-temp" ] && TO_TEMP=true

if [ -z "$BACKUP_DIR" ] || [ -z "$TABLE_NAME" ]; then
    echo "Usage: $0 <backup_directory> <table_name> [--to-temp]"
    exit 1
fi

main() {
    log_info "Partial table restore: $TABLE_NAME from $BACKUP_DIR"
    log_audit "TABLE_RESTORE_START" "table=$TABLE_NAME source=$BACKUP_DIR to_temp=$TO_TEMP"

    # Find and decompress dump
    local dump_file
    dump_file=$(find "$BACKUP_DIR" -name "validation_system_full.sql.gz" -type f | head -1)

    if [ -z "$dump_file" ]; then
        dump_file=$(find "$BACKUP_DIR" -name "validation_system_full.sql" -type f | head -1)
    fi

    if [ -z "$dump_file" ]; then
        log_error "No dump file found in $BACKUP_DIR"
        exit 1
    fi

    local work_dir="${BACKUP_TEMP_DIR}/table_restore_$$"
    mkdir -p "$work_dir"
    trap "rm -rf '$work_dir'" EXIT

    # Decompress if needed
    local sql_file
    if [[ "$dump_file" == *.gz ]]; then
        echo "Decompressing dump..."
        gunzip -c "$dump_file" > "${work_dir}/full_dump.sql"
        sql_file="${work_dir}/full_dump.sql"
    else
        sql_file="$dump_file"
    fi

    # Extract table DDL + data using sed
    echo "Extracting table '$TABLE_NAME' from dump..."
    local table_sql="${work_dir}/${TABLE_NAME}.sql"

    # Extract between DROP TABLE and UNLOCK TABLES for the specific table
    sed -n "/^-- Table structure for table \`${TABLE_NAME}\`/,/^UNLOCK TABLES;/p" \
        "$sql_file" > "$table_sql"

    if [ ! -s "$table_sql" ]; then
        # Try alternate format
        sed -n "/DROP TABLE IF EXISTS \`${TABLE_NAME}\`/,/^UNLOCK TABLES;/p" \
            "$sql_file" > "$table_sql"
    fi

    if [ ! -s "$table_sql" ]; then
        log_error "Table '$TABLE_NAME' not found in dump"
        echo "Available tables in dump:"
        grep "^-- Table structure for table" "$sql_file" | sed "s/.*\`\(.*\)\`.*/  \1/"
        exit 1
    fi

    local line_count=$(wc -l < "$table_sql")
    echo "Extracted: ${line_count} lines for table $TABLE_NAME"

    if $TO_TEMP; then
        # Rename table references to _restored suffix
        local temp_table="${TABLE_NAME}_restored"
        echo "Restoring to temporary table: $temp_table"

        sed -i "s/\`${TABLE_NAME}\`/\`${temp_table}\`/g" "$table_sql"

        mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
            "$MYSQL_DATABASE" < "$table_sql"

        echo ""
        echo "Table restored as: $temp_table"
        echo "Inspect with:"
        echo "  SELECT COUNT(*) FROM ${temp_table};"
        echo "  SELECT * FROM ${temp_table} LIMIT 10;"
        echo ""
        echo "To merge back:"
        echo "  INSERT INTO ${TABLE_NAME} SELECT * FROM ${temp_table};"
        echo "  DROP TABLE ${temp_table};"
    else
        echo ""
        echo "WARNING: This will DROP and recreate table '$TABLE_NAME'"
        read -p "Type table name to confirm: " confirmation
        if [ "$confirmation" != "$TABLE_NAME" ]; then
            echo "Cancelled."
            exit 0
        fi

        mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
            "$MYSQL_DATABASE" < "$table_sql"

        echo "Table '$TABLE_NAME' restored successfully"
    fi

    local count
    count=$(mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
        -N -e "SELECT COUNT(*) FROM \`$([ $TO_TEMP = true ] && echo "${TABLE_NAME}_restored" || echo "$TABLE_NAME")\`" \
        "$MYSQL_DATABASE" 2>/dev/null)
    echo "Row count after restore: $count"

    log_audit "TABLE_RESTORE_COMPLETE" "table=$TABLE_NAME rows=$count to_temp=$TO_TEMP"
}

main "$@"
