package com.rentequip.backend.mappers;

import static com.rentequip.backend.mappers.MappingUtils.applyIfPresent;

import com.rentequip.backend.dtos.request.EquipmentCreateRequest;
import com.rentequip.backend.dtos.request.EquipmentUpdateRequest;
import com.rentequip.backend.dtos.response.EquipmentResponse;
import com.rentequip.backend.dtos.response.EquipmentSummaryResponse;
import com.rentequip.backend.entities.Company;
import com.rentequip.backend.entities.Equipment;
import com.rentequip.backend.entities.EquipmentCategory;
import com.rentequip.backend.enums.EquipmentStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Component
public class EquipmentMapper {

    private static final String DEFAULT_CURRENCY = "PEN";

    public Equipment toEntity(EquipmentCreateRequest request, Company owner, Set<EquipmentCategory> categories) {
        Equipment equipment = new Equipment();
        equipment.setName(request.name().trim());
        equipment.setDescription(request.description());
        equipment.setBrand(request.brand().trim());
        equipment.setModel(request.model().trim());
        equipment.setManufactureYear(request.manufactureYear());
        equipment.setSerialNumber(request.serialNumber());
        equipment.setSpecifications(request.specifications());
        equipment.setDailyRate(request.dailyRate());
        equipment.setWeeklyRate(request.weeklyRate());
        equipment.setSecurityDeposit(request.securityDeposit());
        equipment.setCurrency(request.currency() != null ? request.currency() : DEFAULT_CURRENCY);
        equipment.setStatus(EquipmentStatus.AVAILABLE);
        equipment.setAddress(request.address());
        equipment.setCity(request.city().trim());
        equipment.setLatitude(request.latitude());
        equipment.setLongitude(request.longitude());
        equipment.setOwner(owner);
        if (request.imageUrls() != null) {
            equipment.getImageUrls().addAll(request.imageUrls());
        }
        categories.forEach(equipment::addCategory);
        return equipment;
    }

    public void updateEntity(Equipment equipment, EquipmentUpdateRequest request, Set<EquipmentCategory> categories) {
        applyDetails(equipment, request);
        applyPricing(equipment, request);
        applyLocation(equipment, request);
        applyIfPresent(request.status(), equipment::setStatus);
        if (request.imageUrls() != null) {
            equipment.getImageUrls().clear();
            equipment.getImageUrls().addAll(request.imageUrls());
        }
        if (categories != null) {
            equipment.getCategories().clear();
            categories.forEach(equipment::addCategory);
        }
    }

    public EquipmentResponse toResponse(Equipment equipment) {
        return new EquipmentResponse(
                equipment.getId(),
                equipment.getName(),
                equipment.getDescription(),
                equipment.getBrand(),
                equipment.getModel(),
                equipment.getManufactureYear(),
                equipment.getSerialNumber(),
                equipment.getSpecifications(),
                equipment.getDailyRate(),
                equipment.getWeeklyRate(),
                equipment.getSecurityDeposit(),
                equipment.getCurrency(),
                equipment.getStatus(),
                equipment.getAddress(),
                equipment.getCity(),
                equipment.getLatitude(),
                equipment.getLongitude(),
                equipment.getImageUrls().stream().map(this::publicUrl).collect(Collectors.toCollection(ArrayList::new)),
                categoryNames(equipment),
                equipment.getOwner().getId(),
                equipment.getOwner().getName(),
                equipment.getCreatedAt()
        );
    }

    public EquipmentSummaryResponse toSummary(Equipment equipment) {
        return new EquipmentSummaryResponse(
                equipment.getId(),
                equipment.getName(),
                equipment.getBrand(),
                equipment.getModel(),
                equipment.getDailyRate(),
                equipment.getCurrency(),
                equipment.getStatus(),
                equipment.getCity(),
                equipment.getLatitude(),
                equipment.getLongitude(),
                primaryImageUrl(equipment),
                equipment.getOwner().getId(),
                equipment.getOwner().getName()
        );
    }

    private String primaryImageUrl(Equipment equipment) {
        return equipment.getImageUrls().isEmpty() ? null : publicUrl(equipment.getImageUrls().get(0));
    }

    /**
     * Demo photos are stored as server paths ("/demo-images/x.jpg") because the seeder runs before any
     * request and cannot know the public host. They become absolute here, with the host of the current
     * request, so web and mobile get a URL they can load. Uploaded photos are already absolute.
     */
    private String publicUrl(String url) {
        if (url == null || !url.startsWith("/") || RequestContextHolder.getRequestAttributes() == null) {
            return url;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().path(url).toUriString();
    }

    private void applyDetails(Equipment equipment, EquipmentUpdateRequest request) {
        applyIfPresent(request.name(), equipment::setName);
        applyIfPresent(request.description(), equipment::setDescription);
        applyIfPresent(request.brand(), equipment::setBrand);
        applyIfPresent(request.model(), equipment::setModel);
        applyIfPresent(request.manufactureYear(), equipment::setManufactureYear);
        applyIfPresent(request.specifications(), equipment::setSpecifications);
    }

    private void applyPricing(Equipment equipment, EquipmentUpdateRequest request) {
        applyIfPresent(request.dailyRate(), equipment::setDailyRate);
        applyIfPresent(request.weeklyRate(), equipment::setWeeklyRate);
        applyIfPresent(request.securityDeposit(), equipment::setSecurityDeposit);
        applyIfPresent(request.currency(), equipment::setCurrency);
    }

    private void applyLocation(Equipment equipment, EquipmentUpdateRequest request) {
        applyIfPresent(request.address(), equipment::setAddress);
        applyIfPresent(request.city(), equipment::setCity);
        applyIfPresent(request.latitude(), equipment::setLatitude);
        applyIfPresent(request.longitude(), equipment::setLongitude);
    }

    private Set<String> categoryNames(Equipment equipment) {
        return equipment.getCategories().stream()
                .map(EquipmentCategory::getName)
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
