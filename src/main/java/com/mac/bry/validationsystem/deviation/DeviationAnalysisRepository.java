package com.mac.bry.validationsystem.deviation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeviationAnalysisRepository extends JpaRepository<DeviationAnalysis, Long> {

    Optional<DeviationAnalysis> findByDeviationEventId(Long deviationEventId);
}
