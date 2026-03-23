package com.mac.bry.validationsystem.security;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
@Getter
@Setter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Who
    @Column(name = "user_id")
    private Long userId;

    @Column(length = 50)
    private String username;

    // What
    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(nullable = false, length = 50)
    private String action;

    // Legacy fields - deprecated
    @Column(name = "old_value", columnDefinition = "text")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;

    // ENTERPRISE FIX #4: JSON columns
    @Column(name = "old_value_json", columnDefinition = "json")
    @Convert(converter = JsonNodeConverter.class)
    private JsonNode oldValueJson;

    @Column(name = "new_value_json", columnDefinition = "json")
    @Convert(converter = JsonNodeConverter.class)
    private JsonNode newValueJson;

    // When & Where
    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    // Context
    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "request_url", length = 500)
    private String requestUrl;

    // GMP AUDIT INTEGRITY - Hash Chain for tamper detection
    @Column(name = "previous_hash", length = 64)
    private String previousHash;

    @Column(name = "record_hash", length = 64, unique = true)
    private String recordHash;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }

    /**
     * Compute hash for this record (excluding id, previousHash, recordHash)
     * Used for audit trail integrity verification
     */
    public String computeContentHash() {
        StringBuilder content = new StringBuilder();
        content.append("userId:").append(userId)
               .append("|username:").append(username)
               .append("|entityType:").append(entityType)
               .append("|entityId:").append(entityId)
               .append("|action:").append(action)
               .append("|timestamp:").append(timestamp)
               .append("|ipAddress:").append(ipAddress)
               .append("|sessionId:").append(sessionId);

        if (oldValueJson != null) {
            content.append("|oldValue:").append(oldValueJson.toString());
        }
        if (newValueJson != null) {
            content.append("|newValue:").append(newValueJson.toString());
        }

        return content.toString();
    }
}
