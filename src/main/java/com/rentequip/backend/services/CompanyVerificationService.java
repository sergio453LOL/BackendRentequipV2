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
 * The "verified company" badge, in two steps:
 * <ol>
 *   <li>SUNAT must report the RUC as ACTIVO and HABIDO. This is checked at registration (without blocking it
 *       if SUNAT is down) and on demand from the profile, and is recorded as the company's tax status.</li>
 *   <li>A platform admin (PLATFORM_ADMIN_EMAILS) then approves the company. Anyone can type a real RUC at
 *       sign-up, so a good standing alone proves the RUC exists, not that the user is that company.</li>
 * </ol>
 * A company that SUNAT stops reporting in good standing loses the badge on its next check.
 */
@Service
@RequiredArgsConstructor
public class CompanyVerificationService {

    private static final Logger log = LoggerFactory.getLogger(CompanyVerificationService.class);

    private final IdentityLookupClient lookupClient;
    private final CompanyRepository companyRepository;
    private final CompanyMapper companyMapper;
    private final CurrentUser currentUser;

    /** Best effort at registration: an unreachable SUNAT leaves the tax status empty, never the sign-up undone. */
    public void verifyQuietly(Company company) {
        if (!lookupClient.isConfigured() || !RucValidator.isValid(company.getTaxId())) {
            return;
        }
        try {
            lookupClient.findRuc(company.getTaxId()).ifPresent(info -> {
                // The form locks the name SUNAT returns; enforcing it here keeps an edited request from
                // registering a RUC under someone else's business name.
                if (info.name() != null && info.name().length() >= 2) {
                    company.setName(info.name().length() > 150 ? info.name().substring(0, 150) : info.name());
                }
                recordTaxStatus(company, info);
            });
        } catch (RuntimeException e) {
            log.warn("Could not verify company {} with SUNAT: {}", company.getId(), e.getMessage());
        }
    }

    /**
     * "Verify with SUNAT" on the profile: records the RUC's standing and, when it is good, leaves the company
     * waiting for a platform admin's approval. Errors are reported so the user knows what to fix; a bad
     * standing is still saved (and removes a badge granted before), hence no rollback for it.
     */
    @Transactional(noRollbackFor = InvalidOperationException.class)
    public CompanyResponse verifyMine() {
        Company company = findOrThrow(currentUser.requireCompanyId());
        RucInfo info = lookUp(company);
        recordTaxStatus(company, info);
        if (!info.inGoodStanding()) {
            throw new InvalidOperationException("SUNAT reporta el RUC como " + company.getTaxStatus()
                    + ". Solo se verifican empresas ACTIVO y HABIDO.");
        }
        log.info("Company {} is in good standing with SUNAT; verified badge {}", company.getId(),
                company.isVerified() ? "kept" : "waiting for a platform admin");
        return companyMapper.toResponse(company);
    }

    /**
     * A platform admin grants the badge. SUNAT is asked again when it is configured, so the badge never goes
     * to a RUC that is not in good standing today.
     */
    @Transactional(noRollbackFor = InvalidOperationException.class)
    public CompanyResponse approve(Long companyId) {
        Company company = findOrThrow(companyId);
        if (lookupClient.isConfigured()) {
            RucInfo info = lookUp(company);
            recordTaxStatus(company, info);
            if (!info.inGoodStanding()) {
                throw new InvalidOperationException("SUNAT reporta el RUC como " + company.getTaxStatus()
                        + ": no se puede verificar la empresa.");
            }
        }
        company.setVerified(true);
        company.setVerifiedAt(LocalDateTime.now());
        log.info("Company {} verified by user {}", companyId, currentUser.requireUserId());
        return companyMapper.toResponse(company);
    }

    /** A platform admin withdraws the badge (an impersonation report, for instance). */
    @Transactional
    public CompanyResponse revoke(Long companyId) {
        Company company = findOrThrow(companyId);
        company.setVerified(false);
        company.setVerifiedAt(null);
        log.info("Company {} verification revoked by user {}", companyId, currentUser.requireUserId());
        return companyMapper.toResponse(company);
    }

    private Company findOrThrow(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> ResourceNotFoundException.of("Company", companyId));
    }

    private RucInfo lookUp(Company company) {
        if (!RucValidator.isValid(company.getTaxId())) {
            throw new InvalidOperationException("El RUC registrado no es válido: corrígelo para verificar la empresa");
        }
        Optional<RucInfo> info = lookupClient.findRuc(company.getTaxId());
        return info.orElseThrow(() -> new InvalidOperationException(
                "SUNAT no tiene registrado el RUC " + company.getTaxId()));
    }

    /** Saves SUNAT's answer. A bad standing removes the badge; a good one never grants it by itself. */
    private static void recordTaxStatus(Company company, RucInfo info) {
        company.setTaxStatus(info.state() + " / " + info.condition());
        if (!info.inGoodStanding()) {
            company.setVerified(false);
            company.setVerifiedAt(null);
        }
    }
}
