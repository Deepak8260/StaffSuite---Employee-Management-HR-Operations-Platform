package com.staffsuite.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.staffsuite.dto.DocumentDtos.CategoryRequest;
import com.staffsuite.dto.DocumentDtos.CategoryResponse;
import com.staffsuite.entity.DocumentCategory;
import com.staffsuite.exception.ApiException;
import com.staffsuite.repository.DocumentCategoryRepository;
import com.staffsuite.security.AuthenticatedUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DocumentCategoryService {

    private static final String ENTITY = "DOCUMENT_CATEGORY";

    private final DocumentCategoryRepository categoryRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(boolean includeInactive) {
        List<DocumentCategory> categories = includeInactive
                ? categoryRepository.findAllByOrderByNameAsc()
                : categoryRepository.findByActiveTrueOrderByNameAsc();
        return categories.stream().map(DocumentCategoryService::toResponse).toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request, AuthenticatedUser actor, String ip) {
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new ApiException(HttpStatus.CONFLICT, "A category named '" + name + "' already exists");
        }
        DocumentCategory category = new DocumentCategory();
        category.setName(name);
        category.setDescription(blankToNull(request.description()));
        category = categoryRepository.save(category);
        auditService.log(actor.userId(), "DOCUMENT_CATEGORY_CREATED", ENTITY, category.getId(), ip);
        return toResponse(category);
    }

    /** Categories are deactivated rather than deleted so existing documents keep their category. */
    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request, AuthenticatedUser actor, String ip) {
        DocumentCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Category not found"));
        String name = request.name().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "A category named '" + name + "' already exists");
        }
        category.setName(name);
        category.setDescription(blankToNull(request.description()));
        if (request.active() != null) {
            category.setActive(request.active());
        }
        auditService.log(actor.userId(), "DOCUMENT_CATEGORY_UPDATED", ENTITY, id, ip);
        return toResponse(category);
    }

    private static CategoryResponse toResponse(DocumentCategory c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getDescription(), c.isActive());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
