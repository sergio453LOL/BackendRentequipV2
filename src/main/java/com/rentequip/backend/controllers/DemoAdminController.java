package com.rentequip.backend.controllers;

import com.rentequip.backend.security.PlatformAdmins;
import com.rentequip.backend.services.DemoResetService;
import com.rentequip.backend.services.DemoResetService.DemoResetResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Platform panel: upkeep of the shared demo account. */
@RestController
@RequestMapping("/api/v1/admin/demo")
@RequiredArgsConstructor
public class DemoAdminController {

    private final DemoResetService demoResetService;

    /** Cancels what the visitors left open in the demo account (reservations, offers, purchases). */
    @PreAuthorize(PlatformAdmins.EXPRESSION)
    @PostMapping("/reset")
    public ResponseEntity<DemoResetResult> reset() {
        return ResponseEntity.ok(demoResetService.reset());
    }
}
