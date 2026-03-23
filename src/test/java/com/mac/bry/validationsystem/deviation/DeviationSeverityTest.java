package com.mac.bry.validationsystem.deviation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeviationSeverityTest {

    @ParameterizedTest
    @DisplayName("Should classify severity correctly based on duration and delta temp")
    @CsvSource({
            "60, 0.1, CRITICAL",  // Duration >= 60 min
            "59, 5.0, CRITICAL",  // Delta >= 5.0 C
            "61, 6.0, CRITICAL",  // Both
            "30, 0.1, MAJOR",     // Duration 30-59 min
            "29, 2.0, MAJOR",     // Delta 2.0-4.9 C
            "59, 4.9, MAJOR",     // Both in Major range
            "29, 1.9, MINOR",     // Duration < 30 AND Delta < 2.0
            "1, 0.1, MINOR"       // Minimal values
    })
    void shouldClassifyCorrectly(long duration, double delta, DeviationSeverity expected) {
        assertEquals(expected, DeviationSeverity.classify(duration, delta));
    }

    @Test
    @DisplayName("Should return correct display names")
    void shouldReturnDisplayNames() {
        assertEquals("Krytyczne", DeviationSeverity.CRITICAL.getDisplayName());
        assertEquals("Większe", DeviationSeverity.MAJOR.getDisplayName());
        assertEquals("Mniejsze", DeviationSeverity.MINOR.getDisplayName());
    }
}
