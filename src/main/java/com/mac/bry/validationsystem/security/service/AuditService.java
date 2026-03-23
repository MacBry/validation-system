package com.mac.bry.validationsystem.security.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mac.bry.validationsystem.security.AuditLog;
import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper; // Z autoconfigure Springa

    @Autowired
    @Lazy
    private AuditService self;

    /**
     * Zbieranie kontekstu i opakowanie żądania.
     * Metoda synchroniczna, wyciągająca kontekst przed wrzuceniem do nowego wątku.
     */
    public void logOperation(String entityType, Long entityId, String action,
            Object oldValueObj, Object newValueObj) {

        Long userId = null;
        String username = null;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User) {
            User user = (User) auth.getPrincipal();
            userId = user.getId();
            username = user.getUsername();
        }

        String oldJson = null;
        String newJson = null;

        try {
            oldJson = oldValueObj != null ? objectMapper.writeValueAsString(oldValueObj) : null;
        } catch (Exception e) {
            log.warn("Failed to serialize old value to JSON, falling back to toString", e);
            oldJson = oldValueObj != null ? "{\"fallback\": \"" + oldValueObj.toString().replace("\"", "\\\"") + "\"}"
                    : null;
        }

        try {
            newJson = newValueObj != null ? objectMapper.writeValueAsString(newValueObj) : null;
        } catch (Exception e) {
            log.warn("Failed to serialize new value to JSON, falling back to toString", e);
            newJson = newValueObj != null ? "{\"fallback\": \"" + newValueObj.toString().replace("\"", "\\\"") + "\"}"
                    : null;
        }

        // Dynamically extract IP and Session ID from web context if available
        String ipAddress;
        String sessionId;

        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();

            // Extract IP, considering proxies
            String xf = request.getHeader("X-Forwarded-For");
            ipAddress = (xf != null && !xf.isEmpty()) ? xf.split(",")[0].trim() : request.getRemoteAddr();

            // Extract Session ID
            sessionId = (request.getSession(false) != null) ? request.getSession().getId() : "no-session";
        } else {
            // Fallback for non-web contexts (e.g., startup tasks, background jobs)
            ipAddress = "system-internal";
            sessionId = "internal";
        }

        self.logOperationAsync(userId, username, entityType, entityId, action, oldJson, newJson, ipAddress,
                sessionId);
    }

    /**
     * GMP AUDIT INTEGRITY: Synchronous logging with hash chain for critical operations
     */
    @Transactional
    public void logOperationSync(Long userId, String username, String entityType,
            Long entityId, String action,
            String oldValueJson, String newValueJson,
            String ipAddress, String sessionId) {

        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setUserId(userId);
            auditLog.setUsername(username);
            auditLog.setEntityType(entityType);
            auditLog.setEntityId(entityId);
            auditLog.setAction(action);

            if (oldValueJson != null) {
                auditLog.setOldValueJson(objectMapper.readTree(oldValueJson));
            }
            if (newValueJson != null) {
                auditLog.setNewValueJson(objectMapper.readTree(newValueJson));
            }

            auditLog.setIpAddress(ipAddress);
            auditLog.setSessionId(sessionId);

            // GMP COMPLIANCE: Add hash chain for audit trail integrity
            String previousHash = getLastRecordHash();
            auditLog.setPreviousHash(previousHash);

            // Calculate content hash
            String contentHash = calculateSHA256(auditLog.computeContentHash());

            // Calculate record hash (includes previous hash)
            String recordHash = calculateSHA256(contentHash + "|previousHash:" + (previousHash != null ? previousHash : "GENESIS"));
            auditLog.setRecordHash(recordHash);

            auditLogRepository.save(auditLog);
            log.debug("Audit log saved with hash chain for {} ID: {}, hash: {}",
                      entityType, entityId, recordHash.substring(0, 8) + "...");

        } catch (Exception e) {
            log.error("Failed to save audit log with hash chain", e);
            throw new RuntimeException("Audit trail integrity failure", e);
        }
    }

    /**
     * FIX #3: Wykonanie w puli wątków dla uwolnienia wątku żądania HTTP
     * Używane dla non-critical operations
     */
    @Async("auditTaskExecutor")
    protected void logOperationAsync(Long userId, String username, String entityType,
            Long entityId, String action,
            String oldValueJson, String newValueJson,
            String ipAddress, String sessionId) {

        // For critical operations (password, signature, user changes), use sync logging
        if (isCriticalOperation(entityType, action)) {
            logOperationSync(userId, username, entityType, entityId, action,
                           oldValueJson, newValueJson, ipAddress, sessionId);
            return;
        }

        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setUserId(userId);
            auditLog.setUsername(username);
            auditLog.setEntityType(entityType);
            auditLog.setEntityId(entityId);
            auditLog.setAction(action);

            if (oldValueJson != null) {
                auditLog.setOldValueJson(objectMapper.readTree(oldValueJson));
            }
            if (newValueJson != null) {
                auditLog.setNewValueJson(objectMapper.readTree(newValueJson));
            }

            auditLog.setIpAddress(ipAddress);
            auditLog.setSessionId(sessionId);

            // Async logging without hash chain for non-critical operations
            auditLogRepository.save(auditLog);
            log.debug("Async audit log saved for {} ID: {}", entityType, entityId);

        } catch (Exception e) {
            log.error("Failed to save async audit log", e);
        }
    }

    /**
     * Determines if operation requires synchronous logging with hash chain
     */
    private boolean isCriticalOperation(String entityType, String action) {
        return ("User".equals(entityType) && ("CREATE".equals(action) || "DELETE".equals(action) ||
                "SOFT_DELETE".equals(action) || "RESET_PASSWORD".equals(action))) ||
               ("Validation".equals(entityType) && "SIGN".equals(action)) ||
               ("ValidationSignature".equals(entityType)) ||
               ("ValidationDocument".equals(entityType));
    }

    /**
     * Get hash of the last audit record for hash chain
     */
    private String getLastRecordHash() {
        return auditLogRepository.findLastRecordHash().orElse(null);
    }

    /**
     * Calculate SHA-256 hash
     */
    private String calculateSHA256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Pobiera historię zmian dla konkretnego rodzaju encji i jej ID.
     */
    public List<AuditLog> getLogsForEntity(String entityType, Long entityId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(entityType, entityId);
    }

    /**
     * Pobiera wszystkie logi gdzie użytkownik jest aktorem LUB celem zmiany.
     */
    public List<AuditLog> getRelatedLogsForUser(Long userId) {
        return auditLogRepository.findAllRelatedToUser(userId);
    }

    /**
     * Pobiera logi dla użytkownika z paginacją (najnowsze pierwsze, 20 na stronę).
     */
    public Page<AuditLog> getRelatedLogsForUserWithPagination(Long userId, Pageable pageable) {
        return auditLogRepository.findAllRelatedToUserWithPagination(userId, pageable);
    }

    /**
     * GMP COMPLIANCE: Verify audit trail integrity using hash chain
     * Critical for auditor validation of tamper-evident logging
     */
    public boolean verifyAuditTrailIntegrity() {
        try {
            List<AuditLog> logs = auditLogRepository.findAll().stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .toList();

            if (logs.isEmpty()) {
                return true; // Empty trail is valid
            }

            String expectedPreviousHash = null;
            for (AuditLog auditLog : logs) {
                // Skip logs without hash (legacy records)
                if (auditLog.getRecordHash() == null) {
                    continue;
                }

                // Verify previous hash matches
                if (!java.util.Objects.equals(expectedPreviousHash, auditLog.getPreviousHash())) {
                    log.error("🚨 AUDIT INTEGRITY VIOLATION: Hash chain broken at record ID {}, expected previous hash: {}, actual: {}",
                              auditLog.getId(), expectedPreviousHash, auditLog.getPreviousHash());
                    return false;
                }

                // Verify record hash
                String contentHash = calculateSHA256(auditLog.computeContentHash());
                String expectedRecordHash = calculateSHA256(contentHash + "|previousHash:" + (auditLog.getPreviousHash() != null ? auditLog.getPreviousHash() : "GENESIS"));

                if (!expectedRecordHash.equals(auditLog.getRecordHash())) {
                    log.error("🚨 AUDIT INTEGRITY VIOLATION: Record hash mismatch at ID {}, expected: {}, actual: {}",
                              auditLog.getId(), expectedRecordHash, auditLog.getRecordHash());
                    return false;
                }

                expectedPreviousHash = auditLog.getRecordHash();
            }

            log.info("✅ AUDIT INTEGRITY VERIFIED: Hash chain valid for {} records", logs.size());
            return true;

        } catch (Exception e) {
            log.error("❌ AUDIT INTEGRITY CHECK FAILED", e);
            return false;
        }
    }
}
