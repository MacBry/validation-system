package com.mac.bry.validationsystem.security.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SecurityKeyService - GMP Compliance Tests")
class SecurityKeyServiceTest {

    @Test
    @DisplayName("Should generate secure key when no configuration provided")
    void shouldGenerateSecureKeyWhenNoConfigurationProvided() {
        // Given
        SecurityKeyService service = new SecurityKeyService("");

        // When
        String key = service.getRememberMeKey();

        // Then
        assertThat(key).isNotNull();
        assertThat(key).hasSizeGreaterThanOrEqualTo(32);
        assertThat(key).doesNotContain("validationSystemSecretKey");
    }

    @Test
    @DisplayName("Should use provided configuration key when available")
    void shouldUseProvidedConfigurationKeyWhenAvailable() {
        // Given
        String configuredKey = "MySecureConfiguredKeyThatIsSufficientlyLongForGMPCompliance123!";
        SecurityKeyService service = new SecurityKeyService(configuredKey);

        // When
        String key = service.getRememberMeKey();

        // Then
        assertThat(key).isEqualTo(configuredKey);
    }

    @Test
    @DisplayName("Should reject weak keys - too short")
    void shouldRejectWeakKeysTooShort() {
        // Given & When & Then
        assertThatThrownBy(() -> new SecurityKeyService("shortkey"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GMP VIOLATION")
                .hasMessageContaining("minimum 32 znaki");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "validationSystemSecretKeyButLongEnoughForGMPCompliance123456789",
            "mypasswordkey123456789012345678901234567890ExtraCharsForLength",
            "secretkey123456789012345678901234567890MoreCharsToMeetMinimum"
    })
    @DisplayName("Should accept but warn about potentially weak patterns")
    void shouldAcceptButWarnAboutWeakPatterns(String weakKey) {
        // Given & When - should not throw but will log warning
        SecurityKeyService service = new SecurityKeyService(weakKey);

        // Then
        assertThat(service.getRememberMeKey()).isEqualTo(weakKey);
    }

    @Test
    @DisplayName("Generated keys should have sufficient entropy (256-bit)")
    void generatedKeysShouldHaveSufficientEntropy() {
        // Given
        SecurityKeyService service = new SecurityKeyService("");

        // When
        String key1 = service.generateSecureKey();
        String key2 = service.generateSecureKey();

        // Then
        assertThat(key1).isNotEqualTo(key2);

        // Verify Base64 format (should decode without error)
        assertThatNoException().isThrownBy(() -> Base64.getDecoder().decode(key1));
        assertThatNoException().isThrownBy(() -> Base64.getDecoder().decode(key2));

        // Verify key length corresponds to 256-bit (32 bytes -> 44 chars in Base64 including padding)
        byte[] decodedKey1 = Base64.getDecoder().decode(key1);
        assertThat(decodedKey1).hasSize(32); // 256 bits
    }

    @Test
    @DisplayName("Should generate cryptographically random keys (statistical test)")
    void shouldGenerateCryptographicallyRandomKeys() {
        // Given
        SecurityKeyService service = new SecurityKeyService("");
        Set<String> generatedKeys = new HashSet<>();
        int keyCount = 100;

        // When - Generate multiple keys
        for (int i = 0; i < keyCount; i++) {
            String key = service.generateSecureKey();
            generatedKeys.add(key);
        }

        // Then - All keys should be unique (extremely high probability with 256-bit entropy)
        assertThat(generatedKeys).hasSize(keyCount);
    }

    @Test
    @DisplayName("Session tokens should be cryptographically secure")
    void sessionTokensShouldBeCryptographicallySecure() {
        // Given
        SecurityKeyService service = new SecurityKeyService("");

        // When
        String token1 = service.generateSessionToken();
        String token2 = service.generateSessionToken();

        // Then
        assertThat(token1).isNotEqualTo(token2);
        assertThat(token1).hasSize(32); // 16 bytes -> 32 hex chars
        assertThat(token2).hasSize(32);

        // Verify hex format
        assertThat(token1).matches("[0-9a-f]+");
        assertThat(token2).matches("[0-9a-f]+");
    }

    @Test
    @DisplayName("Key rotation should generate new key")
    void keyRotationShouldGenerateNewKey() {
        // Given
        SecurityKeyService service = new SecurityKeyService("");
        String originalKey = service.getRememberMeKey();

        // When
        String rotatedKey = service.rotateRememberMeKey();

        // Then
        assertThat(rotatedKey).isNotEqualTo(originalKey);
        assertThat(rotatedKey).hasSizeGreaterThanOrEqualTo(32);
    }

    @Test
    @DisplayName("Should handle null configuration gracefully")
    void shouldHandleNullConfigurationGracefully() {
        // Given & When
        SecurityKeyService service = new SecurityKeyService(null);

        // Then
        assertThat(service.getRememberMeKey()).isNotNull();
        assertThat(service.getRememberMeKey()).hasSizeGreaterThanOrEqualTo(32);
    }

    @Test
    @DisplayName("Should handle empty/whitespace configuration gracefully")
    void shouldHandleEmptyWhitespaceConfigurationGracefully() {
        // Given & When
        SecurityKeyService serviceEmpty = new SecurityKeyService("");
        SecurityKeyService serviceWhitespace = new SecurityKeyService("   ");

        // Then
        assertThat(serviceEmpty.getRememberMeKey()).isNotNull();
        assertThat(serviceEmpty.getRememberMeKey()).hasSizeGreaterThanOrEqualTo(32);

        assertThat(serviceWhitespace.getRememberMeKey()).isNotNull();
        assertThat(serviceWhitespace.getRememberMeKey()).hasSizeGreaterThanOrEqualTo(32);
    }

    @Test
    @DisplayName("Should trim whitespace from configured key")
    void shouldTrimWhitespaceFromConfiguredKey() {
        // Given
        String keyWithWhitespace = "   MySecureConfiguredKeyThatIsSufficientlyLongForGMPCompliance123!   ";
        String expectedKey = "MySecureConfiguredKeyThatIsSufficientlyLongForGMPCompliance123!";

        // When
        SecurityKeyService service = new SecurityKeyService(keyWithWhitespace);

        // Then
        assertThat(service.getRememberMeKey()).isEqualTo(expectedKey);
    }

    @Test
    @DisplayName("Multiple service instances should generate different keys")
    void multipleServiceInstancesShouldGenerateDifferentKeys() {
        // Given & When
        SecurityKeyService service1 = new SecurityKeyService("");
        SecurityKeyService service2 = new SecurityKeyService("");

        // Then
        assertThat(service1.getRememberMeKey()).isNotEqualTo(service2.getRememberMeKey());
    }
}