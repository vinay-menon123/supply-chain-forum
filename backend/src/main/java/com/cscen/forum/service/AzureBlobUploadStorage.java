package com.cscen.forum.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.PublicAccessType;
import com.cscen.forum.web.ApiException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

/**
 * {@link UploadStorage} backed by Azure Blob Storage. Enabled only when
 * {@code AZURE_STORAGE_CONNECTION_STRING} is set (see {@code config.StorageConfig});
 * otherwise {@link LocalUploadStorage} is used. Because blobs live off-instance,
 * this is what unblocks running multiple app replicas behind a load balancer.
 *
 * <p>Returns the blob's absolute public URL, which the frontend renders directly
 * in an {@code <img src>} (local mode returns a relative {@code /uploads/...} path
 * instead — both work). This assumes the container allows anonymous blob reads;
 * if the storage account disables that, switch to SAS URLs here at migration.
 */
public class AzureBlobUploadStorage implements UploadStorage {

    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp");

    private final BlobContainerClient container;

    public AzureBlobUploadStorage(String connectionString, String containerName) {
        BlobServiceClient service = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();
        BlobContainerClient c = service.getBlobContainerClient(containerName);
        if (!c.exists()) {
            c.createWithResponse(null, PublicAccessType.BLOB, null, null);
        }
        this.container = c;
    }

    @Override
    public String saveImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String contentType = file.getContentType();
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw ApiException.badRequest("Only JPEG, PNG, GIF and WebP images are allowed");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot >= 0 ? original.substring(dot).toLowerCase() : "";
        String name = UUID.randomUUID() + ext;
        BlobClient blob = container.getBlobClient(name);
        try (InputStream in = file.getInputStream()) {
            blob.upload(in, file.getSize(), true);
            blob.setHttpHeaders(new BlobHttpHeaders().setContentType(contentType));
        } catch (IOException e) {
            throw new ApiException(500, "Failed to store image");
        }
        return blob.getBlobUrl();
    }
}
