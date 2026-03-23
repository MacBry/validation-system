package com.mac.bry.validationsystem.company;

import java.time.LocalDateTime;

/**
 * DTO dla firmy
 */
public record CompanyDto(
    Long id,
    String name,
    String address,
    LocalDateTime createdDate
) {
    /**
     * Konwertuje encję Company na DTO
     */
    public static CompanyDto fromEntity(Company company) {
        if (company == null) {
            return null;
        }
        
        return new CompanyDto(
            company.getId(),
            company.getName(),
            company.getAddress(),
            company.getCreatedDate()
        );
    }
}
