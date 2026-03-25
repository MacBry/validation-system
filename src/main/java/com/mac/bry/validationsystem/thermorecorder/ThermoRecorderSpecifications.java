package com.mac.bry.validationsystem.thermorecorder;

import com.mac.bry.validationsystem.department.Department;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ThermoRecorderSpecifications {

    public static Specification<ThermoRecorder> filterBy(
            boolean isSuperAdmin,
            Collection<Long> allowedCompanyIds,
            Collection<Long> allowedDeptIds,
            Long filterCompanyId,
            Long filterDeptId,
            Long filterLabId) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Joins for filters and access control
            Join<ThermoRecorder, Department> deptJoin = root.join("department", jakarta.persistence.criteria.JoinType.INNER);

            // 1. Access Control
            if (!isSuperAdmin) {
                Predicate companyPredicate = deptJoin.get("company").get("id").in(allowedCompanyIds);
                Predicate deptPredicate = deptJoin.get("id").in(allowedDeptIds);
                predicates.add(cb.or(companyPredicate, deptPredicate));
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
