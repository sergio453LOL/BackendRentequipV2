package com.rentequip.backend.mappers;

import static com.rentequip.backend.mappers.MappingUtils.applyIfPresent;
import static com.rentequip.backend.mappers.MappingUtils.blankToNull;

import com.rentequip.backend.dtos.request.EquipmentCreateRequest;
import com.rentequip.backend.dtos.request.EquipmentUpdateRequest;
import com.rentequip.backend.dtos.response.EquipmentResponse;
import com.rentequip.backend.dtos.request.OperatorProfileRequest;
import com.rentequip.backend.dtos.response.EquipmentSummaryResponse;
import com.rentequip.backend.dtos.response.OperatorProfileResponse;
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
        equipment.setOperatorIncluded(Boolean.TRUE.equals(request.operatorIncluded()));
        equipment.setUsageHours(request.usageHours());
        equipment.setVideoUrl(blankToNull(request.videoUrl()));
        applyOperator(equipment, request.operator());
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
        applyIfPresent(request.operatorIncluded(), equipment::setOperatorIncluded);
        applyIfPresent(request.usageHours(), equipment::setUsageHours);
        if (request.videoUrl() != null) {
            equipment.setVideoUrl(blankToNull(request.videoUrl()));
        }
        if (request.operator() != null) {
            applyOperator(equipment, request.operator());
        }
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
                equipment.getOwner().getLogoUrl(),
                equipment.getOwner().getDescription(),
                equipment.getCreatedAt(),
                equipment.isOperatorIncluded(),
                equipment.getSalePrice(),
                rentCreditPercentOf(equipment),
                equipment.getUsageHours(),
                equipment.getVideoUrl(),
                operatorOf(equipment),
                equipment.getOwner().isVerified(),
                publicUrl(equipment.getManualUrl())
        );
    }

    public EquipmentSummaryResponse toSummary(Equipment equipment) {
        return toSummary(equipment, null);
    }

    /** {@code distanceKm} is how far the machine is from the visitor, when the search knew where they are. */
    public EquipmentSummaryResponse toSummary(Equipment equipment, Double distanceKm) {
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
                equipment.getOwner().getName(),
                equipment.isOperatorIncluded(),
                equipment.getSalePrice(),
                equipment.getManufactureYear(),
                equipment.getUsageHours(),
                distanceKm,
                equipment.getOwner().isVerified()
        );
    }

    /** A blank name clears the whole sheet: an operator without a name tells the renter nothing. */
    private static void applyOperator(Equipment equipment, OperatorProfileRequest operator) {
        String name = operator == null ? null : blankToNull(operator.name());
        equipment.setOperatorName(name == null ? null : name.trim());
        equipment.setOperatorLicense(name == null ? null : blankToNull(operator.license()));
        equipment.setOperatorExperienceYears(name == null ? null : operator.experienceYears());
        equipment.setOperatorCertifications(name == null ? null : blankToNull(operator.certifications()));
    }

    private static OperatorProfileResponse operatorOf(Equipment equipment) {
        if (equipment.getOperatorName() == null) {
            return null;
        }
        return new OperatorProfileResponse(equipment.getOperatorName(), equipment.getOperatorLicense(),
                equipment.getOperatorExperienceYears(), equipment.getOperatorCertifications());
    }

    public static int rentCreditPercentOf(Equipment equipment) {
        return equipment.getRentCreditPercent() == null ? 0 : equipment.getRentCreditPercent();
    }

    public String primaryImageUrl(Equipment equipment) {
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
