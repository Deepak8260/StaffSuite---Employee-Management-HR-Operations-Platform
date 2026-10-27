package com.staffsuite.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.staffsuite.entity.Department;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findByActiveTrueOrderByNameAsc();

    List<Department> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
