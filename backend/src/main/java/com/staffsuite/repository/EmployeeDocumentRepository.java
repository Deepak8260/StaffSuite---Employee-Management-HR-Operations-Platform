package com.staffsuite.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.staffsuite.entity.EmployeeDocument;

public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, Long> {

    @EntityGraph(attributePaths = { "employee", "employee.department", "category" })
    List<EmployeeDocument> findByEmployee_IdOrderByCreatedAtDescIdDesc(Long employeeId);

    @EntityGraph(attributePaths = { "employee", "employee.department", "category" })
    Optional<EmployeeDocument> findWithDetailsById(Long id);

    /** Documents of current (not offboarded) employees expiring within [from, to], soonest first. */
    @Query("select d from EmployeeDocument d join fetch d.employee e left join fetch e.department "
            + "join fetch d.category where d.expiryDate between :from and :to and e.deleted = false "
            + "order by d.expiryDate asc, d.id asc")
    List<EmployeeDocument> findExpiringBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Same, but also everything that has already expired. */
    @Query("select d from EmployeeDocument d join fetch d.employee e left join fetch e.department "
            + "join fetch d.category where d.expiryDate <= :to and e.deleted = false "
            + "order by d.expiryDate asc, d.id asc")
    List<EmployeeDocument> findExpiringUntil(@Param("to") LocalDate to);
}
