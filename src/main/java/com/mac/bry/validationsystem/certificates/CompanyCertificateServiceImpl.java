package com.mac.bry.validationsystem.certificates;

import com.mac.bry.validationsystem.company.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyCertificateServiceImpl implements CompanyCertificateService {

    private final CompanyCertificateRepository repository;
    private final CompanyRepository companyRepository;

    @Override
    @Transactional
    public CompanyCertificate upload(Long companyId, byte[] keystoreBytes,
                                      String password, Long uploadedBy) throws Exception {
        KeyStore ks = loadKeyStore(keystoreBytes, password);

        var aliases = ks.aliases();
        if (!aliases.hasMoreElements()) {
            throw new IllegalArgumentException("Keystore nie zawiera żadnych certyfikatów");
        }
        String alias = aliases.nextElement();
        X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
        if (cert == null) {
            throw new IllegalArgumentException("Keystore nie zawiera certyfikatu dla aliasu: " + alias);
        }

        // Dezaktywuj poprzedni aktywny certyfikat tej firmy
        repository.findByCompanyIdAndActiveTrue(companyId).ifPresent(old -> {
            old.setActive(false);
            repository.save(old);
            log.info("Dezaktywowano poprzedni certyfikat ID={} firmy ID={}", old.getId(), companyId);
        });

        CompanyCertificate saved = repository.save(CompanyCertificate.builder()
                .company(companyRepository.getReferenceById(companyId))
                .alias(alias)
                .subject(cert.getSubjectX500Principal().getName())
                .issuer(cert.getIssuerX500Principal().getName())
                .serialNumber(cert.getSerialNumber().toString(16).toUpperCase())
                .validFrom(toLocalDateTime(cert.getNotBefore()))
                .validTo(toLocalDateTime(cert.getNotAfter()))
                .sha256Fingerprint(sha256hex(cert.getEncoded()))
                .keystoreData(keystoreBytes)
                .keystorePassword(password)
                .active(true)
                .uploadedBy(uploadedBy)
                .uploadedAt(LocalDateTime.now())
                .build());

        log.info("Wgrano certyfikat ID={} dla firmy ID={}, subject={}", saved.getId(), companyId, saved.getSubject());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CompanyCertificate> findActive(Long companyId) {
        return repository.findByCompanyIdAndActiveTrue(companyId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompanyCertificate> findAll(Long companyId) {
        return repository.findByCompanyIdOrderByUploadedAtDesc(companyId);
    }

    @Override
    @Transactional
    public void deactivate(Long certId) {
        repository.findById(certId).ifPresent(cert -> {
            cert.setActive(false);
            repository.save(cert);
            log.info("Dezaktywowano certyfikat ID={}", certId);
        });
    }

    protected KeyStore loadKeyStore(byte[] bytes, String password) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try {
            ks.load(new ByteArrayInputStream(bytes), password.toCharArray());
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("password")) {
                throw new IllegalArgumentException("Nieprawidłowe hasło keystore");
            }
            throw new IllegalArgumentException("Plik nie jest poprawnym archiwum PKCS12: " + e.getMessage());
        }
        return ks;
    }

    private LocalDateTime toLocalDateTime(Date date) {
        if (date == null) return null;
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private String sha256hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Błąd obliczania SHA-256 odcisku certyfikatu", e);
            return null;
        }
    }
}
