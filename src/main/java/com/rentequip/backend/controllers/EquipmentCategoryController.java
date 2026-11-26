package com.rentequip.backend.controllers;

import com.rentequip.backend.dtos.request.EquipmentCategoryCreateRequest;
import com.rentequip.backend.dtos.response.EquipmentCategoryResponse;
import com.rentequip.backend.services.EquipmentCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/equipment-categories")
@RequiredArgsConstructor
public class EquipmentCategoryController {

    /**
     * Categories are shared by every company, so a company ADMIN is not enough: only a platform admin
     * (PLATFORM_ADMIN_EMAILS, see {@link com.rentequip.backend.security.PlatformAdmins}) changes them.
     */
    static final String PLATFORM_ADMIN = "hasRole('ADMIN') and @platformAdmins.includes(authentication)";

    private final EquipmentCategoryService categoryService;

    @PreAuthorize(PLATFORM_ADMIN)
    @PostMapping
    public ResponseEntity<EquipmentCategoryResponse> create(
            @Valid @RequestBody EquipmentCategoryCreateRequest request,
            UriComponentsBuilder uriBuilder) {
        EquipmentCategoryResponse created = categoryService.create(request);
        URI location = uriBuilder.path("/api/v1/equipment-categories/{id}")
                .buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public ResponseEntity<List<EquipmentCategoryResponse>> findAll() {
        return ResponseEntity.ok(categoryService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EquipmentCategoryResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.findById(id));
    }

    @PreAuthorize(PLATFORM_ADMIN)
    @PutMapping("/{id}")
    public ResponseEntity<EquipmentCategoryResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody EquipmentCategoryCreateRequest request) {
        return ResponseEntity.ok(categoryService.update(id, request));
    }

    @PreAuthorize(PLATFORM_ADMIN)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
