package com.mac.bry.validationsystem.company;

import java.util.List;

/**
 * Serwis do zarządzania firmami
 */
public interface CompanyService {

    /**
     * Pobiera główną firmę (RCKiK w Poznaniu)
     */
    Company getMainCompany();

    /**
     * Pobiera wszystkie firmy
     */
    List<Company> getAllCompanies();

    /**
     * Pobiera firmy na podstawie listy identyfikatorów
     */
    List<Company> getAllowedCompanies(java.util.Set<Long> allowedIds);

    /**
     * Pobiera firmę według ID
     */
    Company getCompanyById(Long id);

    /**
     * Tworzy nową firmę
     */
    Company createCompany(String name, String address);

    /**
     * Aktualizuje firmę
     */
    Company updateCompany(Long id, String name, String address);

    /**
     * Usuwa firmę
     */
    void deleteCompany(Long id);
}
