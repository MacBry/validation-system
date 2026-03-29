package com.mac.bry.validationsystem.wizard;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for ValidationPlanData (PERIODIC_REVALIDATION planning phase).
 *
 * Provides database access for validation plans created during the planning phase
 * (steps 1-8) of PERIODIC_REVALIDATION procedure type.
 */
@Repository
public interface ValidationPlanDataRepository extends JpaRepository<ValidationPlanData, Long> {

    /**
     * Find plan data associated with a validation draft.
     * Since the FK (plan_data_id) lives on validation_drafts, we query via JPQL.
     *
     * @param draftId ID of the ValidationDraft
     * @return Optional containing the plan data if found
     */
    @Query("SELECT p FROM ValidationPlanData p " +
           "INNER JOIN ValidationDraft d ON d.planData.id = p.id " +
           "WHERE d.id = :draftId")
    Optional<ValidationPlanData> findByValidationDraftId(@Param("draftId") Long draftId);
}
