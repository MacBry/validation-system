package com.mac.bry.validationsystem.audit;

import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Serwis do pobierania i porównywania rewizji Envers.
 *
 * Główna metoda: getDetailedDiff() — zwraca listę FieldDiffDto dla wszystkich
 * pól,
 * które zmieniły się między rewizją N-1 a N.
 *
 * GMP Annex 11 §10: pełna historia zmian z wartościami przed/po.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class EnversRevisionService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Pobiera listę wszystkich rewizji dla danej encji.
     *
     * @param entityClass klasa encji (np. CoolingDevice.class)
     * @param entityId    ID encji
     * @return lista RevisionInfoDto (metadane każdej rewizji)
     */
    @SuppressWarnings("unchecked")
    public List<RevisionInfoDto> getRevisionHistory(Class<?> entityClass, Long entityId) {
        AuditReader reader = AuditReaderFactory.get(entityManager);
        List<Object[]> revisions;
        try {
            revisions = (List<Object[]>) reader.createQuery()
                    .forRevisionsOfEntity(entityClass, false, true)
                    .add(AuditEntity.id().eq(entityId))
                    .addOrder(AuditEntity.revisionNumber().desc())
                    .getResultList();
        } catch (Exception e) {
            log.warn("Błąd pobierania historii rewizji dla {} ID={}: {}", entityClass.getSimpleName(), entityId,
                    e.getMessage());
            return Collections.emptyList();
        }

        List<RevisionInfoDto> result = new ArrayList<>();
        for (Object[] row : revisions) {
            // row[0] = encja, row[1] = SystemRevisionEntity, row[2] = RevisionType
            if (row[1] instanceof SystemRevisionEntity rev) {
                RevisionType revType = (RevisionType) row[2];
                result.add(RevisionInfoDto.from(rev, revType));
            }
        }
        return result;
    }

    /**
     * Porównuje rewizję N z rewizją N-1 i zwraca listę zmian pól.
     *
     * @param entityClass klasa encji
     * @param entityId    ID encji
     * @param revNumber   numer rewizji do porównania (z poprzednią)
     * @param fieldLabels mapa: fieldName -> displayName (np. "name" -> "Nazwa
     *                    urządzenia")
     * @return lista FieldDiffDto — wszystkie pola z wartościami przed/po
     */
    public List<FieldDiffDto> getDetailedDiff(Class<?> entityClass, Long entityId, int revNumber,
            Map<String, String> fieldLabels) {
        AuditReader reader = AuditReaderFactory.get(entityManager);

        // Pobierz stan encji w rewizji N
        Object currentState = null;
        Object previousState = null;

        try {
            currentState = reader.find(entityClass, entityId, revNumber);
        } catch (Exception e) {
            log.warn("Nie można pobrać rewizji {} dla {} ID={}", revNumber, entityClass.getSimpleName(), entityId);
        }

        // Znajdź poprzednią rewizję (poprzedni numer rewizji tej encji)
        try {
            @SuppressWarnings("unchecked")
            List<Number> revNumbers = (List<Number>) reader.getRevisions(entityClass, entityId);
            int currentIndex = -1;
            for (int i = 0; i < revNumbers.size(); i++) {
                if (revNumbers.get(i).intValue() == revNumber) {
                    currentIndex = i;
                    break;
                }
            }
            if (currentIndex > 0) {
                int prevRevNumber = revNumbers.get(currentIndex - 1).intValue();
                previousState = reader.find(entityClass, entityId, prevRevNumber);
            }
        } catch (Exception e) {
            log.debug("Nie ma poprzedniej rewizji dla {} ID={} rev={}", entityClass.getSimpleName(), entityId,
                    revNumber);
        }

        return buildDiff(currentState, previousState, fieldLabels);
    }

    /**
     * Buduje listę FieldDiffDto przez refleksję na polach encji.
     */
    private List<FieldDiffDto> buildDiff(Object current, Object previous, Map<String, String> fieldLabels) {
        List<FieldDiffDto> diffs = new ArrayList<>();

        if (current == null) {
            return diffs;
        }

        for (Map.Entry<String, String> entry : fieldLabels.entrySet()) {
            String fieldName = entry.getKey();
            String displayName = entry.getValue();

            String currentValue = extractFieldValue(current, fieldName);
            String previousValue = previous != null ? extractFieldValue(previous, fieldName) : null;

            boolean changed = !Objects.equals(currentValue, previousValue);

            diffs.add(FieldDiffDto.builder()
                    .fieldName(fieldName)
                    .displayName(displayName)
                    .oldValue(previousValue)
                    .newValue(currentValue)
                    .changed(changed)
                    .build());
        }

        return diffs;
    }

    /**
     * Pobiera wartość pola przez refleksję, obsługując relacje (ManyToOne) przez
     * toString() lub getId().
     */
    private String extractFieldValue(Object entity, String fieldName) {
        try {
            // Obsługa zagnieżdżonych pól (np. "department.name")
            if (fieldName.contains(".")) {
                String[] parts = fieldName.split("\\.", 2);
                Object nested = getField(entity, parts[0]);
                return nested != null ? extractFieldValue(nested, parts[1]) : null;
            }
            Object value = getField(entity, fieldName);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.debug("Nie można pobrać pola {} z {}: {}", fieldName, entity.getClass().getSimpleName(),
                    e.getMessage());
            return null;
        }
    }

    private Object getField(Object obj, String fieldName) throws Exception {
        Class<?> clazz = obj.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(obj);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }
}
