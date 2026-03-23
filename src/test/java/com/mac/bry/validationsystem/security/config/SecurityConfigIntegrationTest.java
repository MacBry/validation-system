package com.mac.bry.validationsystem.security.config;

import com.mac.bry.validationsystem.security.service.SecurityKeyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureWebMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.security.remember-me.key=TestSecureKeyForIntegrationTestsThatIs256BitCompliant123456",
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.redis.host=localhost",
        "spring.redis.port=6379",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"
})
@DisplayName("SecurityConfig Integration Tests - GMP Compliance")
class SecurityConfigIntegrationTest {

    @Autowired
    private SecurityKeyService securityKeyService;

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Test
    @DisplayName("SecurityKeyService should be properly injected")
    void securityKeyServiceShouldBeProperlyInjected() {
        // Then
        assertThat(securityKeyService).isNotNull();
    }

    @Test
    @DisplayName("SecurityKeyService should use configured key")
    void securityKeyServiceShouldUseConfiguredKey() {
        // When
        String rememberMeKey = securityKeyService.getRememberMeKey();

        // Then
        assertThat(rememberMeKey).isEqualTo("TestSecureKeyForIntegrationTestsThatIs256BitCompliant123456");
    }

    @Test
    @DisplayName("SecurityFilterChain should be properly configured")
    void securityFilterChainShouldBeProperlyConfigured() {
        // Then
        assertThat(securityFilterChain).isNotNull();
    }

    @Test
    @DisplayName("SecurityKeyService should generate secure session tokens")
    void securityKeyServiceShouldGenerateSecureSessionTokens() {
        // When
        String token1 = securityKeyService.generateSessionToken();
        String token2 = securityKeyService.generateSessionToken();

        // Then
        assertThat(token1).isNotEqualTo(token2);
        assertThat(token1).hasSize(32);
        assertThat(token2).hasSize(32);
        assertThat(token1).matches("[0-9a-f]+");
        assertThat(token2).matches("[0-9a-f]+");
    }

    @Test
    @DisplayName("Key rotation should work in Spring context")
    void keyRotationShouldWorkInSpringContext() {
        // Given
        String originalKey = securityKeyService.getRememberMeKey();

        // When
        String rotatedKey = securityKeyService.rotateRememberMeKey();

        // Then
        assertThat(rotatedKey).isNotEqualTo(originalKey);
        assertThat(rotatedKey).hasSizeGreaterThanOrEqualTo(32);

        // Original service should still return the original key
        // (rotation returns new key but doesn't modify the service instance)
        assertThat(securityKeyService.getRememberMeKey()).isEqualTo(originalKey);
    }
}