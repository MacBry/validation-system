package com.mac.bry.validationsystem.laboratory;

import com.mac.bry.validationsystem.department.Department;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.envers.Audited;

import java.util.Objects;

@Entity
@Table(name = "laboratories")
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Laboratory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * Dział do którego należy pracownia
     * NOWE POLE!
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(name = "full_name", nullable = false, unique = true, length = 200)
    @NotBlank(message = "Pełna nazwa pracowni jest wymagana")
    private String fullName;

    @Column(name = "abbreviation", nullable = false, unique = true, length = 50)
    @NotBlank(message = "Skrót pracowni jest wymagany")
    private String abbreviation;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Laboratory that = (Laboratory) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Laboratory{" +
                "id=" + id +
                ", fullName='" + fullName + '\'' +
                ", abbreviation='" + abbreviation + '\'' +
                '}';
    }
}
