package com.mac.bry.validationsystem.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generuje unikalny token (nonce) dla każdego żądania HTTP.
 * Token ten jest wykorzystywany w nagłówku Content-Security-Policy
 * oraz w szablonach Thymeleaf do autoryzacji skryptów inline.
 */
@Component
public class ContentSecurityPolicyNonceFilter extends OncePerRequestFilter {

    public static final String NONCE_ATTRIBUTE = "cspNonce";
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Generuj 128-bitowy (16 bajtów) losowy nonce
        byte[] nonceBytes = new byte[16];
        secureRandom.nextBytes(nonceBytes);
        String nonce = Base64.getEncoder().encodeToString(nonceBytes);

        // Zapisz nonce w atrybutach żądania dla Thymeleaf
        request.setAttribute(NONCE_ATTRIBUTE, nonce);

        filterChain.doFilter(request, response);
    }
}
