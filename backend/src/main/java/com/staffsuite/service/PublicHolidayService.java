package com.staffsuite.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.staffsuite.dto.LeaveDtos.HolidayRequest;
import com.staffsuite.dto.LeaveDtos.HolidayResponse;
import com.staffsuite.entity.PublicHoliday;
import com.staffsuite.exception.ApiException;
import com.staffsuite.repository.PublicHolidayRepository;
import com.staffsuite.security.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PublicHolidayService {

    private static final String ENTITY = "PUBLIC_HOLIDAY";

    private final PublicHolidayRepository holidayRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<HolidayResponse> list(int year) {
        return holidayRepository
                .findByHolidayDateBetweenOrderByHolidayDateAsc(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
                .stream().map(PublicHolidayService::toResponse).toList();
    }

    /** Holidays added or removed later do not change days already counted on existing leave requests. */
    @Transactional
    public HolidayResponse create(HolidayRequest request, AuthenticatedUser actor, String ip) {
        if (holidayRepository.existsByHolidayDate(request.holidayDate())) {
            throw new ApiException(HttpStatus.CONFLICT, "A holiday is already defined on " + request.holidayDate());
        }
        PublicHoliday holiday = new PublicHoliday();
        apply(holiday, request);
        holiday = holidayRepository.save(holiday);
        auditService.log(actor.userId(), "HOLIDAY_CREATED", ENTITY, holiday.getId(), ip);
        return toResponse(holiday);
    }

    @Transactional
    public HolidayResponse update(Long id, HolidayRequest request, AuthenticatedUser actor, String ip) {
        PublicHoliday holiday = find(id);
        if (holidayRepository.existsByHolidayDateAndIdNot(request.holidayDate(), id)) {
            throw new ApiException(HttpStatus.CONFLICT, "A holiday is already defined on " + request.holidayDate());
        }
        apply(holiday, request);
        auditService.log(actor.userId(), "HOLIDAY_UPDATED", ENTITY, id, ip);
        return toResponse(holiday);
    }

    @Transactional
    public void delete(Long id, AuthenticatedUser actor, String ip) {
        holidayRepository.delete(find(id));
        auditService.log(actor.userId(), "HOLIDAY_DELETED", ENTITY, id, ip);
    }

    private PublicHoliday find(Long id) {
        return holidayRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Holiday not found"));
    }

    private static void apply(PublicHoliday holiday, HolidayRequest r) {
        holiday.setName(r.name().trim());
        holiday.setHolidayDate(r.holidayDate());
        holiday.setDescription(r.description() == null || r.description().isBlank() ? null : r.description().trim());
        holiday.setOptional(Boolean.TRUE.equals(r.optional()));
    }

    static HolidayResponse toResponse(PublicHoliday h) {
        return new HolidayResponse(h.getId(), h.getName(), h.getHolidayDate(), h.getDescription(), h.isOptional());
    }
}
