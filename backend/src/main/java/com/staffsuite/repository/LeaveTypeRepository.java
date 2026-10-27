package com.staffsuite.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.staffsuite.entity.LeaveType;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {

    List<LeaveType> findByActiveTrueOrderByNameAsc();

    List<LeaveType> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
