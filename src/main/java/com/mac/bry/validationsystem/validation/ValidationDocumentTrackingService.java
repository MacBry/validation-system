package com.mac.bry.validationsystem.validation;

import com.mac.bry.validationsystem.security.service.SecurityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Śledzi historię generowania dokumentów walidacji.
 *
 * Przepływ dwufazowy (Faza 2):
 *   1. getOrAllocate()     — przed generowaniem PDF: nadaje numer dokumentu (lub zwraca istniejący).
 *   2. recordGeneration()  — po generowaniu: inkrementuje licznik, oblicza hash, wykrywa zmiany danych.
 *
 * Dla dokumentów gdzie numer nie musi być znany przed generowaniem (np. Word) używaj trackGeneration().
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidationDocumentTrackingService {

    private final ValidationDocumentRepository documentRepository;
    private final DocumentNumberingService numberingService;
    private final SecurityService securityService;

    // ── Faza 1: alokacja numeru dokumentu przed generowaniem ─────────────────

    /**
     * Pobiera istniejący rekord dokumentu lub tworzy nowy z przydzielonym numerem (count = 0).
     * Wywoływać PRZED generowaniem dokumentu, gdy numer musi być znany z wyprzedzeniem (np. stopka PDF).
     */
    @Transactional
    public ValidationDocument getOrAllocate(Validation validation, DocumentType documentType) {
        return documentRepository
                .findByValidationIdAndDocumentType(validation.getId(), documentType)
                .orElseGet(() -> {
                    String labAbbrev = resolveLabAbbrev(validation);
                    int year = LocalDateTime.now().getYear();
                    String docNumber = numberingService.generateNextNumber(
                            documentType.getPrefix(), labAbbrev, year);

                    ValidationDocument doc = new ValidationDocument();
                    doc.setValidation(validation);
                    doc.setDocumentNumber(docNumber);
                    doc.setDocumentType(documentType);
                    doc.setGenerationCount(0);
                    doc.setDataChanged(false);

                    ValidationDocument saved = documentRepository.save(doc);
                    log.info("Zaalokowano numer dokumentu: {} (walidacja ID: {})", docNumber, validation.getId());
                    return saved;
                });
    }

    // ── Faza 2: rejestracja zakończonej generacji ─────────────────────────────

    /**
     * Rejestruje zakończone generowanie dokumentu.
     * Przy pierwszej generacji (count == 0): zapisuje hash SHA-256 treści.
     * Przy kolejnych: porównuje hash i ustawia dataChanged jeśli treść się zmieniła.
     *
     * @param docId   ID rekordu (z getOrAllocate lub wcześniejszej generacji)
     * @param content treść dokumentu do hashowania; null = brak hashowania (np. Word)
     */
    @Transactional
    public ValidationDocument recordGeneration(Long docId, byte[] content) {
        ValidationDocument doc = documentRepository.findById(docId)
                .orElseThrow(() -> new IllegalStateException("Nie znaleziono rekordu dokumentu ID: " + docId));

        String currentUser = getCurrentUsername();
        LocalDateTime now = LocalDateTime.now();
        boolean isFirst = doc.getGenerationCount() == 0;

        if (isFirst) {
            doc.setFirstGeneratedAt(now);
            doc.setFirstGeneratedBy(currentUser);
            if (content != null) {
                doc.setPdfHashSha256(computeSha256(content));
            }
            doc.setDataChanged(false);
        } else {
            if (content != null && doc.getPdfHashSha256() != null) {
                String currentHash = computeSha256(content);
                boolean changed = !doc.getPdfHashSha256().equals(currentHash);
                doc.setDataChanged(changed);
                if (changed) {
                    log.warn("Wykryto zmianę danych w dokumencie {} (walidacja ID: {})",
                            doc.getDocumentNumber(), doc.getValidation().getId());
                }
            }
        }

        doc.setGenerationCount(doc.getGenerationCount() + 1);
        doc.setLastGeneratedAt(now);
        doc.setLastGeneratedBy(currentUser);

        log.info("Zarejestrowano generację dokumentu: {} — łącznie: {}",
                doc.getDocumentNumber(), doc.getGenerationCount());
        return documentRepository.save(doc);
    }

    // ── Skrót jednoetapowy (dla dokumentów bez numeru w treści) ──────────────

    /**
     * Jednoetapowe śledzenie generacji (getOrAllocate + recordGeneration).
     * Używać gdy numer dokumentu NIE musi być znany przed generowaniem treści.
     */
    @Transactional
    public ValidationDocument trackGeneration(Validation validation, DocumentType documentType, byte[] content) {
        ValidationDocument doc = getOrAllocate(validation, documentType);
        return recordGeneration(doc.getId(), content);
    }

    // ── Odczyt ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ValidationDocument> getDocumentsForValidation(Long validationId) {
        return documentRepository.findByValidationIdOrderByDocumentTypeAsc(validationId);
    }

    // ── Narzędzia publiczne ───────────────────────────────────────────────────

    public String getCurrentUsername() {
        var user = securityService.getCurrentUser();
        return user != null ? user.getUsername() : "system";
    }

    // ── Prywatne ─────────────────────────────────────────────────────────────

    private String resolveLabAbbrev(Validation validation) {
        if (validation.getMeasurementSeries() != null && !validation.getMeasurementSeries().isEmpty()) {
            var device = validation.getMeasurementSeries().get(0).getCoolingDevice();
            if (device != null && device.getLaboratory() != null) {
                return device.getLaboratory().getAbbreviation();
            }
        }
        return "BRK";
    }

    private String computeSha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 niedostępny", e);
            return null;
        }
    }
}
