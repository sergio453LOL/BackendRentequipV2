package com.rentequip.backend.services;

import com.rentequip.backend.dtos.request.CompanyCreateRequest;
import com.rentequip.backend.dtos.request.CompanyUpdateRequest;
import com.rentequip.backend.dtos.response.CompanyResponse;
import com.rentequip.backend.dtos.response.PageResponse;
import com.rentequip.backend.entities.Company;
import com.rentequip.backend.exceptions.DuplicateResourceException;
import com.rentequip.backend.exceptions.ForbiddenOperationException;
import com.rentequip.backend.exceptions.ResourceNotFoundException;
import com.rentequip.backend.mappers.CompanyMapper;
import com.rentequip.backend.repositories.CompanyRepository;
import com.rentequip.backend.security.CurrentUser;
import com.rentequip.backend.security.DemoAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final CurrentUser currentUser;
    private final CompanyMapper companyMapper;
    private final DemoAccess demoAccess;

    @Transactional
    public CompanyResponse create(CompanyCreateRequest request) {
        return companyMapper.toResponse(createEntity(request));
    }

    /**
     * Same creation rules, but handing back the entity so the registration flow can attach the first
     * administrator to it inside the very same transaction.
     */
    @Transactional
    public Company createEntity(CompanyCreateRequest request) {
        validateTaxIdIsFree(request.taxId());
        validateEmailIsFree(request.email(), null);
        return companyRepository.save(companyMapper.toEntity(request));
    }

    @Transactional
    public CompanyResponse update(Long companyId, CompanyUpdateRequest request) {
        demoAccess.forbidChangesBy(currentUser.require());
        Company company = findOrThrow(companyId);
        validateSelfService(companyId, currentUser.requireCompanyId());
        if (request.email() != null) {
            validateEmailIsFree(request.email(), companyId);
        }
        companyMapper.updateEntity(company, request);
        return companyMapper.toResponse(company);
    }

    /**
     * Soft delete: companies keep their reservation history, so they are deactivated instead of removed.
     */
    @Transactional
    public void deactivate(Long companyId) {
        demoAccess.forbidChangesBy(currentUser.require());
        Company company = findOrThrow(companyId);
        validateSelfService(companyId, currentUser.requireCompanyId());
        company.setActive(false);
    }

    @Transactional(readOnly = true)
    public CompanyResponse findById(Long companyId) {
        return companyMapper.toResponse(findOrThrow(companyId));
    }

    @Transactional(readOnly = true)
    public PageResponse<CompanyResponse> findAll(String city, Pageable pageable) {
        Page<Company> page = city == null || city.isBlank()
                ? companyRepository.findByActiveTrue(pageable)
                : companyRepository.findByCityIgnoreCaseAndActiveTrue(city.trim(), pageable);
        return PageResponse.from(page, companyMapper::toResponse);
    }

    private Company findOrThrow(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> ResourceNotFoundException.of("Company", companyId));
    }

    private void validateTaxIdIsFree(String taxId) {
        if (companyRepository.existsByTaxId(taxId)) {
            throw DuplicateResourceException.of("Company", "tax id", taxId);
        }
    }

    private void validateEmailIsFree(String email, Long currentId) {
        boolean taken = currentId == null
                ? companyRepository.existsByEmailIgnoreCase(email)
                : companyRepository.existsByEmailIgnoreCaseAndIdNot(email, currentId);
        if (taken) {
            throw DuplicateResourceException.of("Company", "email", email);
        }
    }

    private void validateSelfService(Long companyId, Long actingCompanyId) {
        if (!companyId.equals(actingCompanyId)) {
            throw ForbiddenOperationException.notOwner("company", companyId);
        }
    }
}
