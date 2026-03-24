#!/bin/bash

# ============================================================================
# Database Fix Script - Hibernate/MySQL Compatibility Issue
# ============================================================================
# Automatycznie naprawia problem: "scale has no meaning for SQL floating point types"
# ============================================================================

set -e  # Exit on error

MYSQL_USER="${DB_USERNAME:-root}"
MYSQL_PASS="${DB_PASSWORD:?ERROR: DB_PASSWORD environment variable must be set}"
MYSQL_HOST="${DB_HOST:-localhost}"
DB_NAME="validation_system"
MYSQL_BIN="/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe"

echo "============================================================"
echo "🔧 Naprawa bazy danych - Hibernate/MySQL Compatibility"
echo "============================================================"
echo ""

# 1. Usunięcie starej bazy
echo "1️⃣  Usuwanie starej bazy danych..."
"$MYSQL_BIN" -u $MYSQL_USER -p$MYSQL_PASS -e "DROP DATABASE IF EXISTS $DB_NAME;" 2>/dev/null || true
echo "   ✓ Stara baza usunięta"
echo ""

# 2. Utworzenie nowej bazy z prawidłowymi kodowaniami
echo "2️⃣  Tworzenie nowej bazy danych..."
"$MYSQL_BIN" -u $MYSQL_USER -p$MYSQL_PASS -e "CREATE DATABASE $DB_NAME CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
echo "   ✓ Nowa baza utworzona"
echo ""

# 3. Stosowanie migracji
echo "3️⃣  Stosowanie migracji SQL..."

# Czekamy na MySQL
sleep 2

# V2.24.0 - Wizard schema
echo "   • Migracja V2.24.0 (Wizard schema)..."
"$MYSQL_BIN" -u $MYSQL_USER -p$MYSQL_PASS $DB_NAME < "src/main/resources/db/migration/V2.24.0__wizard_schema.sql" 2>/dev/null || {
    echo "   ❌ Błąd przy migracji V2.24.0"
    exit 1
}
echo "   ✓ V2.24.0 zastosowana"

# V2.24.1 - Fix Hibernate/MySQL compatibility
echo "   • Migracja V2.24.1 (Hibernate/MySQL fix)..."
"$MYSQL_BIN" -u $MYSQL_USER -p$MYSQL_PASS $DB_NAME < "src/main/resources/db/migration/V2.24.1__fix_hibernate_mysql_precision_scale.sql" 2>/dev/null || {
    echo "   ❌ Błąd przy migracji V2.24.1"
    exit 1
}
echo "   ✓ V2.24.1 zastosowana"
echo ""

# 4. Weryfikacja
echo "4️⃣  Weryfikacja bazy danych..."
TABLE_COUNT=$("$MYSQL_BIN" -u $MYSQL_USER -p$MYSQL_PASS $DB_NAME -e "SELECT COUNT(*) as count FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='$DB_NAME';" 2>/dev/null | tail -1)
echo "   ✓ Liczba tabel: $TABLE_COUNT"
echo ""

echo "============================================================"
echo "✅ BAZA DANYCH NAPRAWIONA!"
echo "============================================================"
echo ""
echo "Co dalej:"
echo "1. Uruchom: mvn clean spring-boot:run"
echo "2. Aplikacja powinna teraz startować bez błędów"
echo "3. Odwiedź: https://localhost:8443"
echo ""
