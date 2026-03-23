package com.mac.bry.validationsystem.deviation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeviationEventRepository extends JpaRepository<DeviationEvent, Long> {

    List<DeviationEvent> findByValidationIdOrderBySeverityAscStartTimeAsc(Long validationId);

    @Query("SELECT COUNT(e) FROM DeviationEvent e WHERE e.validation.id = :validationId")
    long countByValidationId(Long validationId);

    @Query("SELECT COUNT(e) FROM DeviationEvent e WHERE e.validation.id = :validationId AND e.severity = :severity")
    long countByValidationIdAndSeverity(Long validationId, DeviationSeverity severity);

    void deleteByValidationId(Long validationId);
}
