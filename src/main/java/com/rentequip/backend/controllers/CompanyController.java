package com.rentequip.backend.controllers;

import com.rentequip.backend.dtos.request.CompanyCreateRequest;
import com.rentequip.backend.dtos.request.CompanyUpdateRequest;
import com.rentequip.backend.dtos.response.CompanyDashboardResponse;
import com.rentequip.backend.dtos.response.CompanyResponse;
import com.rentequip.backend.dtos.response.EquipmentSummaryResponse;
import com.rentequip.backend.dtos.response.PageResponse;
import com.rentequip.backend.dtos.response.UserResponse;
import com.rentequip.backend.enums.UserRole;
import com.rentequip.backend.hateoas.CompanyModelAssembler;
import com.rentequip.backend.services.CompanyDashboardService;
import com.rentequip.backend.services.CompanyService;
import com.rentequip.backend.services.EquipmentService;
import com.rentequip.backend.services.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
@Validated
public class CompanyController {

    private final CompanyModelAssembler companyAssembler;
    private final CompanyService companyService;
    private final CompanyDashboardService companyDashboardService;
    private final UserService userService;
    private final EquipmentService equipmentService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<EntityModel<CompanyResponse>> create(
            @Valid @RequestBody CompanyCreateRequest request,
            UriComponentsBuilder uriBuilder) {
        CompanyResponse created = companyService.create(request);
        URI location = uriBuilder.path("/api/v1/companies/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(companyAssembler.toModel(created));
    }

    @GetMapping("/me/dashboard")
    public ResponseEntity<CompanyDashboardResponse> getMyDashboard() {
        return ResponseEntity.ok(companyDashboardService.getForCurrentCompany());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EntityModel<CompanyResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(companyAssembler.toModel(companyService.findById(id)));
    }

    @GetMapping
    public ResponseEntity<PageResponse<CompanyResponse>> findAll(
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
        return ResponseEntity.ok(companyService.findAll(city, pageable));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}")
    public ResponseEntity<EntityModel<CompanyResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody CompanyUpdateRequest request) {
        return ResponseEntity.ok(companyAssembler.toModel(
                companyService.update(id, request)));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        companyService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/users")
    public ResponseEntity<PageResponse<UserResponse>> findUsers(
            @PathVariable Long id,
            @RequestParam(required = false) UserRole role,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "lastName"));
        return ResponseEntity.ok(userService.findByCompany(id, role, pageable));
    }

    @GetMapping("/{id}/equipment")
    public ResponseEntity<PageResponse<EquipmentSummaryResponse>> findEquipment(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(equipmentService.findByOwner(id, pageable));
    }
}
