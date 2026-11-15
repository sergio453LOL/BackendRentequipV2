package com.rentequip.backend.services;

import com.rentequip.backend.dtos.response.CompanyResponse;
import com.rentequip.backend.entities.Company;
import com.rentequip.backend.exceptions.InvalidOperationException;
import com.rentequip.backend.exceptions.ResourceNotFoundException;
import com.rentequip.backend.identity.IdentityLookupClient;
import com.rentequip.backend.identity.RucInfo;
import com.rentequip.backend.identity.RucValidator;
import com.rentequip.backend.mappers.CompanyMapper;
import com.rentequip.backend.repositories.CompanyRepository;
import com.rentequip.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * The "verified company" badge: a company is verified when SUNAT reports its RUC as ACTIVO and HABIDO.
 * Checked automatically at registration (without blocking it if SUNAT is down) and on demand from the
 * profile.
 */
@Service
@RequiredArgsConstructor
public class CompanyVerificationService {

    private static final Logger log = LoggerFactory.getLogger(CompanyVerificationService.class);

    private final IdentityLookupClient lookupClient;
    private final CompanyRepository companyRepository;
    private final CompanyMapper companyMapper;
    private final CurrentUser currentUser;

    /** Best effort at registration: an unreachable SUNAT leaves the company unverified, never unregistered. */
    public void verifyQuietly(Company company) {
        if (!lookupClient.isConfigured() || !RucValidator.isValid(company.getTaxId())) {
            return;
        }
        try {
            lookupClient.findRuc(company.getTaxId()).ifPresent(info -> apply(company, info));
        } catch (RuntimeException e) {
            log.warn("Could not verify company {} with SUNAT: {}", company.getId(), e.getMessage());
        }
    }

    /** "Verify with SUNAT" on the profile: errors are reported, so the user knows what to fix. */
    @Transactional
    public CompanyResponse verifyMine() {
        Long companyId = currentUser.requireCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> ResourceNotFoundException.of("Company", companyId));
        if (!RucValidator.isValid(company.getTaxId())) {
            throw new InvalidOperationException("El RUC registrado no es válido: corrígelo para verificar la empresa");
        }
        Optional<RucInfo> info = lookupClient.findRuc(company.getTaxId());
        if (info.isEmpty()) {
            throw new InvalidOperationException("SUNAT no tiene registrado el RUC " + company.getTaxId());
        }
        apply(company, info.get());
        if (!company.isVerified()) {
            throw new InvalidOperationException("SUNAT reporta el RUC como " + company.getTaxStatus()
                    + ". Solo se verifican empresas ACTIVO y HABIDO.");
        }
        return companyMapper.toResponse(company);
    }

    private static void apply(Company company, RucInfo info) {
        company.setTaxStatus(info.state() + " / " + info.condition());
        company.setVerified(info.inGoodStanding());
        company.setVerifiedAt(info.inGoodStanding() ? LocalDateTime.now() : null);
    }
}
