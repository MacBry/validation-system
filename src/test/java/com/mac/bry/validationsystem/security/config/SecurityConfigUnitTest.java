package com.mac.bry.validationsystem.security.config;

import com.mac.bry.validationsystem.security.filter.ContentSecurityPolicyNonceFilter;
import com.mac.bry.validationsystem.security.filter.ForcedPasswordChangeFilter;
import com.mac.bry.validationsystem.security.handler.CsrfViolationHandler;
import com.mac.bry.validationsystem.security.handler.CustomAuthenticationFailureHandler;
import com.mac.bry.validationsystem.security.handler.CustomAuthenticationSuccessHandler;
import com.mac.bry.validationsystem.security.service.SecurityKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityConfig Unit Tests - GMP Compliance")
class SecurityConfigUnitTest {

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private CustomAuthenticationSuccessHandler successHandler;

    @Mock
    private CustomAuthenticationFailureHandler failureHandler;

    @Mock
    private ForcedPasswordChangeFilter forcedPasswordChangeFilter;

    @Mock
    private CsrfViolationHandler csrfViolationHandler;

    @Mock
    private org.springframework.security.core.session.SessionRegistry sessionRegistry;

    @Mock
    private ContentSecurityPolicyNonceFilter nonceFilter;

    private SecurityKeyService securityKeyService;
    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        securityKeyService = new SecurityKeyService("TestSecureKeyForUnitTestsThatIs256BitCompliant123456");
        securityConfig = new SecurityConfig(
                userDetailsService,
                successHandler,
                failureHandler,
                forcedPasswordChangeFilter,
                csrfViolationHandler,
                securityKeyService,
                sessionRegistry,
                nonceFilter);
    }

    @Test
    @DisplayName("SecurityConfig should create PasswordEncoder with proper strength")
    void securityConfigShouldCreatePasswordEncoderWithProperStrength() {
        // When
        var passwordEncoder = securityConfig.passwordEncoder();

        // Then
        assertThat(passwordEncoder).isNotNull();
        assertThat(passwordEncoder.getClass().getSimpleName()).isEqualTo("BCryptPasswordEncoder");

        // Test encoding works
        String encoded = passwordEncoder.encode("testPassword123!");
        assertThat(encoded).isNotNull();
        assertThat(encoded).isNotEqualTo("testPassword123!");
        assertThat(passwordEncoder.matches("testPassword123!", encoded)).isTrue();
    }

    @Test
    @DisplayName("SecurityConfig should create DaoAuthenticationProvider")
    void securityConfigShouldCreateDaoAuthenticationProvider() {
        // When
        var authProvider = securityConfig.authenticationProvider();

        // Then
        assertThat(authProvider).isNotNull();
        assertThat(authProvider.getClass().getSimpleName()).isEqualTo("DaoAuthenticationProvider");
    }

    @Test
    @DisplayName("SecurityKeyService should be properly injected and provide secure key")
    void securityKeyServiceShouldBeProperlyInjectedAndProvideSecureKey() {
        // When
        String key = securityKeyService.getRememberMeKey();

        // Then
        assertThat(key).isEqualTo("TestSecureKeyForUnitTestsThatIs256BitCompliant123456");
        assertThat(key).hasSizeGreaterThanOrEqualTo(32);
    }

    @Test
    @DisplayName("SecurityKeyService should generate unique session tokens")
    void securityKeyServiceShouldGenerateUniqueSessionTokens() {
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
    @DisplayName("SecurityKeyService should generate cryptographically secure keys")
    void securityKeyServiceShouldGenerateCryptographicallySecureKeys() {
        // When
        String key1 = securityKeyService.generateSecureKey();
        String key2 = securityKeyService.generateSecureKey();

        // Then
        assertThat(key1).isNotEqualTo(key2);
        assertThat(key1).hasSizeGreaterThanOrEqualTo(32);
        assertThat(key2).hasSizeGreaterThanOrEqualTo(32);
    }

    @Test
    @DisplayName("SecurityConfig components should be properly wired")
    void securityConfigComponentsShouldBeProperlyWired() {
        // Then - verify all dependencies are injected
        assertThat(securityConfig).isNotNull();

        // Verify beans can be created
        assertThatNoException().isThrownBy(() -> securityConfig.passwordEncoder());
        assertThatNoException().isThrownBy(() -> securityConfig.authenticationProvider());
    }
}