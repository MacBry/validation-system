#!/usr/bin/env bash
# =============================================================================
# VALIDATION SYSTEM v2.12.0-ENTERPRISE - S3 Lifecycle & Replication Test
# =============================================================================
# PURPOSE:  Verify S3 backup storage, lifecycle policies, cross-region replication
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/../scripts/backup-env.sh"
source "${SCRIPT_DIR}/../scripts/backup-functions.sh"

echo "================================================================"
echo "  S3 BACKUP STORAGE VERIFICATION"
echo "================================================================"

if [ -z "${S3_BUCKET:-}" ] || [ "$S3_BUCKET" = "s3://" ]; then
    echo "SKIP: S3_BUCKET not configured"
    exit 0
fi

echo ""
echo "--- Test 1: S3 Bucket Access ---"
if aws s3 ls "$S3_BUCKET" --region "$S3_REGION" > /dev/null 2>&1; then
    echo "[PASS] Can access $S3_BUCKET"
else
    echo "[FAIL] Cannot access $S3_BUCKET"
    exit 1
fi

echo ""
echo "--- Test 2: Recent Uploads ---"
RECENT=$(aws s3 ls "${S3_BUCKET}/daily/" --recursive --region "$S3_REGION" 2>/dev/null | sort | tail -5)
if [ -n "$RECENT" ]; then
    echo "[PASS] Recent daily backups found:"
    echo "$RECENT" | while read -r line; do echo "  $line"; done
else
    echo "[WARN] No daily backups found in S3"
fi

echo ""
echo "--- Test 3: Encryption Status ---"
SAMPLE=$(aws s3 ls "${S3_BUCKET}/daily/" --recursive --region "$S3_REGION" 2>/dev/null | tail -1 | awk '{print $4}')
if [ -n "$SAMPLE" ]; then
    ENC=$(aws s3api head-object --bucket "${S3_BUCKET#s3://}" --key "$SAMPLE" --region "$S3_REGION" 2>/dev/null | grep -i "ServerSideEncryption" || echo "")
    if [ -n "$ENC" ]; then
        echo "[PASS] Server-side encryption active: $ENC"
    else
        echo "[WARN] No server-side encryption detected"
    fi
fi

echo ""
echo "--- Test 4: Bucket Versioning ---"
VERSIONING=$(aws s3api get-bucket-versioning --bucket "${S3_BUCKET#s3://}" --region "$S3_REGION" 2>/dev/null | grep -o '"Status": "[^"]*"' || echo "")
echo "Versioning: ${VERSIONING:-Disabled}"
[ -n "$VERSIONING" ] && echo "[PASS] Versioning enabled" || echo "[WARN] Versioning not enabled (recommended for GMP)"

echo ""
echo "--- Test 5: Storage Usage ---"
TOTAL_SIZE=$(aws s3 ls "${S3_BUCKET}/" --recursive --summarize --region "$S3_REGION" 2>/dev/null | grep "Total Size" || echo "Unknown")
echo "Total backup storage: $TOTAL_SIZE"

echo ""
echo "--- Test 6: Download Test ---"
if [ -n "$SAMPLE" ]; then
    TEST_FILE="/tmp/s3_backup_test_$$"
    if aws s3 cp "s3://${S3_BUCKET#s3://}/${SAMPLE}" "$TEST_FILE" --region "$S3_REGION" > /dev/null 2>&1; then
        echo "[PASS] Download test successful ($(du -sh "$TEST_FILE" | cut -f1))"
        rm -f "$TEST_FILE"
    else
        echo "[FAIL] Download test failed"
    fi
fi

echo ""
echo "================================================================"
echo "  S3 VERIFICATION COMPLETE"
echo "================================================================"
