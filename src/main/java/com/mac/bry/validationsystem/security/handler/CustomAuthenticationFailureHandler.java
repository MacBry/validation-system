package com.mac.bry.validationsystem.security.handler;

import com.mac.bry.validationsystem.security.User;
import com.mac.bry.validationsystem.security.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final UserRepository userRepository;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_TIME_DURATION_MINUTES = 15;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {

        String username = request.getParameter("username");
        String errorMessage = "Nieprawidłowa nazwa użytkownika lub hasło.";

        if (username != null && !username.isEmpty()) {
            var userOpt = userRepository.findByUsername(username);
            if (userOpt.isPresent()) {
                User user = userOpt.get();

                if (exception instanceof LockedException) {
                    // Account is already locked (from a previous attempt)
                    errorMessage = "Konto zostało tymczasowo zablokowane z powodu zbyt wielu nieudanych prób logowania. " +
                                   "Spróbuj ponownie za " + LOCK_TIME_DURATION_MINUTES + " minut.";
                    log.warn("Próba logowania na zablokowane konto: {}", username);
                } else if (exception instanceof BadCredentialsException) {
                    if (user.isEnabled() && user.isAccountNonLocked()) {
                        int attempts = user.getFailedLoginAttempts() + 1;
                        user.setFailedLoginAttempts(attempts);

                        if (attempts >= MAX_FAILED_ATTEMPTS) {
                            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_TIME_DURATION_MINUTES));
                            user.setLocked(true);
                            errorMessage = "Konto zostało zablokowane na " + LOCK_TIME_DURATION_MINUTES +
                                           " minut z powodu " + MAX_FAILED_ATTEMPTS + " nieudanych prób logowania.";
                            log.warn("Konto {} zablokowane po {} nieudanych próbach", username, attempts);
                        } else {
                            int remaining = MAX_FAILED_ATTEMPTS - attempts;
                            errorMessage = "Nieprawidłowe hasło. Pozostało prób: " + remaining + " z " + MAX_FAILED_ATTEMPTS + ".";
                            log.info("Nieudana próba logowania {}/{} dla użytkownika: {}", attempts, MAX_FAILED_ATTEMPTS, username);
                        }

                        userRepository.save(user);
                    } else if (!user.isAccountNonLocked()) {
                        errorMessage = "Konto zostało tymczasowo zablokowane. Spróbuj ponownie za " + LOCK_TIME_DURATION_MINUTES + " minut.";
                    } else if (!user.isEnabled()) {
                        errorMessage = "Konto zostało dezaktywowane. Skontaktuj się z administratorem.";
                    }
                }
            }
            // If user not found, keep the generic "wrong username/password" message
        }

        request.getSession().setAttribute("errorMessage", errorMessage);
        super.setDefaultFailureUrl("/login?error=true");
        super.onAuthenticationFailure(request, response, exception);
    }
}

