package com.rentequip.backend.controllers;

import com.rentequip.backend.services.ImageStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Duration;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class UploadController {

    private final ImageStorageService imageStorageService;

    /** Uploads one equipment photo and returns the public URL to put in the equipment's imageUrls. */
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PostMapping(path = "/api/v1/uploads/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        String storedName = imageStorageService.store(file);
        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/uploads/{name}").buildAndExpand(storedName).toUriString();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("url", url));
    }

    @GetMapping("/uploads/{name}")
    public ResponseEntity<Resource> serveImage(@PathVariable String name) {
        return ResponseEntity.ok()
                .contentType(imageStorageService.mediaTypeOf(name))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(imageStorageService.load(name));
    }
}
