package com.mac.bry.validationsystem.security;

import com.mac.bry.validationsystem.company.Company;
import com.mac.bry.validationsystem.department.Department;
import com.mac.bry.validationsystem.laboratory.Laboratory;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_permissions")
@Audited
@Getter
@Setter
public class UserPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Zależność ucięta tylko do ID, aby uniknąć zbędnego ładowania usera jeśli nie
    // trzeba
    // lub ładowanie EAGER jeśli zawsze potrzebne. Ustawiamy obiekt ze sprzężeniem
    // LAZY.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission_type", nullable = false)
    private PermissionType permissionType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "laboratory_id")
    private Laboratory laboratory;

    @Column(name = "granted_by", nullable = true)
    private Long grantedBy;

    @Column(name = "granted_date", nullable = false, updatable = false)
    private LocalDateTime grantedDate;

    @Column(name = "expires_date")
    private LocalDateTime expiresDate;

    @PrePersist
    protected void onCreate() {
        if (grantedDate == null) {
            grantedDate = LocalDateTime.now();
        }
    }
}
