package com.mac.bry.validationsystem.notification;

/**
 * DTO dla pojedynczego powiadomienia na dashboardzie.
 *
 * @param type     typ: "VALIDATION", "CALIBRATION", "PASSWORD", "ADMIN_PASSWORD"
 * @param title    nagłówek powiadomienia
 * @param message  treść powiadomienia
 * @param link     link do powiązanego zasobu (null jeśli brak)
 * @param daysLeft liczba dni do zdarzenia
 * @param severity "warning" (żółty) lub "danger" (czerwony)
 */
public record NotificationDTO(
        String type,
        String title,
        String message,
        String link,
        int daysLeft,
        String severity
) {
    /** Severity na podstawie progu dni */
    public static String severityFor(int daysLeft, int dangerThreshold) {
        return daysLeft <= dangerThreshold ? "danger" : "warning";
    }
}
