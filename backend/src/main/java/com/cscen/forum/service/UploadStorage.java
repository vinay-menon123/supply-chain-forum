package com.cscen.forum.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Stores user-uploaded images and returns a public URL for each.
 *
 * <p>Two implementations exist, wired in {@code config.StorageConfig}:
 * <ul>
 *   <li>{@link LocalUploadStorage} — writes to the local {@code uploads/} dir
 *       (default; fine for a single instance / Railway).</li>
 *   <li>{@link AzureBlobUploadStorage} — uploads to Azure Blob Storage; enabled
 *       only when {@code AZURE_STORAGE_CONNECTION_STRING} is set. Object storage
 *       is what lets us run multiple replicas, since local disk isn't shared.</li>
 * </ul>
 * The switch is config-only — no controller code changes when we move to Azure.
 */
public interface UploadStorage {

    /** Saves a validated image and returns its public URL, or {@code null} when absent. */
    String saveImage(MultipartFile file);
}
