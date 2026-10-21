package com.rentequip.backend.services;

import com.rentequip.backend.dtos.request.EquipmentCreateRequest;
import com.rentequip.backend.dtos.request.EquipmentUpdateRequest;
import com.rentequip.backend.dtos.response.EquipmentResponse;
import com.rentequip.backend.dtos.response.EquipmentSummaryResponse;
import com.rentequip.backend.dtos.response.PageResponse;
import com.rentequip.backend.entities.Company;
import com.rentequip.backend.entities.Equipment;
import com.rentequip.backend.entities.EquipmentCategory;
import com.rentequip.backend.exceptions.DuplicateResourceException;
import com.rentequip.backend.exceptions.ForbiddenOperationException;
import com.rentequip.backend.exceptions.InvalidOperationException;
import com.rentequip.backend.exceptions.InvalidReservationDateException;
import com.rentequip.backend.exceptions.ResourceNotFoundException;
import com.rentequip.backend.mappers.EquipmentMapper;
import com.rentequip.backend.repositories.CompanyRepository;
import com.rentequip.backend.repositories.EquipmentCategoryRepository;
import com.rentequip.backend.repositories.EquipmentRepository;
import com.rentequip.backend.security.CurrentUser;
import com.rentequip.backend.repositories.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EquipmentService {

    private static final double MAX_SEARCH_RADIUS_KM = 1000.0;
    private static final double DEFAULT_SEARCH_RADIUS_KM = 25.0;

    /** Lima's Plaza Mayor: origin of the catalogue when the client sends no GPS coordinates. */
    static final BigDecimal DEFAULT_LATITUDE = new BigDecimal("-12.04637");
    static final BigDecimal DEFAULT_LONGITUDE = new BigDecimal("-77.04279");

    private final EquipmentRepository equipmentRepository;
    private final CurrentUser currentUser;
    private final EquipmentCategoryRepository categoryRepository;
    private final CompanyRepository companyRepository;
    private final ReservationRepository reservationRepository;
    private final EquipmentMapper equipmentMapper;

    @Transactional
    public EquipmentResponse create(EquipmentCreateRequest request) {
        Company owner = findCompanyOrThrow(request.ownerCompanyId());
        validateSerialNumberIsFree(request.serialNumber(), null);
        Set<EquipmentCategory> categories = resolveCategories(request.categoryIds());

        Equipment equipment = equipmentMapper.toEntity(request, owner, categories);
        return equipmentMapper.toResponse(equipmentRepository.save(equipment));
    }

    @Transactional
    public EquipmentResponse update(Long equipmentId, EquipmentUpdateRequest request) {
        Long actingCompanyId = currentUser.requireCompanyId();
        Equipment equipment = findOrThrow(equipmentId);
        validateOwnership(equipment, actingCompanyId);

        Set<EquipmentCategory> categories = request.categoryIds() == null
                ? null
                : resolveCategories(request.categoryIds());
        equipmentMapper.updateEntity(equipment, request, categories);
        return equipmentMapper.toResponse(equipment);
    }

    @Transactional
    public void delete(Long equipmentId) {
        Long actingCompanyId = currentUser.requireCompanyId();
        Equipment equipment = findOrThrow(equipmentId);
        validateOwnership(equipment, actingCompanyId);
        validateNoActiveReservations(equipmentId);
        equipmentRepository.delete(equipment);
    }

    @Transactional(readOnly = true)
    public EquipmentResponse findById(Long equipmentId) {
        return equipmentMapper.toResponse(
                equipmentRepository.findByIdWithCategories(equipmentId)
                        .orElseThrow(() -> ResourceNotFoundException.of("Equipment", equipmentId)));
    }

    @Transactional(readOnly = true)
    public PageResponse<EquipmentSummaryResponse> findByOwner(Long ownerCompanyId, Pageable pageable) {
        assertCompanyExists(ownerCompanyId);
        Page<Equipment> page = equipmentRepository.findByOwnerId(ownerCompanyId, pageable);
        return PageResponse.from(page, equipmentMapper::toSummary);
    }

    /**
     * Search with optional location. A caller with coordinates gets a nearby search (25 km unless it asks
     * otherwise); a caller without them gets the whole catalogue, ordered by distance to Lima.
     */
    @Transactional(readOnly = true)
    public PageResponse<EquipmentSummaryResponse> searchCatalog(BigDecimal latitude, BigDecimal longitude,
                                                                 Double radiusKm, String query, Long categoryId,
                                                                 String city, BigDecimal maxDailyRate,
                                                                 LocalDate startDate, LocalDate endDate,
                                                                 Long excludedOwnerId, int page, int size) {
        boolean hasLocation = latitude != null && longitude != null;
        double effectiveRadius = radiusKm != null
                ? radiusKm
                : hasLocation ? DEFAULT_SEARCH_RADIUS_KM : MAX_SEARCH_RADIUS_KM;
        return runSearch(hasLocation ? latitude : DEFAULT_LATITUDE, hasLocation ? longitude : DEFAULT_LONGITUDE,
                effectiveRadius, query, categoryId, city, maxDailyRate, startDate, endDate, excludedOwnerId,
                page, size);
    }

    /**
     * Proximity search used by the catalogue screen. The repository orders by distance, so the page is
     * requested unsorted to keep a single ORDER BY clause in the generated query.
     */
    @Transactional(readOnly = true)
    public PageResponse<EquipmentSummaryResponse> search(BigDecimal latitude, BigDecimal longitude,
                                                          double radiusKm, Long categoryId, String city,
                                                          BigDecimal maxDailyRate, LocalDate startDate,
                                                          LocalDate endDate, Long excludedOwnerId,
                                                          int page, int size) {
        return runSearch(latitude, longitude, radiusKm, null, categoryId, city, maxDailyRate, startDate, endDate,
                excludedOwnerId, page, size);
    }

    private PageResponse<EquipmentSummaryResponse> runSearch(BigDecimal latitude, BigDecimal longitude,
                                                             double radiusKm, String query, Long categoryId,
                                                             String city, BigDecimal maxDailyRate,
                                                             LocalDate startDate, LocalDate endDate,
                                                             Long excludedOwnerId, int page, int size) {
        validateRadius(radiusKm);
        validateSearchDates(startDate, endDate);
        validateCategoryExists(categoryId);

        Page<Equipment> results = equipmentRepository.searchNearby(latitude, longitude, radiusKm, categoryId,
                normalize(city), toContainsPattern(query), maxDailyRate, startDate, endDate, excludedOwnerId,
                PageRequest.of(page, size));
        return PageResponse.from(results, equipmentMapper::toSummary);
    }

    /**
     * Case-insensitive "contains" pattern for the free-text filter. The pattern is built here rather than
     * in JPQL so PostgreSQL always receives a typed string parameter, even when the filter is absent.
     */
    private String toContainsPattern(String query) {
        String normalized = normalize(query);
        return normalized == null ? null : "%" + normalized.toLowerCase(Locale.ROOT) + "%";
    }

    private Set<EquipmentCategory> resolveCategories(Set<Long> categoryIds) {
        List<EquipmentCategory> found = categoryRepository.findAllByIdIn(categoryIds);
        if (found.size() != categoryIds.size()) {
            Set<Long> missing = new HashSet<>(categoryIds);
            found.forEach(category -> missing.remove(category.getId()));
            throw new ResourceNotFoundException("Unknown equipment categories: " + missing);
        }
        return new LinkedHashSet<>(found);
    }

    private Equipment findOrThrow(Long equipmentId) {
        return equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> ResourceNotFoundException.of("Equipment", equipmentId));
    }

    private Company findCompanyOrThrow(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> ResourceNotFoundException.of("Company", companyId));
    }

    private void assertCompanyExists(Long companyId) {
        if (!companyRepository.existsById(companyId)) {
            throw ResourceNotFoundException.of("Company", companyId);
        }
    }

    private void validateOwnership(Equipment equipment, Long actingCompanyId) {
        if (!equipment.getOwner().getId().equals(actingCompanyId)) {
            throw ForbiddenOperationException.notOwner("equipment", equipment.getId());
        }
    }

    private void validateSerialNumberIsFree(String serialNumber, Long currentId) {
        if (serialNumber == null || serialNumber.isBlank()) {
            return;
        }
        boolean taken = currentId == null
                ? equipmentRepository.existsBySerialNumber(serialNumber)
                : equipmentRepository.existsBySerialNumberAndIdNot(serialNumber, currentId);
        if (taken) {
            throw DuplicateResourceException.of("Equipment", "serial number", serialNumber);
        }
    }

    private void validateNoActiveReservations(Long equipmentId) {
        boolean hasActive = !reservationRepository
                .findOverlapping(equipmentId, LocalDate.now(), LocalDate.now().plusYears(10)).isEmpty();
        if (hasActive) {
            throw new InvalidOperationException(
                    "Equipment with active or pending reservations cannot be deleted; deactivate it instead");
        }
    }

    private void validateRadius(double radiusKm) {
        if (radiusKm <= 0 || radiusKm > MAX_SEARCH_RADIUS_KM) {
            throw new InvalidOperationException(
                    "Search radius must be between 0 and " + MAX_SEARCH_RADIUS_KM + " km");
        }
    }

    private void validateSearchDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null && endDate == null) {
            return;
        }
        if (startDate == null || endDate == null) {
            throw new InvalidReservationDateException("Both start date and end date are required to filter by dates");
        }
        if (endDate.isBefore(startDate)) {
            throw new InvalidReservationDateException("End date must be on or after start date");
        }
    }

    private void validateCategoryExists(Long categoryId) {
        if (categoryId != null && !categoryRepository.existsById(categoryId)) {
            throw ResourceNotFoundException.of("EquipmentCategory", categoryId);
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
