package com.mac.bry.validationsystem.materialtype;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MaterialTypeServiceImpl implements MaterialTypeService {

    private final MaterialTypeRepository repository;

    @Override
    public List<MaterialType> findAll(Set<Long> allowedCompanyIds) {
        log.debug("Pobieranie materiałów dla firm: {}", allowedCompanyIds);
        if (allowedCompanyIds == null) {
            return repository.findAllByOrderByNameAsc();
        }
        if (allowedCompanyIds.isEmpty()) {
            return repository.findAllByCompanyIdInOrCompanyIsNullOrderByNameAsc(Collections.emptyList());
        }
        return repository.findAllByCompanyIdInOrCompanyIsNullOrderByNameAsc(allowedCompanyIds);
    }

    @Override
    public List<MaterialType> findAllActive(Set<Long> allowedCompanyIds) {
        log.debug("Pobieranie aktywnych materiałów dla firm: {}", allowedCompanyIds);
        if (allowedCompanyIds == null) {
            return repository.findByActiveTrueAndCompanyIdInOrActiveTrueAndCompanyIsNullOrderByNameAsc(null);
        }
        return repository.findByActiveTrueAndCompanyIdInOrActiveTrueAndCompanyIsNullOrderByNameAsc(allowedCompanyIds);
    }

    @Override
    public Optional<MaterialType> findById(Long id) {
        log.debug("Pobieranie materiału o id: {}", id);
        return repository.findById(id);
    }

    @Override
    public List<MaterialType> findAllAdmin() {
        return repository.findAllByOrderByNameAsc();
    }

    @Override
    @Transactional
    public MaterialType save(MaterialType material) {
        log.debug("Zapisywanie materiału: {}", material);
        return repository.save(material);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        log.debug("Usuwanie materiału o id: {}", id);
        repository.deleteById(id);
    }

    @Override
    public boolean existsByName(String name, Long companyId) {
        if (companyId == null) {
            return repository.existsByNameAndCompanyIsNull(name);
        }
        return repository.existsByNameAndCompanyId(name, companyId) || repository.existsByNameAndCompanyIsNull(name);
    }
}
