package com.mac.bry.validationsystem.validation;

import com.mac.bry.validationsystem.device.CoolingDevice;
import com.mac.bry.validationsystem.department.Department;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ValidationSpecifications {

    public static Specification<Validation> filterBy(
            boolean isSuperAdmin,
            Collection<Long> allowedCompanyIds,
            Collection<Long> allowedDeptIds,
            Collection<Long> allowedLabIds,
            ValidationStatus status,
            Long filterCompanyId,
            Long filterDeptId,
            Long filterLabId) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Joins needed for filters and access control
            Join<Validation, CoolingDevice> deviceJoin = root.join("coolingDevice", jakarta.persistence.criteria.JoinType.INNER);
            Join<CoolingDevice, Department> deptJoin = deviceJoin.join("department", jakarta.persistence.criteria.JoinType.INNER);

            // 1. Access Control
            if (!isSuperAdmin) {
                Predicate companyPredicate = deptJoin.get("company").get("id").in(allowedCompanyIds);
                Predicate deptPredicate = deptJoin.get("id").in(allowedDeptIds);
                
                Predicate labPredicate = cb.disjunction(); // Default false
                if (allowedLabIds != null && !allowedLabIds.isEmpty()) {
                    labPredicate = deviceJoin.get("laboratory").get("id").in(allowedLabIds);
                }
                
                predicates.add(cb.or(companyPredicate, deptPredicate, labPredicate));
            }

            // 2. User Filters
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (filterCompanyId != null) {
                predicates.add(cb.equal(deptJoin.get("company").get("id"), filterCompanyId));
            }
            if (filterDeptId != null) {
                predicates.add(cb.equal(deptJoin.get("id"), filterDeptId));
            }
            if (filterLabId != null) {
                predicates.add(cb.equal(deviceJoin.get("laboratory").get("id"), filterLabId));
            }

            // Order by date desc
            query.orderBy(cb.desc(root.get("createdDate")));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
