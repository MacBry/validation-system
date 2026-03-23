package com.mac.bry.validationsystem.company;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;

/**
 * Encja reprezentująca firmę (organizację)
 * Przykład: RCKiK w Poznaniu
 */
@Entity
@Table(name = "companies")
@Audited
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Company {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Nazwa firmy
     * Przykład: "Regionalne Centrum Krwiodawstwa i Krwiolecznictwa w Poznaniu"
     */
    @Column(name = "name", nullable = false, length = 255)
    private String name;
    
    /**
     * Adres siedziby firmy
     * Przykład: "ul. Marcelińska 44, 60-354 Poznań"
     */
    @Column(name = "address", length = 500)
    private String address;
    
    /**
     * Data utworzenia rekordu w systemie
     */
    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;
    
    @PrePersist
    protected void onCreate() {
        if (createdDate == null) {
            createdDate = LocalDateTime.now();
        }
    }
}
