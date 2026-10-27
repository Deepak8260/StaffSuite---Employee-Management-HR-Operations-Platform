package com.staffsuite.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.staffsuite.dto.OrganizationDtos.OrgNode;
import com.staffsuite.service.OrgChartService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/org-chart")
@RequiredArgsConstructor
public class OrgChartController {

    private final OrgChartService orgChartService;

    /** Hierarchy of active employees (name, title, department only), visible to every signed-in user. */
    @GetMapping
    public List<OrgNode> orgChart() {
        return orgChartService.build();
    }
}
