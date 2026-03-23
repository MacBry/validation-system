package com.mac.bry.validationsystem.validation;

import com.mac.bry.validationsystem.certificates.CompanyCertificate;
import com.mac.bry.validationsystem.certificates.CompanyCertificateService;
import com.mac.bry.validationsystem.company.Company;
import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Serwis elektronicznego podpisywania walidacji (Annex 11 §12).
 *
 * Przepływ:
 *  1. Walidacja guardu (nie podpisano wcześniej, status != COMPLETED)
 *  2. Weryfikacja tożsamości — passwordEncoder.matches(hasło, hash BCrypt)
 *  3. Generacja Schematu Wizualnego PDF z numerem dokumentu (Faza 2 flow)
 *  4. Podpisanie kryptograficzne PDF certyfikatem org (PdfSigningService)
 *     → per-firma jeśli istnieje aktywny CompanyCertificate, fallback globalny
 *  5. Zapis podpisanego PDF na dysk (uploads/signed/)
 *  6. Zapis ValidationSignature do DB
 *  7. Zmiana statusu → COMPLETED (nieodwracalna blokada)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidationSigningService {

    private final ValidationRepository validationRepository;
    private final ValidationSignatureRepository signatureRepository;
    private final SchematWizualnyPdfService schematService;
    private final ValidationDocumentTrackingService trackingService;
    private final PdfSigningService pdfSigningService;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final CompanyCertificateService companyCertificateService;

    @Value("${app.signed.documents.path:uploads/signed}")
    private String signedDocsPath;

    private Path resolvedSignedDocsPath;

    @PostConstruct
    public void init() throws IOException {
        resolvedSignedDocsPath = Path.of(signedDocsPath).toAbsolutePath().normalize();
        Files.createDirectories(resolvedSignedDocsPath);
        log.info("Katalog podpisanych dokumentów: {}", resolvedSignedDocsPath);
    }

    /**
     * Podpisuje walidację elektronicznie i zmienia jej status na COMPLETED.
     *
     * @param validationId  ID walidacji
     * @param username      nazwa zalogowanego użytkownika (z Security Context)
     * @param rawPassword   hasło podane przez użytkownika w modalu
     * @param signingIntent treść oświadczenia (signing intent) wg Annex 11
     * @return zapis ValidationSignature
     * @throws IllegalStateException    jeśli walidacja już podpisana
     * @throws IllegalArgumentException jeśli hasło nieprawidłowe
     */
    @Transactional
    public ValidationSignature signValidation(Long validationId,
                                              String username,
                                              String rawPassword,
                                              String signingIntent) throws Exception {
        // 1. Pobierz walidację z pessimistic lock (zapobiega race condition przy równoległych żądaniach)
        Validation validation = validationRepository.findByIdForUpdate(validationId)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono walidacji ID: " + validationId));

        // 2. Guard: już podpisano? (bezpieczny dopiero po uzyskaniu locka)
        if (signatureRepository.findByValidationId(validationId).isPresent()) {
            throw new IllegalStateException("Walidacja ID " + validationId + " jest już podpisana.");
        }

        // 3. Weryfikacja hasła aplikacji (BCrypt)
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Nieznany użytkownik: " + username));
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            log.warn("Nieudana próba podpisania walidacji {} przez użytkownika {}", validationId, username);
            throw new IllegalArgumentException("Nieprawidłowe hasło. Podpis nie został złożony.");
        }

        // 4. Generuj Schemat Wizualny PDF (Faza 2: allocate → generate → record)
        ValidationDocument swDoc = trackingService.getOrAllocate(validation, DocumentType.SCHEMAT_WIZUALNY);
        LocalDateTime now = LocalDateTime.now();
        byte[] pdfBytes = schematService.generateSchematWizualnyPdf(
                validation, swDoc.getDocumentNumber(), username, now);
        trackingService.recordGeneration(swDoc.getId(), pdfBytes);

        // 5. Podpisz PDF — per-firma jeśli dostępny certyfikat, w przeciwnym razie globalny fallback
        String location = resolveLocation(validation);
        String reason = "Zatwierdzenie walidacji: " + signingIntent;

        Company company = resolveCompany(validation);
        Optional<CompanyCertificate> companyCert = (company != null)
                ? companyCertificateService.findActive(company.getId())
                : Optional.empty();

        byte[] signedPdf;
        String certSubject;
        String certSerial;

        if (companyCert.isPresent()) {
            CompanyCertificate cc = companyCert.get();
            log.info("Podpisywanie walidacji {} certyfikatem firmowym ID={}", validationId, cc.getId());
            signedPdf   = pdfSigningService.signPdf(pdfBytes, reason, location,
                                                     cc.getKeystoreData(), cc.getKeystorePassword());
            certSubject = pdfSigningService.getCertSubject(cc.getKeystoreData(), cc.getKeystorePassword());
            certSerial  = pdfSigningService.getCertSerial(cc.getKeystoreData(), cc.getKeystorePassword());
        } else {
            log.info("Podpisywanie walidacji {} globalnym certyfikatem (fallback)", validationId);
            signedPdf   = pdfSigningService.signPdf(pdfBytes, reason, location);
            certSubject = pdfSigningService.getCertSubject();
            certSerial  = pdfSigningService.getCertSerial();
        }

        // 6. Zapisz podpisany PDF na dysk
        String pdfPath = saveSignedPdf(validationId, signedPdf);

        // 7. Utwórz rekord podpisu
        ValidationSignature sig = ValidationSignature.builder()
                .validation(validation)
                .signedBy(username)
                .signedAt(now)
                .signingIntent(signingIntent)
                .certSubject(certSubject)
                .certSerial(certSerial)
                .documentHash(sha256hex(signedPdf))
                .signedPdfPath(pdfPath)
                .build();
        signatureRepository.save(sig);

        // 8. Zmień status → COMPLETED (nieodwracalne)
        validation.setStatus(ValidationStatus.COMPLETED);
        validationRepository.save(validation);

        log.info("Walidacja {} podpisana elektronicznie przez {} o {}. Plik: {}",
                validationId, username, now, pdfPath);
        return sig;
    }

    private String saveSignedPdf(Long validationId, byte[] bytes) throws IOException {
        Path file = resolvedSignedDocsPath
                .resolve("validation_" + validationId + "_signed.pdf")
                .normalize();
        if (!file.startsWith(resolvedSignedDocsPath)) {
            throw new SecurityException("Path traversal detected for validationId: " + validationId);
        }
        Files.write(file, bytes);
        return file.toString();
    }

    private String resolveLocation(Validation validation) {
        try {
            if (!validation.getMeasurementSeries().isEmpty()) {
                var device = validation.getMeasurementSeries().get(0).getCoolingDevice();
                if (device != null && device.getLaboratory() != null) {
                    return device.getLaboratory().getFullName();
                }
            }
        } catch (Exception ignored) {
        }
        return "Laboratorium";
    }

    private Company resolveCompany(Validation validation) {
        try {
            return validation.getMeasurementSeries().get(0)
                    .getCoolingDevice().getDepartment().getCompany();
        } catch (Exception e) {
            return null;
        }
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
            log.error("Błąd obliczania SHA-256", e);
            return null;
        }
    }
}
