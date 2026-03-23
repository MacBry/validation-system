package com.mac.bry.validationsystem.security.repository;

import com.mac.bry.validationsystem.security.CsrfViolationAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CsrfViolationAuditRepository extends JpaRepository<CsrfViolationAudit, Long> {
}
