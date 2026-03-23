package com.mac.bry.validationsystem.notification;

import com.mac.bry.validationsystem.config.TestMailConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testy integracyjne endpointu GET /api/notifications.
 *
 * Używają MockMvc z @MockBean NotificationService — weryfikują
 * routing, format JSON i wymaganie autoryzacji.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestMailConfig.class)
@DisplayName("NotificationController — testy integracyjne")
class NotificationControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    NotificationService notificationService;

    // =========================================================================
    // 1. Dostęp nieautoryzowany
    // =========================================================================

    @Test
    @DisplayName("GET /api/notifications — zwraca 401/403 bez autoryzacji")
    void unauthenticated_shouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().is(anyOf(
                        equalTo(401), equalTo(302),  // Spring może też zrobić redirect do /login
                        equalTo(403)
                )));
    }

    // =========================================================================
    // 2. Pusta lista
    // =========================================================================

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    @DisplayName("GET /api/notifications — zwraca pustą tablicę JSON gdy brak powiadomień")
    void authenticated_emptyList() throws Exception {
        when(notificationService.getAllNotifications()).thenReturn(List.of());

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // =========================================================================
    // 3. Jedno powiadomienie
    // =========================================================================

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    @DisplayName("GET /api/notifications — zwraca powiadomienie VALIDATION")
    void authenticated_singleValidationNotification() throws Exception {
        NotificationDTO notif = new NotificationDTO(
                "VALIDATION",
                "Zbliża się termin walidacji",
                "Urządzenie \"Lodówka\" (INW-001) — za 5 dni",
                "/devices/1",
                5,
                "danger"
        );
        when(notificationService.getAllNotifications()).thenReturn(List.of(notif));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type", is("VALIDATION")))
                .andExpect(jsonPath("$[0].title", is("Zbliża się termin walidacji")))
                .andExpect(jsonPath("$[0].daysLeft", is(5)))
                .andExpect(jsonPath("$[0].severity", is("danger")))
                .andExpect(jsonPath("$[0].link", is("/devices/1")));
    }

    // =========================================================================
    // 4. Wiele powiadomień różnych typów
    // =========================================================================

    @Test
    @WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
    @DisplayName("GET /api/notifications — zwraca wiele powiadomień różnych typów")
    void authenticated_multipleNotifications() throws Exception {
        List<NotificationDTO> notifs = List.of(
                new NotificationDTO("VALIDATION", "Termin walidacji", "msg1", "/devices/1", 3, "danger"),
                new NotificationDTO("CALIBRATION", "Wygasa wzorcowanie", "msg2", "/recorders/2", 7, "danger"),
                new NotificationDTO("PASSWORD", "Wygasa hasło", "msg3", "/profile/change-password", 12, "warning"),
                new NotificationDTO("ADMIN_PASSWORD", "Hasło użytkownika", "msg4", "/admin/users/5", 20, "warning")
        );
        when(notificationService.getAllNotifications()).thenReturn(notifs);

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[*].type", containsInAnyOrder(
                        "VALIDATION", "CALIBRATION", "PASSWORD", "ADMIN_PASSWORD")));
    }

    // =========================================================================
    // 5. Struktura JSON
    // =========================================================================

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    @DisplayName("GET /api/notifications — odpowiedź JSON zawiera wszystkie wymagane pola")
    void responseContainsAllRequiredFields() throws Exception {
        NotificationDTO notif = new NotificationDTO(
                "CALIBRATION",
                "Wygasa wzorcowanie rejestratora",
                "Rejestrator S/N: SN-99999 — za 10 dni",
                "/recorders/3",
                10,
                "warning"
        );
        when(notificationService.getAllNotifications()).thenReturn(List.of(notif));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").exists())
                .andExpect(jsonPath("$[0].title").exists())
                .andExpect(jsonPath("$[0].message").exists())
                .andExpect(jsonPath("$[0].link").exists())
                .andExpect(jsonPath("$[0].daysLeft").exists())
                .andExpect(jsonPath("$[0].severity").exists())
                .andExpect(jsonPath("$[0].severity", is(oneOf("warning", "danger"))));
    }

    // =========================================================================
    // 6. Content-Type header
    // =========================================================================

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    @DisplayName("GET /api/notifications — odpowiada application/json")
    void responseContentType_isJson() throws Exception {
        when(notificationService.getAllNotifications()).thenReturn(List.of());

        mockMvc.perform(get("/api/notifications").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}
