package com.mac.bry.validationsystem.certificates;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyCertificateRepository extends JpaRepository<CompanyCertificate, Long> {

    Optional<CompanyCertificate> findByCompanyIdAndActiveTrue(Long companyId);

    List<CompanyCertificate> findByCompanyIdOrderByUploadedAtDesc(Long companyId);
}
