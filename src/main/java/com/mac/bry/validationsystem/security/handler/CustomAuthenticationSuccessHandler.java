package com.mac.bry.validationsystem.security.handler;

import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.UserPermissionsCache;
import com.mac.bry.validationsystem.security.repository.UserRepository;
import com.mac.bry.validationsystem.security.service.PermissionService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final PermissionService permissionService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws ServletException, IOException {

        User user = (User) authentication.getPrincipal();

        // 1. Reset failed attempts & zaktualizuj lastLogin info
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLogin(LocalDateTime.now());

        // 2. FIX #1 Rebuild JSON permissions cache after successful login
        try {
            permissionService.rebuildUserPermissionsCache(user.getId());
            // Przeładuj obiekt użytkownika z bazy, aby mieć świeży cache w principalu
            User updatedUser = userRepository.findById(user.getId()).orElse(user);
            // Uwaga: Spring Security trzyma 'principal' w Authentication.
            // Ponieważ User implementuje UserDetails, zmiana pól w obiekcie 'user'
            // który jest principalem w sesji (przez referencję) powinna wystarczyć,
            // ale wywołanie rebuildUserPermissionsCache i tak aktualizuje bazę.
        } catch (Exception e) {
            logger.error("Error rebuilding permissions cache on login", e);
        }

        userRepository.save(user);

        // Kontynuuj standardowe zachowanie frameworka (przekierowanie na żądany
        // endpoint lub default Target Url)
        super.setDefaultTargetUrl("/");
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
