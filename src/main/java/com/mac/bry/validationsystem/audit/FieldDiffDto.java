package com.mac.bry.validationsystem.audit;

import lombok.Builder;
import lombok.Value;

/**
 * DTO reprezentujący zmianę pojedynczego pola encji między dwiema rewizjami.
 *
 * Używany przez widok Envers do prezentacji tabeli: Pole | Przed | Po
 *
 * GMP Annex 11 §10: każda zmiana musi być widoczna z pełnymi danymi przed i po.
 */
@Value
@Builder
public class FieldDiffDto {

    /** Nazwa techniczna pola (np. "name") */
    String fieldName;

    /** Czytelna dla użytkownika etykieta (np. "Nazwa urządzenia") */
    String displayName;

    /** Wartość pola przed zmianą (null = brak poprzedniej rewizji) */
    String oldValue;

    /** Wartość pola po zmianie */
    String newValue;

    /** Czy wartość faktycznie się zmieniła (false = pole bez zmian) */
    boolean changed;
}
