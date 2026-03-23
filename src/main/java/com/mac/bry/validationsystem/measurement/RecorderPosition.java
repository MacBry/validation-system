package com.mac.bry.validationsystem.measurement;

/**
 * Enum opisujący umiejscowienie rejestratora temperatury w urządzeniu chłodniczym.
 * Siatka 3×3×3: 3 poziomy (Góra/Środek/Dół) × 3 głębokości (Tył/Środek/Przód) × 3 kolumny (Lewy/Środek/Prawy).
 * Klucze dokumentu (G1–G9, S1–S9, D1–D9) odpowiadają placeholderom w szablonie Schemat_Wizualny.
 *
 * Układ współrzędnych:
 *   x: 1=Lewy, 2=Środek, 3=Prawy
 *   y: 1=Przód, 2=Środek, 3=Tył
 *   z: 1=Dół,  2=Środek, 3=Góra
 */
public enum RecorderPosition {

    // ── Góra / Sufit ─────────────────────────────────────────────────────────
    TOP_REAR_LEFT    ("G1 – Lewy Tył",    "G1", 1, 3, 3),
    TOP_REAR_CENTER  ("G2 – Środek Tył",  "G2", 2, 3, 3),
    TOP_REAR_RIGHT   ("G3 – Prawy Tył",   "G3", 3, 3, 3),
    TOP_CENTER_LEFT  ("G4 – Lewy Środek", "G4", 1, 2, 3),
    TOP_CENTER_CENTER("G5 – Środek",      "G5", 2, 2, 3),
    TOP_CENTER_RIGHT ("G6 – Prawy Środek","G6", 3, 2, 3),
    TOP_FRONT_LEFT   ("G7 – Lewy Przód",  "G7", 1, 1, 3),
    TOP_FRONT_CENTER ("G8 – Środek Przód","G8", 2, 1, 3),
    TOP_FRONT_RIGHT  ("G9 – Prawy Przód", "G9", 3, 1, 3),

    // ── Środek ───────────────────────────────────────────────────────────────
    MIDDLE_REAR_LEFT    ("S1 – Lewy Tył",    "S1", 1, 3, 2),
    MIDDLE_REAR_CENTER  ("S2 – Środek Tył",  "S2", 2, 3, 2),
    MIDDLE_REAR_RIGHT   ("S3 – Prawy Tył",   "S3", 3, 3, 2),
    MIDDLE_CENTER_LEFT  ("S4 – Lewy Środek", "S4", 1, 2, 2),
    MIDDLE_CENTER_CENTER("S5 – Środek",      "S5", 2, 2, 2),
    MIDDLE_CENTER_RIGHT ("S6 – Prawy Środek","S6", 3, 2, 2),
    MIDDLE_FRONT_LEFT   ("S7 – Lewy Przód",  "S7", 1, 1, 2),
    MIDDLE_FRONT_CENTER ("S8 – Środek Przód","S8", 2, 1, 2),
    MIDDLE_FRONT_RIGHT  ("S9 – Prawy Przód", "S9", 3, 1, 2),

    // ── Dół / Podłoga ────────────────────────────────────────────────────────
    BOTTOM_REAR_LEFT    ("D1 – Lewy Tył",    "D1", 1, 3, 1),
    BOTTOM_REAR_CENTER  ("D2 – Środek Tył",  "D2", 2, 3, 1),
    BOTTOM_REAR_RIGHT   ("D3 – Prawy Tył",   "D3", 3, 3, 1),
    BOTTOM_CENTER_LEFT  ("D4 – Lewy Środek", "D4", 1, 2, 1),
    BOTTOM_CENTER_CENTER("D5 – Środek",      "D5", 2, 2, 1),
    BOTTOM_CENTER_RIGHT ("D6 – Prawy Środek","D6", 3, 2, 1),
    BOTTOM_FRONT_LEFT   ("D7 – Lewy Przód",  "D7", 1, 1, 1),
    BOTTOM_FRONT_CENTER ("D8 – Środek Przód","D8", 2, 1, 1),
    BOTTOM_FRONT_RIGHT  ("D9 – Prawy Przód", "D9", 3, 1, 1);

    private final String displayName;
    private final String documentKey;
    private final int x; // 1=Lewy, 2=Środek, 3=Prawy
    private final int y; // 1=Przód, 2=Środek, 3=Tył
    private final int z; // 1=Dół,  2=Środek, 3=Góra

    RecorderPosition(String displayName, String documentKey, int x, int y, int z) {
        this.displayName = displayName;
        this.documentKey = documentKey;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String getDisplayName()  { return displayName; }
    public String getDocumentKey()  { return documentKey; }
    public int getX()               { return x; }
    public int getY()               { return y; }
    public int getZ()               { return z; }
}
