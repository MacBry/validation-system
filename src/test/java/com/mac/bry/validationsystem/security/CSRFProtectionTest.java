package com.mac.bry.validationsystem.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class CSRFProtectionTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldBlockPOSTWithoutCSRFToken() throws Exception {
        mockMvc.perform(post("/perform_login")
                .param("username", "testuser")
                .param("password", "testpass"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Location",
                        org.hamcrest.Matchers.containsString("/error/csrf")));
    }

    @Test
    void shouldAllowPOSTWithCSRFToken() throws Exception {
        mockMvc.perform(post("/perform_login")
                .param("username", "testuser")
                .param("password", "testpass")
                .with(csrf()))
                .andExpect(status().isFound()); // Found (302) oznacza, że żądanie przeszło przez filtr CSRF i dotarło
                                                // do uwierzytelniania, co skutkuje redirectem do /login?error
    }
}
