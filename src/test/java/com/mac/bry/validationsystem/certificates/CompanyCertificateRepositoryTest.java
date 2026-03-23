package com.mac.bry.validationsystem.certificates;

import com.mac.bry.validationsystem.company.Company;
import com.mac.bry.validationsystem.company.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class CompanyCertificateRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CompanyCertificateRepository repository;

    private Company company;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setName("Test Company");
        company.setAddress("Test Address");
        entityManager.persist(company);
        entityManager.flush();
    }

    @Test
    @DisplayName("Should find active certificate for company")
    void shouldFindActiveByCompanyId() {
        // Given
        CompanyCertificate activeCert = CompanyCertificate.builder()
                .company(company)
                .active(true)
                .keystoreData(new byte[]{1})
                .keystorePassword("pass")
                .uploadedAt(LocalDateTime.now())
                .build();
        
        CompanyCertificate inactiveCert = CompanyCertificate.builder()
                .company(company)
                .active(false)
                .keystoreData(new byte[]{2})
                .keystorePassword("pass")
                .uploadedAt(LocalDateTime.now().minusDays(1))
                .build();

        entityManager.persist(activeCert);
        entityManager.persist(inactiveCert);
        entityManager.flush();

        // When
        Optional<CompanyCertificate> result = repository.findByCompanyIdAndActiveTrue(company.getId());

        // Then
        assertTrue(result.isPresent());
        assertEquals(activeCert.getId(), result.get().getId());
        assertTrue(result.get().isActive());
    }

    @Test
    @DisplayName("Should return empty when no active certificate exists")
    void shouldReturnEmptyWhenNoActive() {
        // Given
        CompanyCertificate inactiveCert = CompanyCertificate.builder()
                .company(company)
                .active(false)
                .keystoreData(new byte[]{2})
                .keystorePassword("pass")
                .uploadedAt(LocalDateTime.now())
                .build();
        entityManager.persist(inactiveCert);
        entityManager.flush();

        // When
        Optional<CompanyCertificate> result = repository.findByCompanyIdAndActiveTrue(company.getId());

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Should find all certificates for company ordered by upload date desc")
    void shouldFindAllByCompanyIdOrdered() {
        // Given
        CompanyCertificate cert1 = CompanyCertificate.builder()
                .company(company)
                .active(false)
                .keystoreData(new byte[]{1})
                .keystorePassword("pass")
                .uploadedAt(LocalDateTime.now().minusDays(2))
                .build();
        
        CompanyCertificate cert2 = CompanyCertificate.builder()
                .company(company)
                .active(true)
                .keystoreData(new byte[]{2})
                .keystorePassword("pass")
                .uploadedAt(LocalDateTime.now().minusDays(1))
                .build();

        entityManager.persist(cert1);
        entityManager.persist(cert2);
        entityManager.flush();

        // When
        List<CompanyCertificate> result = repository.findByCompanyIdOrderByUploadedAtDesc(company.getId());

        // Then
        assertEquals(2, result.size());
        assertEquals(cert2.getId(), result.get(0).getId());
        assertEquals(cert1.getId(), result.get(1).getId());
    }
}
