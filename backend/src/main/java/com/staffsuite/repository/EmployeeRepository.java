package com.staffsuite.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.staffsuite.entity.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    // ---- reporting aggregates (read-only; one grouped query each) ----

    /** Rows of [departmentId, headcount] for current (not offboarded) employees, optionally one department. */
    @Query("select e.department.id, count(e) from Employee e where e.deleted = false and e.department is not null "
            + "and (:departmentId is null or e.department.id = :departmentId) group by e.department.id")
    List<Object[]> headcountByDepartment(@Param("departmentId") Long departmentId);

    /** Current employees who have no department (only counted when no department filter is applied). */
    @Query("select count(e) from Employee e where e.deleted = false and e.department is null and :departmentId is null")
    long headcountWithoutDepartment(@Param("departmentId") Long departmentId);

    /** Rows of [employmentStatus, headcount] for current employees, optionally one department. */
    @Query("select e.employmentStatus, count(e) from Employee e where e.deleted = false "
            + "and (:departmentId is null or e.department.id = :departmentId) group by e.employmentStatus")
    List<Object[]> headcountByStatus(@Param("departmentId") Long departmentId);

    /**
     * Rows of [joiningDate, deleted] for everyone hired in the range, including people who have since been
     * offboarded: a hire is still a hire.
     */
    @Query("select e.joiningDate, e.deleted from Employee e where e.joiningDate between :from and :to "
            + "and (:departmentId is null or e.department.id = :departmentId)")
    List<Object[]> hiresBetween(@Param("from") LocalDate from, @Param("to") LocalDate to,
            @Param("departmentId") Long departmentId);

    boolean existsByEmailIgnoreCase(String email);

    Optional<Employee> findByEmailIgnoreCase(String email);

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
