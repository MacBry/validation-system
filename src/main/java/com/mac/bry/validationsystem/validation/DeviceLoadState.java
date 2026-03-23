package com.mac.bry.validationsystem.validation;

/**
 * Stan załadowania urządzenia podczas walidacji.
 *
 * <p>
 * Informacja o stanie urządzenia ma istotny wpływ na rozkład temperatur
 * i jest wymagana w protokołach walidacji zgodnie z GMP/GDP.
 * </p>
 */
public enum DeviceLoadState {

    /**
     * Urządzenie pełne (załadowane produktem/materiałem)
     * <p>
     * Symuluje rzeczywiste warunki pracy z pełnym obciążeniem termicznym.
     * Zalecany stan dla walidacji operacyjnej.
     * </p>
     */
    FULL("Pełne", "Urządzenie załadowane produktem/materiałem - symuluje rzeczywiste warunki pracy"),

    /**
     * Urządzenie puste (bez produktu/materiału)
     * <p>
     * Walidacja w najlepszych możliwych warunkach bez obciążenia termicznego.
     * Używany dla walidacji instalacyjnej i kwalifikacyjnej.
     * </p>
     */
    EMPTY("Puste", "Urządzenie bez produktu/materiału - warunki bez obciążenia termicznego"),

    /**
     * Urządzenie częściowo załadowane
     * <p>
     * Stan pośredni - część pojemności zajęta produktem.
     * Używany do testowania różnych scenariuszy załadowania.
     * </p>
     */
    PARTIALLY_LOADED("Częściowo załadowane", "Część pojemności zajęta produktem - warunki pośrednie");

    private final String displayName;
    private final String description;

    DeviceLoadState(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Zwraca pełny opis stanu dla dokumentacji
     */
    public String getFullDescription() {
        return displayName + " - " + description;
    }

    /**
     * Sprawdza czy stan reprezentuje pełne lub częściowe załadowanie
     */
    public boolean isLoaded() {
        return this == FULL || this == PARTIALLY_LOADED;
    }

    /**
     * Zwraca emoji reprezentujące stan załadowania
     */
    public String getStateIcon() {
        switch (this) {
            case FULL:
                return "🔴"; // pełne - czerwone kółko
            case EMPTY:
                return "⚪"; // puste - białe kółko
            case PARTIALLY_LOADED:
                return "🟡"; // częściowe - żółte kółko
            default:
                return "❓";
        }
    }
}