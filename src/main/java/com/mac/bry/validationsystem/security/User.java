package com.mac.bry.validationsystem.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "users")
@Audited
@Getter
@Setter
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private boolean locked = false;

    @Column(name = "account_expired", nullable = false)
    private boolean accountExpired = false;

    @Column(name = "credentials_expired", nullable = false)
    private boolean credentialsExpired = false;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(length = 20)
    private String phone;

    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @Column(name = "updated_date")
    private LocalDateTime updatedDate;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @Column(name = "password_expires_at")
    private LocalDateTime passwordExpiresAt;

    @Column(name = "password_expiry_days")
    private Integer passwordExpiryDays = 90; // 90 dni domyślnie

    @Column(name = "created_by")
    private Long createdBy;

    // Relacja Many-To-Many dla głównych ról (RBAC)
    // @NotAudited — Role nie jest @Audited; zmiany ról śledzone przez AuditLog
    @NotAudited
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    // ENTERPRISE FIX #1: Cache uprawnień
    // @NotAudited — pochodna cache, nie dane biznesowe; uprawnienia śledzone przez UserPermission_AUD
    @NotAudited
    @Column(name = "permissions_cache_json", columnDefinition = "json")
    private String permissionsCacheJson;

    @Transient
    private UserPermissionsCache permissionsCache;

    @PrePersist
    protected void onCreate() {
        if (createdDate == null) {
            createdDate = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedDate = LocalDateTime.now();
    }

    @PostLoad
    public void deserializePermissionsCache() {
        if (permissionsCacheJson != null && !permissionsCacheJson.isEmpty()) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                mapper.findAndRegisterModules(); // dla LocalDate/Time
                this.permissionsCache = mapper.readValue(permissionsCacheJson, UserPermissionsCache.class);
            } catch (JsonProcessingException e) {
                // Ignore błąd bazy lub loguj, ale nie crashuj systemu przy każdym Load
            }
        }
    }

    // Aktualizacja JSON z obiektu DTO
    public void syncPermissionsCache() {
        if (permissionsCache != null) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                mapper.findAndRegisterModules();
                this.permissionsCacheJson = mapper.writeValueAsString(permissionsCache);
            } catch (JsonProcessingException e) {
                // Ignore or log
            }
        }
    }

    // ========================================================================
    // Password Expiry Methods
    // ========================================================================

    /**
     * Sprawdza czy hasło użytkownika wygasło
     */
    public boolean isPasswordExpired() {
        if (passwordExpiresAt == null) {
            return false; // Bez ograniczeń czasowych
        }
        return LocalDateTime.now().isAfter(passwordExpiresAt);
    }

    /**
     * Sprawdza czy hasło wygasa w ciągu określonych dni
     */
    public boolean isPasswordExpiringInDays(int days) {
        if (passwordExpiresAt == null) {
            return false;
        }
        return LocalDateTime.now().plusDays(days).isAfter(passwordExpiresAt);
    }

    /**
     * Sprawdza czy użytkownik musi zmienić hasło (wymuszenie lub wygaśnięcie)
     */
    public boolean mustChangePasswordNow() {
        return mustChangePassword || isPasswordExpired();
    }

    /**
     * Oblicza dni do wygaśnięcia hasła
     */
    public Long getDaysUntilPasswordExpiry() {
        if (passwordExpiresAt == null) {
            return null; // Bez ograniczenia czasowego
        }
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), passwordExpiresAt);
    }

    /**
     * Przedłuża ważność hasła o określoną liczbę dni
     */
    public void extendPasswordExpiry(int days) {
        if (passwordExpiresAt != null) {
            this.passwordExpiresAt = passwordExpiresAt.plusDays(days);
        } else if (passwordExpiryDays != null) {
            this.passwordExpiresAt = LocalDateTime.now().plusDays(passwordExpiryDays + days);
        }
    }

    // --- UserDetails Methods ---

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles;
    }

    @Override
    public boolean isAccountNonExpired() {
        return !accountExpired;
    }

    @Override
    public boolean isAccountNonLocked() {
        if (locked)
            return false;
        if (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now())) {
            return false;
        }
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return !credentialsExpired;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public String getFullName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else if (lastName != null) {
            return lastName;
        }
        return username;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
