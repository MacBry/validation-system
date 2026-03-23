package com.mac.bry.validationsystem.wizard.pdf;

import com.mac.bry.validationsystem.wizard.ValidationProcedureType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for selecting the appropriate ValidationProcedureStrategy.
 *
 * <p>
 * Maps ValidationProcedureType to its strategy implementation.
 * </p>
 */
@Component
@RequiredArgsConstructor
public class ProcedureStrategyFactory {

    private final OqProcedureStrategy oqStrategy;
    private final PqProcedureStrategy pqStrategy;
    private final MappingProcedureStrategy mappingStrategy;

    /**
     * Get the strategy for a given procedure type
     */
    public ValidationProcedureStrategy getStrategy(ValidationProcedureType procedureType) {
        switch (procedureType) {
            case OQ:
                return oqStrategy;
            case PQ:
                return pqStrategy;
            case MAPPING:
                return mappingStrategy;
            default:
                throw new IllegalArgumentException("Unknown procedure type: " + procedureType);
        }
    }

    /**
     * Get a map of all available strategies
     */
    public Map<ValidationProcedureType, ValidationProcedureStrategy> getAll() {
        Map<ValidationProcedureType, ValidationProcedureStrategy> strategies = new HashMap<>();
        strategies.put(ValidationProcedureType.OQ, oqStrategy);
        strategies.put(ValidationProcedureType.PQ, pqStrategy);
        strategies.put(ValidationProcedureType.MAPPING, mappingStrategy);
        return strategies;
    }
}
