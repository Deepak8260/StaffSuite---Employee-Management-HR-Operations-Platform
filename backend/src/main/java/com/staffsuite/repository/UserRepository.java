package com.staffsuite.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.staffsuite.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByResetTokenHash(String resetTokenHash);

    Optional<User> findByEmployeeId(Long employeeId);

    java.util.List<User> findByRoleAndActiveTrueOrderByIdAsc(com.staffsuite.entity.Role role);

    boolean existsByEmailIgnoreCase(String email);
}
