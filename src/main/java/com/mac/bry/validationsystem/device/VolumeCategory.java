package com.mac.bry.validationsystem.device;

/**
 * Klasyfikacja kubatury urządzeń chłodniczych według PDA TR-64 i praktyki WHO.
 *
 * <p>
 * Klasy kubatury determinują minimalne wymagania dotyczące liczby punktów
 * pomiarowych w walidacji przestrzennej urządzenia.
 * </p>
 *
 * @see <a href="https://www.pda.org/global/technical-resources/technical-reports">PDA TR-64</a>
 * @see <a href="https://www.who.int/publications/m/item/annex-5-good-storage-practices">WHO Good Storage Practices</a>
 */
public enum VolumeCategory {

    /**
     * Klasa S (Small) - do ~2 m³
     * <p>
     * <b>Zastosowania:</b> lodówki apteczne, laboratoryjne, małe inkubatory<br>
     * <b>Minimalne punkty pomiarowe:</b> 9 (narożniki + środek na 3 poziomach)<br>
     * <b>Uzasadnienie:</b> mała objętość zapewnia względną jednorodność temperatur
     * </p>
     */
    SMALL("Klasa S (≤ 2 m³)", "Lodówki apteczne, laboratoryjne, małe inkubatory", 2.0, 9),

    /**
     * Klasa M (Medium) - ~2–20 m³
     * <p>
     * <b>Zastosowania:</b> szafy chłodnicze walk-in, duże lodówki przemysłowe, małe komory chłodnicze<br>
     * <b>Minimalne punkty pomiarowe:</b> 15–18 (rozszerzona siatka)<br>
     * <b>Uzasadnienie:</b> większa objętość może powodować zróżnicowane strefy termiczne
     * </p>
     */
    MEDIUM("Klasa M (2–20 m³)", "Szafy chłodnicze walk-in, duże lodówki przemysłowe", 20.0, 15),

    /**
     * Klasa L (Large) - powyżej 20 m³
     * <p>
     * <b>Zastosowania:</b> komory chłodnicze, magazyny, chłodnie, kontenery<br>
     * <b>Minimalne punkty pomiarowe:</b> 27+ (pełna siatka 3×3×3 lub więcej)<br>
     * <b>Uzasadnienie:</b> duża objętość wymaga szczegółowej analizy przestrzennej
     * </p>
     */
    LARGE("Klasa L (> 20 m³)", "Komory chłodnicze, magazyny, chłodnie, kontenery", Double.MAX_VALUE, 27);

    private final String displayName;
    private final String description;
    private final double maxVolume;
    private final int minMeasurementPoints;

    VolumeCategory(String displayName, String description, double maxVolume, int minMeasurementPoints) {
        this.displayName = displayName;
        this.description = description;
        this.maxVolume = maxVolume;
        this.minMeasurementPoints = minMeasurementPoints;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public double getMaxVolume() {
        return maxVolume;
    }

    public int getMinMeasurementPoints() {
        return minMeasurementPoints;
    }

    /**
     * Automatycznie określa klasę kubatury na podstawie objętości urządzenia.
     *
     * @param volume objętość w m³
     * @return odpowiednia klasa kubatury
     */
    public static VolumeCategory fromVolume(double volume) {
        if (volume <= SMALL.maxVolume) {
            return SMALL;
        } else if (volume <= MEDIUM.maxVolume) {
            return MEDIUM;
        } else {
            return LARGE;
        }
    }

    /**
     * Sprawdza czy dana liczba punktów pomiarowych jest zgodna z klasą kubatury.
     *
     * @param measurementPoints liczba planowanych punktów pomiarowych
     * @return true jeśli liczba punktów jest wystarczająca, false w przeciwnym przypadku
     */
    public boolean isValidMeasurementPoints(int measurementPoints) {
        return measurementPoints >= this.minMeasurementPoints;
    }

    /**
     * Zwraca szczegółowy opis wymagań dla klasy kubatury.
     */
    public String getValidationRequirements() {
        return String.format(
            "%s: %s (min. %d punktów pomiarowych)",
            displayName, description, minMeasurementPoints
        );
    }
}