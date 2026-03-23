package com.mac.bry.validationsystem.security;

import com.mac.bry.validationsystem.security.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Import;
import com.mac.bry.validationsystem.config.TestMailConfig;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestMailConfig.class)
public class LoginIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        if (userRepository.findByUsername("integrationuser").isEmpty()) {
            User user = new User();
            user.setUsername("integrationuser");
            user.setPassword(passwordEncoder.encode("correctPassword123"));
            user.setEmail("int@example.com");
            user.setEnabled(true);
            user.setFailedLoginAttempts(0);
            user.setLocked(false);
            userRepository.save(user);
        } else {
            User user = userRepository.findByUsername("integrationuser").get();
            user.setFailedLoginAttempts(0);
            user.setLocked(false);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
    }

    @Test
    void shouldRedirectToLoginForUnauthenticated() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void shouldLoginWithCorrectCredentials() throws Exception {
        mockMvc.perform(post("/perform_login")
                .param("username", "integrationuser")
                .param("password", "correctPassword123")
                .with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/")); // Assuming successful login redirects to /
    }

    @Test
    void shouldFailWithIncorrectPassword() throws Exception {
        mockMvc.perform(post("/perform_login")
                .param("username", "integrationuser")
                .param("password", "wrongpassword")
                .with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?error=true"));
    }

    @Test
    void shouldLockAfter5FailedAttempts() throws Exception {
        // 5 prób błędnego logowania
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/perform_login")
                    .param("username", "integrationuser")
                    .param("password", "wrongpassword")
                    .with(csrf()))
                    .andExpect(status().isFound())
                    .andExpect(redirectedUrl("/login?error=true"));
        }

        // 6 próba - konto zablokowane - ale tym razem użyjemy poprawnego hasła
        mockMvc.perform(post("/perform_login")
                .param("username", "integrationuser")
                .param("password", "correctPassword123")
                .with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?error=true"));

        User lockedUser = userRepository.findByUsername("integrationuser").get();
        assertTrue(
                lockedUser.isLocked() || (lockedUser.getLockedUntil() != null
                        && lockedUser.getLockedUntil().isAfter(java.time.LocalDateTime.now())),
                "User account should be locked!");
    }
}
