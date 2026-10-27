package com.staffsuite.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.staffsuite.entity.PublicHoliday;

public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, Long> {

    List<PublicHoliday> findByHolidayDateBetweenOrderByHolidayDateAsc(LocalDate from, LocalDate to);

    boolean existsByHolidayDate(LocalDate date);

    boolean existsByHolidayDateAndIdNot(LocalDate date, Long id);
}
