package com.mac.bry.validationsystem.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "csrf_violation_audit")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsrfViolationAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "violation_type", nullable = false, length = 100)
    private String violationType;

    @Column(name = "http_method", length = 10)
    private String httpMethod;

    @Column(name = "request_url", length = 1024)
    private String requestUrl;

    @Column(name = "session_id", length = 100)
    private String sessionId;

    @Column(name = "remote_address", length = 100)
    private String remoteAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "severity", length = 20)
    private String severity;

    @Column(name = "compliance_required")
    private boolean complianceRequired;
}
