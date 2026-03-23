package com.mac.bry.validationsystem.company;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementacja serwisu do zarządzania firmami
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {

    private final CompanyRepository companyRepository;

    @Override
    @Transactional(readOnly = true)
    public Company getMainCompany() {
        log.debug("Pobieranie głównej firmy (RCKiK w Poznaniu)");

        // Pobierz pierwszą firmę z bazy (powinna być tylko jedna - RCKiK)
        return companyRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Nie znaleziono żadnej firmy w systemie! Czy wykonano migrację v2.11.0?"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Company> getAllCompanies() {
        log.debug("Pobieranie wszystkich firm");
        return companyRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Company> getAllowedCompanies(java.util.Set<Long> allowedIds) {
        log.debug("Pobieranie dozwolonych firm dla: {}", allowedIds);
        if (allowedIds == null) {
            return companyRepository.findAll();
        }
        return companyRepository.findAllById(allowedIds);
    }

    @Override
    @Transactional(readOnly = true)
    public Company getCompanyById(Long id) {
        log.debug("Pobieranie firmy o ID: {}", id);
        return companyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Nie znaleziono firmy o ID: " + id));
    }

    @Override
    @Transactional
    public Company createCompany(String name, String address) {
        log.debug("Tworzenie nowej firmy: {}", name);

        Company company = new Company();
        company.setName(name);
        company.setAddress(address);

        return companyRepository.save(company);
    }

    @Override
    @Transactional
    public Company updateCompany(Long id, String name, String address) {
        log.debug("Aktualizacja firmy: {}", id);

        Company company = getCompanyById(id);
        company.setName(name);
        company.setAddress(address);

        return companyRepository.save(company);
    }

    @Override
    @Transactional
    public void deleteCompany(Long id) {
        log.debug("Usuwanie firmy: {}", id);

        Company company = getCompanyById(id);
        companyRepository.delete(company);
    }
}
