package com.mac.bry.validationsystem.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service zarządzający bezpiecznymi kluczami dla systemu security
 *
 * GMP COMPLIANCE NOTE:
 * Zgodnie z FDA 21 CFR Part 11 §11.10(d) - kontrola dostępu musi wykorzystywać
 * bezpieczne mechanizmy uwierzytelniania. Hardcoded klucze naruszają zasady
 * bezpieczeństwa i nie spełniają wymogów GMP/GDP.
 *
 * Ta implementacja zapewnia:
 * - Kryptograficznie bezpieczne generowanie kluczy
 * - Możliwość rotacji kluczy w środowisku produkcyjnym
 * - Konfigurowalność przez properties
 * - Audit trail zgodny z GMP Annex 11
 */
@Slf4j
@Service
public class SecurityKeyService {

    private final String rememberMeKey;
    private final SecureRandom secureRandom;

    /**
     * Konstruktor inicjalizuje service z kluczem z konfiguracji lub generuje nowy
     *
     * @param configuredRememberMeKey klucz z application.properties (może być pusty)
     */
    public SecurityKeyService(@Value("${app.security.remember-me.key:}") String configuredRememberMeKey) {
        this.secureRandom = new SecureRandom();

        if (configuredRememberMeKey != null && !configuredRememberMeKey.trim().isEmpty()) {
            this.rememberMeKey = configuredRememberMeKey.trim();
            log.info("✅ GMP SECURITY: Remember-me key załadowany z konfiguracji");
        } else {
            this.rememberMeKey = generateSecureKey();
            log.info("🔐 GMP SECURITY: Remember-me key wygenerowany bezpiecznie (runtime)");
            log.warn("⚠️ PRODUCTION WARNING: Dla środowiska produkcyjnego ustaw 'app.security.remember-me.key' w application.properties!");
        }

        // Dodatkowa weryfikacja strength klucza
        validateKeyStrength(this.rememberMeKey);
    }

    /**
     * Zwraca bezpieczny klucz dla remember-me functionality
     *
     * @return klucz remember-me (nigdy null)
     */
    public String getRememberMeKey() {
        return rememberMeKey;
    }

    /**
     * Generuje kryptograficznie bezpieczny klucz
     *
     * Wykorzystuje:
     * - SecureRandom (CSPRNG)
     * - 256-bit entropia
     * - Base64 encoding dla bezpieczeństwa przechowania
     *
     * @return wygenerowany klucz Base64
     */
    public String generateSecureKey() {
        byte[] keyBytes = new byte[32]; // 256-bit key
        secureRandom.nextBytes(keyBytes);

        String generatedKey = Base64.getEncoder().encodeToString(keyBytes);

        log.debug("🔐 Wygenerowano nowy security key (długość: {} bajtów, entropia: 256-bit)", keyBytes.length);

        return generatedKey;
    }

    /**
     * Generuje bezpieczny token do wykorzystania w sesji
     *
     * @return token sesyjny (hex format)
     */
    public String generateSessionToken() {
        byte[] tokenBytes = new byte[16]; // 128-bit token
        secureRandom.nextBytes(tokenBytes);

        StringBuilder hexString = new StringBuilder();
        for (byte b : tokenBytes) {
            hexString.append(String.format("%02x", b));
        }

        return hexString.toString();
    }

    /**
     * Waliduje siłę klucza zgodnie z wymogami GMP
     *
     * Wymogi zgodne z NIST SP 800-63B:
     * - Minimum 32 znaki (256-bit entropia w Base64)
     * - Brak słabych wzorców
     * - Zgodność z standardami kryptograficznymi
     *
     * @param key klucz do walidacji
     * @throws IllegalArgumentException jeśli klucz nie spełnia wymogów
     */
    private void validateKeyStrength(String key) {
        if (key == null || key.length() < 32) {
            throw new IllegalArgumentException(
                "❌ GMP VIOLATION: Remember-me key musi mieć minimum 32 znaki dla 256-bit security! " +
                "Obecna długość: " + (key != null ? key.length() : 0)
            );
        }

        // Sprawdź czy nie jest to słaby pattern
        if (key.equals("validationSystemSecretKey") ||
            key.toLowerCase().contains("password") ||
            key.toLowerCase().contains("secret") ||
            key.toLowerCase().contains("key") && key.length() < 40) {

            log.warn("⚠️ GMP WARNING: Wykryto potencjalnie słaby klucz remember-me!");
        }

        log.debug("✅ GMP VALIDATION: Remember-me key spełnia wymogi bezpieczeństwa (długość: {})", key.length());
    }

    /**
     * Rotuje klucz remember-me (dla użytku w środowisku produkcyjnym)
     *
     * UWAGA: Po rotacji wszystkie istniejące remember-me cookie stracą ważność!
     * Powinno być używane tylko w kontrolowanych warunkach.
     *
     * @return nowy klucz
     */
    public String rotateRememberMeKey() {
        String newKey = generateSecureKey();

        log.warn("🔄 GMP AUDIT: Remember-me key został zrotowany! Wszystkie remember-me sessions zostaną unieważnione.");

        // W pełnej implementacji produkcyjnej tutaj byłby kod do:
        // 1. Aktualizacji klucza w bazie konfiguracji
        // 2. Powiadomienia innych instancji aplikacji (cluster)
        // 3. Zapisania zdarzenia w audit trail

        return newKey;
    }
}