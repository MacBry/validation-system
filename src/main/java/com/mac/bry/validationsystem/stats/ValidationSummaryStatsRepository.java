package com.mac.bry.validationsystem.stats;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repozytorium dla {@link ValidationSummaryStats}.
 *
 * <p>
 * Zapewnia dostęp do statystyk zbiorczych przez ID walidacji lub ID encji.
 * </p>
 */
@Repository
public interface ValidationSummaryStatsRepository
        extends JpaRepository<ValidationSummaryStats, Long> {

    /**
     * Pobiera statystyki zbiorcze dla konkretnej walidacji.
     *
     * @param validationId ID walidacji
     * @return Optional z encją statystyk lub empty jeśli jeszcze nie obliczono
     */
    @Query("SELECT vss FROM ValidationSummaryStats vss " +
            "WHERE vss.validation.id = :validationId")
    Optional<ValidationSummaryStats> findByValidationId(@Param("validationId") Long validationId);

    /**
     * Sprawdza czy statystyki dla danej walidacji już istnieją.
     *
     * @param validationId ID walidacji
     * @return true jeśli rekord istnieje
     */
    boolean existsByValidationId(Long validationId);
}
