package com.mac.bry.validationsystem.wizard.pq;

import com.mac.bry.validationsystem.wizard.ValidationDraft;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing PQ checklist items in wizard step 5.
 *
 * <p>
 * PQ (Performance Qualification) checklist contains 10 standard items (PQ-01..PQ-10)
 * that verify the cooling device meets performance requirements.
 * </p>
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PqChecklistService {

    private final PqChecklistItemRepository pqChecklistItemRepository;

    /**
     * Default PQ checklist items (PQ-01..PQ-10)
     */
    private static final String[] DEFAULT_PQ_ITEMS = {
        "PQ-01|Urządzenie osiąga i utrzymuje temperaturę nominalną w warunkach obciążenia",
        "PQ-02|Rozkład temperatur w całej objętości nie przekracza ±2°C od średniej",
        "PQ-03|Wszystkie czujniki temperatury działają prawidłowo",
        "PQ-04|System kontroli temperatury działa prawidłowo",
        "PQ-05|Procent zgodności z limitami temperaturowymi wynosi ≥95%",
        "PQ-06|Żaden pojedynczy pomiar nie przekracza MKT o więcej niż 5°C",
        "PQ-07|Drzwi urządzenia zamykają się i otwierają prawidłowo",
        "PQ-08|Brak wycieku chłodziwa",
        "PQ-09|Wyświetlacz temperatury jest czytelny i dokładny",
        "PQ-10|Urządzenie spełnia wszystkie wymagania normatywne"
    };

    /**
     * Initialize default PQ checklist for a draft (idempotent)
     * Creates 10 standard PQ items if they don't already exist
     * @param draftId Draft ID
     * @param createdBy Username
     */
    public void initializeDefaultChecklist(Long draftId, String createdBy) {
        log.info("Initializing default PQ checklist for draft: {}", draftId);

        // Check if items already exist (idempotent)
        long existingCount = pqChecklistItemRepository.countByValidationDraftId(draftId);
        if (existingCount > 0) {
            log.debug("PQ checklist already exists for draft: {}, skipping initialization", draftId);
            return;
        }

        ValidationDraft draft = new ValidationDraft();
        draft.setId(draftId);

        List<PqChecklistItem> items = new ArrayList<>();
        for (int i = 0; i < DEFAULT_PQ_ITEMS.length; i++) {
            String[] parts = DEFAULT_PQ_ITEMS[i].split("\\|");
            String itemCode = parts[0];
            String description = parts[1];

            PqChecklistItem item = PqChecklistItem.builder()
                .validationDraft(draft)
                .itemCode(itemCode)
                .itemDescription(description)
                .displayOrder(i)
                .createdBy(createdBy)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

            items.add(item);
        }

        pqChecklistItemRepository.saveAll(items);
        log.info("Created {} default PQ checklist items for draft: {}", items.size(), draftId);
    }

    /**
     * Find all PQ items for a draft
     */
    public List<PqChecklistItem> findByDraftId(Long draftId) {
        return pqChecklistItemRepository.findByValidationDraftIdOrderByDisplayOrder(draftId);
    }

    /**
     * Find a specific PQ item by code
     */
    public Optional<PqChecklistItem> findByDraftIdAndItemCode(Long draftId, String itemCode) {
        return pqChecklistItemRepository.findByValidationDraftIdAndItemCode(draftId, itemCode);
    }

    /**
     * Save answer to a PQ checklist item
     */
    public PqChecklistItem saveAnswer(Long itemId, Boolean passed, String comment, String username) {
        log.debug("Saving answer to PQ item: {}", itemId);

        PqChecklistItem item = pqChecklistItemRepository.findById(itemId)
            .orElseThrow(() -> new IllegalArgumentException("PQ item not found: " + itemId));

        item.setPassed(passed);
        item.setComment(comment);
        item.setCreatedBy(username);
        item.setUpdatedAt(LocalDateTime.now());

        return pqChecklistItemRepository.save(item);
    }

    /**
     * Check if all PQ items have been assessed
     */
    public boolean areAllItemsAssessed(Long draftId) {
        return !pqChecklistItemRepository.existsByValidationDraftIdAndPassedIsNull(draftId);
    }

    /**
     * Get count of passed items
     */
    public long getPassedItemCount(Long draftId) {
        return pqChecklistItemRepository.countByValidationDraftIdAndPassedTrue(draftId);
    }

    /**
     * Get total count of items
     */
    public long getTotalItemCount(Long draftId) {
        return pqChecklistItemRepository.countByValidationDraftId(draftId);
    }

    /**
     * Get pass percentage
     */
    public double getPassPercentage(Long draftId) {
        long total = getTotalItemCount(draftId);
        if (total == 0) {
            return 0;
        }
        long passed = getPassedItemCount(draftId);
        return (passed * 100.0) / total;
    }

    /**
     * Check if all items have passed
     */
    public boolean haveAllItemsPassed(Long draftId) {
        long total = getTotalItemCount(draftId);
        long passed = getPassedItemCount(draftId);
        return total > 0 && passed == total;
    }
}
