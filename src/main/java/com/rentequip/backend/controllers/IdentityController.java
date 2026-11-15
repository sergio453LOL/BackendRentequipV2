package com.rentequip.backend.controllers;

import com.rentequip.backend.exceptions.InvalidOperationException;
import com.rentequip.backend.exceptions.ResourceNotFoundException;
import com.rentequip.backend.identity.DniInfo;
import com.rentequip.backend.identity.IdentityLookupClient;
import com.rentequip.backend.identity.RucInfo;
import com.rentequip.backend.identity.RucValidator;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public lookups for the registration form: type a RUC (20 or 10) or a DNI and the form fills in the
 * name from SUNAT or RENIEC. A RUC with a wrong check digit is refused before asking anyone.
 */
@RestController
@RequestMapping("/api/v1/identity")
@RequiredArgsConstructor
@Validated
public class IdentityController {

    private final IdentityLookupClient lookupClient;

    @GetMapping("/ruc/{ruc}")
    public ResponseEntity<RucInfo> findRuc(@PathVariable @Pattern(regexp = "\\d{11}", message = "El RUC tiene 11 dígitos") String ruc) {
        if (!RucValidator.isValid(ruc)) {
            throw new InvalidOperationException("El RUC no es válido: revisa los dígitos");
        }
        return ResponseEntity.ok(lookupClient.findRuc(ruc)
                .orElseThrow(() -> new ResourceNotFoundException("SUNAT no tiene registrado el RUC " + ruc)));
    }

    @GetMapping("/dni/{dni}")
    public ResponseEntity<DniInfo> findDni(@PathVariable @Pattern(regexp = "\\d{8}", message = "El DNI tiene 8 dígitos") String dni) {
        return ResponseEntity.ok(lookupClient.findDni(dni)
                .orElseThrow(() -> new ResourceNotFoundException("RENIEC no tiene registrado el DNI " + dni)));
    }
}
