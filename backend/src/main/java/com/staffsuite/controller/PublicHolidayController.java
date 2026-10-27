package com.staffsuite.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.staffsuite.dto.LeaveDtos.HolidayRequest;
import com.staffsuite.dto.LeaveDtos.HolidayResponse;
import com.staffsuite.security.AuthenticatedUser;
import com.staffsuite.service.PublicHolidayService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/public-holidays")
@RequiredArgsConstructor
public class PublicHolidayController {

    private static final String HR_OR_ADMIN = "hasAnyRole('SYSTEM_ADMIN','HR_ADMIN')";

    private final PublicHolidayService holidayService;

    @GetMapping
    public List<HolidayResponse> list(@RequestParam(required = false) Integer year) {
        return holidayService.list(year != null ? year : LocalDate.now().getYear());
    }

    @PostMapping
    @PreAuthorize(HR_OR_ADMIN)
    public ResponseEntity<HolidayResponse> create(@AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody HolidayRequest request, HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(holidayService.create(request, actor, http.getRemoteAddr()));
    }

    @PutMapping("/{id}")
    @PreAuthorize(HR_OR_ADMIN)
    public HolidayResponse update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id,
            @Valid @RequestBody HolidayRequest request, HttpServletRequest http) {
        return holidayService.update(id, request, actor, http.getRemoteAddr());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(HR_OR_ADMIN)
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable Long id,
            HttpServletRequest http) {
        holidayService.delete(id, actor, http.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
