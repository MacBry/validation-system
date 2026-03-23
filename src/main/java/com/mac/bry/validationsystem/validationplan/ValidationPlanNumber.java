package com.mac.bry.validationsystem.validationplan;

import com.mac.bry.validationsystem.device.CoolingDevice;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.envers.Audited;

import java.util.Objects;

@Entity
@Table(name = "validation_plan_numbers",
       uniqueConstraints = @UniqueConstraint(columnNames = {"cooling_device_id", "`year`"}))
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationPlanNumber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "`year`", nullable = false)
    @NotNull(message = "Rok jest wymagany")
    @Min(value = 2000, message = "Rok nie może być wcześniejszy niż 2000")
    private Integer year;

    @Column(name = "plan_number", nullable = false)
    @NotNull(message = "Numer RPW jest wymagany")
    @Min(value = 1, message = "Numer RPW musi być większy od 0")
    private Integer planNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cooling_device_id", nullable = false)
    private CoolingDevice coolingDevice;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidationPlanNumber that = (ValidationPlanNumber) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ValidationPlanNumber{" +
                "id=" + id +
                ", year=" + year +
                ", planNumber=" + planNumber +
                '}';
    }
}
