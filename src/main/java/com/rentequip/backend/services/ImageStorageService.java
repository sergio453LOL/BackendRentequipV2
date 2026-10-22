package com.rentequip.backend.services;

import com.rentequip.backend.exceptions.InvalidOperationException;
import com.rentequip.backend.exceptions.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stores equipment photos on the local disk. Files are renamed to a random UUID and their type is checked
 * against the first bytes of the content, so neither the client's file name nor its declared content type
 * is trusted.
 */
@Service
public class ImageStorageService {

    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;
    private static final Pattern STORED_NAME = Pattern.compile("^[0-9a-f]{32}\\.(jpg|png|webp)$");
    private static final Map<String, MediaType> MEDIA_TYPES = Map.of(
            "jpg", MediaType.IMAGE_JPEG,
            "png", MediaType.IMAGE_PNG,
            "webp", MediaType.parseMediaType("image/webp"));

    private final Path root;

    public ImageStorageService(@Value("${rentequip.uploads.dir}") String uploadsDir) {
        this.root = Path.of(uploadsDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot create the uploads directory " + root, exception);
        }
    }

    /** Saves the image and returns the name it is served under. */
    public String store(MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidOperationException("The image is empty");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new InvalidOperationException("The image must be 5 MB or smaller");
        }
        String extension = detectExtension(file);
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, root.resolve(storedName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not store the image", exception);
        }
        return storedName;
    }

    public Resource load(String storedName) {
        if (!STORED_NAME.matcher(storedName).matches()) {
            throw new ResourceNotFoundException("Image not found: " + storedName);
        }
        try {
            Resource resource = new UrlResource(root.resolve(storedName).toUri());
            if (!resource.exists()) {
                throw new ResourceNotFoundException("Image not found: " + storedName);
            }
            return resource;
        } catch (MalformedURLException exception) {
            throw new ResourceNotFoundException("Image not found: " + storedName);
        }
    }

    public MediaType mediaTypeOf(String storedName) {
        String extension = storedName.substring(storedName.lastIndexOf('.') + 1);
        return MEDIA_TYPES.getOrDefault(extension, MediaType.APPLICATION_OCTET_STREAM);
    }

    private String detectExtension(MultipartFile file) {
        byte[] header;
        try (InputStream input = file.getInputStream()) {
            header = input.readNBytes(12);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read the image", exception);
        }
        if (startsWith(header, 0xFF, 0xD8, 0xFF)) return "jpg";
        if (startsWith(header, 0x89, 'P', 'N', 'G')) return "png";
        if (header.length >= 12 && startsWith(header, 'R', 'I', 'F', 'F')
                && Arrays.equals(Arrays.copyOfRange(header, 8, 12), new byte[]{'W', 'E', 'B', 'P'})) {
            return "webp";
        }
        throw new InvalidOperationException("Only JPG, PNG or WEBP images are allowed");
    }

    private boolean startsWith(byte[] data, int... expected) {
        if (data.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if ((data[i] & 0xFF) != expected[i]) return false;
        }
        return true;
    }
}
