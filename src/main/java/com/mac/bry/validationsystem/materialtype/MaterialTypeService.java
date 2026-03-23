package com.mac.bry.validationsystem.materialtype;

import java.util.List;
import java.util.Optional;

public interface MaterialTypeService {

    /**
     * Pobiera wszystkie materiały widoczne dla danych firm (oraz globalne)
     */
    List<MaterialType> findAll(java.util.Set<Long> allowedCompanyIds);

    /**
     * Pobiera wszystkie aktywne materiały widoczne dla danych firm
     */
    List<MaterialType> findAllActive(java.util.Set<Long> allowedCompanyIds);

    /**
     * Pobiera materiał po ID
     */
    Optional<MaterialType> findById(Long id);

    /**
     * Zapisuje materiał
     */
    MaterialType save(MaterialType material);

    /**
     * Usuwa materiał
     */
    void deleteById(Long id);

    /**
     * Sprawdza czy materiał o danej nazwie istnieje w firmie lub globalnie
     */
    boolean existsByName(String name, Long companyId);

    /**
     * Pobiera wszystkie podstawowe materiały (globalne i dla wszystkich firm)
     * Używane tylko przez Super Admina lub do celów administracyjnych.
     */
    List<MaterialType> findAllAdmin();
}
