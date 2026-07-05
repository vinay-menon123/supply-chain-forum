package com.cscen.forum.service;

import com.cscen.forum.web.ApiException;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Default {@link UploadStorage}: writes images to the local {@code uploads/}
 * directory, served back by {@code WebConfig} at {@code /uploads/**}.
 *
 * <p>Simple and dependency-free, but the files live on the instance's disk, so
 * this only works for a single instance. Swap to {@link AzureBlobUploadStorage}
 * (set {@code AZURE_STORAGE_CONNECTION_STRING}) before running multiple replicas.
 * Instantiated by {@code config.StorageConfig} — not component-scanned.
 */
public class LocalUploadStorage implements UploadStorage {

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp");

    private final File uploadDir = new File("uploads").getAbsoluteFile();

    public LocalUploadStorage() {
        uploadDir.mkdirs();
    }

    @Override
    public String saveImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw ApiException.badRequest("Only JPEG, PNG, GIF and WebP images are allowed");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot >= 0 ? original.substring(dot).toLowerCase() : "";
        String name = UUID.randomUUID() + ext;
        try {
            file.transferTo(new File(uploadDir, name));
        } catch (IOException e) {
            throw new ApiException(500, "Failed to store image");
        }
        return "/uploads/" + name;
    }
}
