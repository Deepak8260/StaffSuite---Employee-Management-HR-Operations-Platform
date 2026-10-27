package com.staffsuite.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.staffsuite.entity.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    /** Active (not offboarded) employees currently assigned to a department. */
    long countByDepartment_IdAndDeletedFalse(Long departmentId);

    /** Rows of [departmentId, activeEmployeeCount]. */
    @Query("select e.department.id, count(e) from Employee e "
            + "where e.deleted = false and e.department is not null group by e.department.id")
    List<Object[]> countActiveByDepartment();

    @EntityGraph(attributePaths = { "department", "manager" })
    List<Employee> findByDeletedFalse();

    @Override
    @EntityGraph(attributePaths = { "department", "designation", "manager" })
    Page<Employee> findAll(Specification<Employee> spec, Pageable pageable);
}
