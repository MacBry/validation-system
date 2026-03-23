package com.mac.bry.validationsystem.certificates;

import com.mac.bry.validationsystem.company.Company;
import com.mac.bry.validationsystem.company.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Optional;

import javax.security.auth.x500.X500Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CompanyCertificateIntegrationTest {

    @SpyBean
    private CompanyCertificateService service;

    @Autowired
    private CompanyCertificateRepository repository;

    @Autowired
    private CompanyRepository companyRepository;

    private Company company;
    private final byte[] dummyBytes = new byte[]{1, 2, 3};
    private final String password = "password";

    @BeforeEach
    void setUp() throws Exception {
        company = new Company();
        company.setName("Integration Test Co");
        company = companyRepository.saveAndFlush(company);

        // Prepare Mock KeyStore to be returned by Spy
        KeyStore mockKs = mock(KeyStore.class);
        X509Certificate mockCert = mock(X509Certificate.class);
        
        lenient().when(mockKs.aliases()).thenAnswer(invocation -> 
            Collections.enumeration(Collections.singletonList("test-alias")));
        lenient().when(mockKs.getCertificate("test-alias")).thenReturn(mockCert);
        lenient().when(mockCert.getSubjectX500Principal()).thenReturn(new X500Principal("CN=Integration Test"));
        lenient().when(mockCert.getIssuerX500Principal()).thenReturn(new X500Principal("CN=Integration Test"));
        lenient().when(mockCert.getSerialNumber()).thenReturn(new BigInteger("123"));
        lenient().when(mockCert.getEncoded()).thenReturn(new byte[]{0});
        
        // Mock the protected method on the SpyBean
        // Since loadKeyStore is protected, we need to handle it carefully or use the real implementation if we can.
        // Actually, since it's a SpyBean, we can do:
        doReturn(mockKs).when(((CompanyCertificateServiceImpl)service)).loadKeyStore(any(), eq(password));
    }

    @Test
    @DisplayName("Should perform full certificate lifecycle: upload -> find -> deactivate")
    void shouldPerformFullLifecycle() throws Exception {
        // 1. Upload
        CompanyCertificate uploaded = service.upload(company.getId(), dummyBytes, password, 1L);
        assertNotNull(uploaded.getId());
        assertTrue(uploaded.isActive());
        assertEquals("test-alias", uploaded.getAlias());

        // 2. Find Active
        Optional<CompanyCertificate> active = service.findActive(company.getId());
        assertTrue(active.isPresent());
        assertEquals(uploaded.getId(), active.get().getId());

        // 3. Upload Second Certificate (should deactivate first)
        CompanyCertificate uploaded2 = service.upload(company.getId(), dummyBytes, password, 1L);
        assertNotNull(uploaded2.getId());
        
        Optional<CompanyCertificate> firstCert = repository.findById(uploaded.getId());
        assertTrue(firstCert.isPresent());
        assertFalse(firstCert.get().isActive());

        Optional<CompanyCertificate> secondCert = service.findActive(company.getId());
        assertTrue(secondCert.isPresent());
        assertEquals(uploaded2.getId(), secondCert.get().getId());

        // 4. Deactivate manually
        service.deactivate(uploaded2.getId());
        Optional<CompanyCertificate> finalCheck = service.findActive(company.getId());
        assertFalse(finalCheck.isPresent());
    }
}
