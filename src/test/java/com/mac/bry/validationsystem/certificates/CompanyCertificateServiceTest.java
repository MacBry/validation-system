package com.mac.bry.validationsystem.certificates;

import com.mac.bry.validationsystem.company.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Optional;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyCertificateServiceTest {

    @Mock
    private CompanyCertificateRepository repository;

    @Mock
    private CompanyRepository companyRepository;

    @InjectMocks
    @Spy
    private CompanyCertificateServiceImpl service;

    private final String password = "password";

    @Test
    @DisplayName("Should successfully upload and extract certificate metadata using mocked KeyStore")
    void shouldUploadSuccessfully() throws Exception {
        // Given
        Long companyId = 1L;
        byte[] bytes = new byte[]{1, 2, 3};
        
        KeyStore mockKs = mock(KeyStore.class);
        X509Certificate mockCert = mock(X509Certificate.class);
        
        when(mockKs.aliases()).thenReturn(Collections.enumeration(Collections.singletonList("alias")));
        when(mockKs.getCertificate("alias")).thenReturn(mockCert);
        when(mockCert.getSubjectX500Principal()).thenReturn(new X500Principal("CN=Test"));
        when(mockCert.getIssuerX500Principal()).thenReturn(new X500Principal("CN=Issuer"));
        when(mockCert.getSerialNumber()).thenReturn(new BigInteger("1"));
        when(mockCert.getEncoded()).thenReturn(new byte[]{0});
        
        doReturn(mockKs).when(service).loadKeyStore(bytes, password);
        when(repository.findByCompanyIdAndActiveTrue(companyId)).thenReturn(Optional.empty());
        when(repository.save(any(CompanyCertificate.class))).thenAnswer(i -> i.getArgument(0));

        // When
        CompanyCertificate result = service.upload(companyId, bytes, password, 1L);

        // Then
        assertNotNull(result);
        assertEquals("alias", result.getAlias());
        assertEquals("CN=Test", result.getSubject());
        verify(repository).save(any(CompanyCertificate.class));
    }

    @Test
    @DisplayName("Should handle KeyStore loading failure")
    void shouldHandleLoadingFailure() throws Exception {
        // Given
        byte[] bytes = new byte[]{0};
        doThrow(new IllegalArgumentException("Invalid")).when(service).loadKeyStore(bytes, password);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> service.upload(1L, bytes, password, 1L));
    }

    @Test
    @DisplayName("Should find active certificate")
    void shouldFindActive() {
        // Given
        CompanyCertificate cert = new CompanyCertificate();
        when(repository.findByCompanyIdAndActiveTrue(1L)).thenReturn(Optional.of(cert));

        // When
        Optional<CompanyCertificate> result = service.findActive(1L);

        // Then
        assertTrue(result.isPresent());
    }

    @Test
    @DisplayName("Should deactivate certificate manually")
    void shouldDeactivateManually() {
        // Given
        CompanyCertificate cert = new CompanyCertificate();
        cert.setActive(true);
        when(repository.findById(1L)).thenReturn(Optional.of(cert));

        // When
        service.deactivate(1L);

        // Then
        assertFalse(cert.isActive());
        verify(repository).save(cert);
    }
}
