package com.mac.bry.validationsystem.security.config;

import com.mac.bry.validationsystem.security.filter.ForcedPasswordChangeFilter;
import com.mac.bry.validationsystem.security.filter.SessionSecurityFilter;
import com.mac.bry.validationsystem.security.handler.CsrfViolationHandler;
import com.mac.bry.validationsystem.security.handler.CustomAuthenticationFailureHandler;
import com.mac.bry.validationsystem.security.handler.CustomAuthenticationSuccessHandler;
import com.mac.bry.validationsystem.security.service.SecurityKeyService;
import com.mac.bry.validationsystem.security.service.SessionManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.session.SessionFixationProtectionStrategy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final UserDetailsService userDetailsService;
        private final CustomAuthenticationSuccessHandler successHandler;
        private final CustomAuthenticationFailureHandler failureHandler;
        private final ForcedPasswordChangeFilter forcedPasswordChangeFilter;
        private final CsrfViolationHandler csrfViolationHandler;
        private final SecurityKeyService securityKeyService;
        private final SessionRegistry sessionRegistry;
        private final ContentSecurityPolicyNonceFilter nonceFilter;

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder(12);
        }


        @Bean
        public DaoAuthenticationProvider authenticationProvider() {
                DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
                authProvider.setUserDetailsService(userDetailsService);
                authProvider.setPasswordEncoder(passwordEncoder());
                return authProvider;
        }

        @Bean
        public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
                return authConfig.getAuthenticationManager();
        }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http, SessionManagementService sessionManagementService) throws Exception {
                http
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**",
                                                                "/fa/**", "/fonts/**", "/.well-known/**")
                                                .permitAll()
                                                .requestMatchers("/login", "/forgot-password", "/reset-password",
                                                                "/error")
                                                .permitAll()
                                                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                                                .anyRequest().authenticated())
                                .formLogin(form -> form
                                                .loginPage("/login")
                                                .loginProcessingUrl("/perform_login")
                                                .successHandler(successHandler)
                                                .failureHandler(failureHandler)
                                                .permitAll())
                                .logout(logout -> logout
                                                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                                                .logoutSuccessUrl("/login?logout")
                                                .invalidateHttpSession(true)
                                                .deleteCookies("JSESSIONID", "remember-me")
                                                .permitAll())
                                .rememberMe(remember -> remember
                                                .key(securityKeyService.getRememberMeKey())
                                                .tokenValiditySeconds(86400) // 1 day - GMP compliance
                                                .userDetailsService(userDetailsService))
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                                                .sessionFixation().changeSessionId() // GMP: Ochrona przed Session
                                                                                     // Fixation
                                                .maximumSessions(1)
                                                .maxSessionsPreventsLogin(false)
                                                .sessionRegistry(sessionRegistry))
                                .csrf(csrf -> csrf
                                                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                                                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                                                .ignoringRequestMatchers(
                                                                "/actuator/health",
                                                                "/actuator/info"))
                                .exceptionHandling(ex -> ex
                                                .accessDeniedHandler(csrfViolationHandler))
                                .headers(headers -> headers
                                                .frameOptions(frameOptions -> frameOptions.sameOrigin())
                                                .xssProtection(xss -> {}) // Enable XSS protection with defaults
                                                .httpStrictTransportSecurity(hsts -> hsts.disable())
                                                .contentSecurityPolicy(csp -> csp.policyDirectives(
                                                "default-src 'self'; " +
                                                                "script-src 'self' 'nonce-{nonce}'; " +
                                                                "style-src 'self' 'unsafe-inline'; " + // Bootstrap still needs unsafe-inline for styles unless handled individually
                                                                "img-src 'self' data: blob:; " +
                                                                "font-src 'self' data:; " +
                                                                "connect-src 'self'; " +
                                                                "frame-ancestors 'self';")));

                http.authenticationProvider(authenticationProvider());

                // Dodaj filtr generujący nonce dla CSP (musi być przed filtracją dostępu)
                http.addFilterBefore(nonceFilter, UsernamePasswordAuthenticationFilter.class);

                // Dodaj filtr wymuszonej zmiany hasła po uwierzytelnieniu
                http.addFilterAfter(forcedPasswordChangeFilter, UsernamePasswordAuthenticationFilter.class);

                // Dodaj filtr bezpieczeństwa sesji (GMP compliance)
                http.addFilterAfter(new SessionSecurityFilter(sessionManagementService), ForcedPasswordChangeFilter.class);

                return http.build();
        }

        /**
         * Zapobiega automatycznej rejestracji ForcedPasswordChangeFilter
         * jako filtr serwletu przez Spring Boot.
         * Filtr jest już zarejestrowany w SecurityFilterChain powyżej.
         */
        @Bean
        public FilterRegistrationBean<ForcedPasswordChangeFilter> disableForcedPasswordChangeAutoRegistration(
                        ForcedPasswordChangeFilter filter) {
                FilterRegistrationBean<ForcedPasswordChangeFilter> registration = new FilterRegistrationBean<>(filter);
                registration.setEnabled(false);
                return registration;
        }
}
