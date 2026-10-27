package com.staffsuite.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.staffsuite.entity.DocumentCategory;

public interface DocumentCategoryRepository extends JpaRepository<DocumentCategory, Long> {

    List<DocumentCategory> findByActiveTrueOrderByNameAsc();

    List<DocumentCategory> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
