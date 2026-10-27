package com.staffsuite.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.staffsuite.entity.Designation;

public interface DesignationRepository extends JpaRepository<Designation, Long> {

    List<Designation> findByActiveTrueOrderByTitleAsc();

    List<Designation> findAllByOrderByTitleAsc();

    /** True when another designation already has this title in the same department (or both have none). */
    @Query("""
            select count(d) > 0 from Designation d
            where lower(d.title) = lower(:title)
              and ((:departmentId is null and d.departmentId is null) or d.departmentId = :departmentId)
              and (:excludeId is null or d.id <> :excludeId)
            """)
    boolean titleTaken(@Param("title") String title, @Param("departmentId") Long departmentId,
            @Param("excludeId") Long excludeId);
}
