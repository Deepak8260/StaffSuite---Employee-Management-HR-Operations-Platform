package com.staffsuite.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.staffsuite.dto.OrganizationDtos.OrgNode;
import com.staffsuite.entity.Employee;
import com.staffsuite.entity.Role;
import com.staffsuite.security.AuthenticatedUser;
import com.staffsuite.repository.EmployeeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrgChartService {

    private final EmployeeRepository employeeRepository;

    /**
     * Builds the manager -> direct reports forest of the active employees the caller may see: everyone for HR/Admin,
     * a Manager's own team (themself on top), and only themself for anyone else. Anyone with no visible manager
     * (or whose manager has been offboarded) is a root.
     */
    @Transactional(readOnly = true)
    public List<OrgNode> build(AuthenticatedUser actor) {
        boolean all = actor.role() == Role.SYSTEM_ADMIN || actor.role() == Role.HR_ADMIN;
        Long me = actor.employeeId();
        List<Employee> employees = employeeRepository.findByDeletedFalse().stream()
                .filter(e -> all || (me != null && (e.getId().equals(me)
                        || (actor.role() == Role.MANAGER && e.getManager() != null && e.getManager().getId().equals(me)))))
                .toList();
        Set<Long> activeIds = new HashSet<>();
        employees.forEach(e -> activeIds.add(e.getId()));

        Map<Long, List<Employee>> reportsByManager = new HashMap<>();
        List<Employee> roots = new ArrayList<>();
        for (Employee e : employees) {
            Long managerId = e.getManager() == null ? null : e.getManager().getId();
            if (managerId == null || !activeIds.contains(managerId)) {
                roots.add(e);
            } else {
                reportsByManager.computeIfAbsent(managerId, k -> new ArrayList<>()).add(e);
            }
        }

        Set<Long> visited = new HashSet<>();
        List<OrgNode> forest = new ArrayList<>();
        for (Employee root : sorted(roots)) {
            forest.add(toNode(root, reportsByManager, visited));
        }
        // Safety net: if bad data ever formed a loop, its members are unreachable from any root.
        // Surface them as roots rather than silently dropping people from the chart.
        for (Employee e : sorted(employees)) {
            if (!visited.contains(e.getId())) {
                forest.add(toNode(e, reportsByManager, visited));
            }
        }
        return forest;
    }

    private OrgNode toNode(Employee e, Map<Long, List<Employee>> reportsByManager, Set<Long> visited) {
        visited.add(e.getId());
        List<OrgNode> children = new ArrayList<>();
        for (Employee report : sorted(reportsByManager.getOrDefault(e.getId(), List.of()))) {
            if (!visited.contains(report.getId())) {
                children.add(toNode(report, reportsByManager, visited));
            }
        }
        return new OrgNode(e.getId(), e.getEmployeeCode(), e.getFullName(), e.getJobTitle(),
                e.getDepartment() == null ? null : e.getDepartment().getName(), e.getEmploymentStatus(), children);
    }

    private static List<Employee> sorted(List<Employee> list) {
        return list.stream().sorted(Comparator.comparing(Employee::getFullName, String.CASE_INSENSITIVE_ORDER)).toList();
    }
}
