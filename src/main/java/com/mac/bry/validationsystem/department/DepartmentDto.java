package com.mac.bry.validationsystem.department;

/**
 * DTO dla działu
 */
public record DepartmentDto(
    Long id,
    Long companyId,
    String companyName,
    String name,
    String abbreviation,
    String description,
    Boolean hasLaboratories,
    Integer laboratoriesCount
) {
    /**
     * Konwertuje encję Department na DTO
     */
    public static DepartmentDto fromEntity(Department department) {
        if (department == null) {
            return null;
        }
        
        return new DepartmentDto(
            department.getId(),
            department.getCompany() != null ? department.getCompany().getId() : null,
            department.getCompany() != null ? department.getCompany().getName() : null,
            department.getName(),
            department.getAbbreviation(),
            department.getDescription(),
            department.getHasLaboratories(),
            null // Będzie uzupełniane w razie potrzeby przez serwis
        );
    }
    
    /**
     * Konstruktor dla formularza (tworzenie/edycja)
     */
    public static DepartmentDto forForm(Long companyId, String name, String abbreviation, 
                                        String description, Boolean hasLaboratories) {
        return new DepartmentDto(
            null,
            companyId,
            null,
            name,
            abbreviation,
            description,
            hasLaboratories,
            null
        );
    }
}
