package com.rentequip.backend.mappers;

import static com.rentequip.backend.mappers.MappingUtils.applyIfPresent;
import static com.rentequip.backend.mappers.MappingUtils.blankToNull;

import com.rentequip.backend.dtos.request.CompanyCreateRequest;
import com.rentequip.backend.dtos.request.CompanyUpdateRequest;
import com.rentequip.backend.dtos.response.CompanyResponse;
import com.rentequip.backend.entities.Company;
import org.springframework.stereotype.Component;

@Component
public class CompanyMapper {

    public Company toEntity(CompanyCreateRequest request) {
        Company company = new Company();
        company.setName(request.name().trim());
        company.setTaxId(request.taxId());
        company.setEmail(request.email().trim().toLowerCase());
        company.setPhone(request.phone());
        company.setAddress(request.address());
        company.setCity(request.city().trim());
        company.setLatitude(request.latitude());
        company.setLongitude(request.longitude());
        company.setActive(true);
        return company;
    }

    public void updateEntity(Company company, CompanyUpdateRequest request) {
        applyIfPresent(request.name(), value -> company.setName(value.trim()));
        applyIfPresent(request.email(), value -> company.setEmail(value.trim().toLowerCase()));
        applyIfPresent(request.phone(), company::setPhone);
        applyIfPresent(request.address(), company::setAddress);
        applyIfPresent(request.city(), value -> company.setCity(value.trim()));
        applyIfPresent(request.latitude(), company::setLatitude);
        applyIfPresent(request.longitude(), company::setLongitude);
        applyIfPresent(request.logoUrl(), value -> company.setLogoUrl(blankToNull(value)));
        applyIfPresent(request.description(), value -> company.setDescription(blankToNull(value)));
    }

    public CompanyResponse toResponse(Company company) {
        return new CompanyResponse(
                company.getId(),
                company.getName(),
                company.getTaxId(),
                company.getEmail(),
                company.getPhone(),
                company.getAddress(),
                company.getCity(),
                company.getLatitude(),
                company.getLongitude(),
                company.getLogoUrl(),
                company.getDescription(),
                company.isActive(),
                company.getCreatedAt()
        );
    }
}
