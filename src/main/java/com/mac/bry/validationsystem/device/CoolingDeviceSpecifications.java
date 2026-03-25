package com.mac.bry.validationsystem.device;

import com.mac.bry.validationsystem.department.Department;
import com.mac.bry.validationsystem.laboratory.Laboratory;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class CoolingDeviceSpecifications {

    public static Specification<CoolingDevice> filterBy(
            boolean isSuperAdmin,
            Collection<Long> allowedCompanyIds,
            Collection<Long> allowedDeptIds,
            Collection<Long> allowedLabIds,
            Long filterCompanyId,
            Long filterDeptId,
            Long filterLabId) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Joins for filters and access control
            Join<CoolingDevice, Department> deptJoin = root.join("department", jakarta.persistence.criteria.JoinType.INNER);
            
            // 1. Access Control (Multi-Tenancy)
            if (!isSuperAdmin) {
                Predicate companyPredicate = deptJoin.get("company").get("id").in(allowedCompanyIds);
                Predicate deptPredicate = deptJoin.get("id").in(allowedDeptIds);
                
                // Lab access: if user has explicit access to labs
                Predicate labPredicate = cb.disjunction(); // Default false
                if (allowedLabIds != null && !allowedLabIds.isEmpty()) {
                    labPredicate = root.get("laboratory").get("id").in(allowedLabIds);
                }

                predicates.add(cb.or(companyPredicate, deptPredicate, labPredicate));
            }

            // 2. User Filters
            if (filterCompanyId != null) {
                predicates.add(cb.equal(deptJoin.get("company").get("id"), filterCompanyId));
            }
            if (filterDeptId != null) {
                predicates.add(cb.equal(deptJoin.get("id"), filterDeptId));
            }
            if (filterLabId != null) {
                predicates.add(cb.equal(root.get("laboratory").get("id"), filterLabId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
