CREATE TABLE csrf_violation_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    violation_type VARCHAR(100) NOT NULL,
    http_method VARCHAR(10),
    request_url VARCHAR(1024),
    session_id VARCHAR(100),
    remote_address VARCHAR(100),
    user_agent VARCHAR(500),
    details TEXT,
    timestamp DATETIME NOT NULL,
    severity VARCHAR(20),
    compliance_required BOOLEAN DEFAULT TRUE
);

-- Index for querying audit logs by timestamp
CREATE INDEX idx_csrf_audit_timestamp ON csrf_violation_audit(timestamp);

-- Index for tracking repeated violations from specific IPs or sessions
CREATE INDEX idx_csrf_audit_remote_address ON csrf_violation_audit(remote_address);
CREATE INDEX idx_csrf_audit_session_id ON csrf_violation_audit(session_id);
