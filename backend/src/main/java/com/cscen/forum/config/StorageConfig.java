package com.cscen.forum.config;

import com.cscen.forum.service.AzureBlobUploadStorage;
import com.cscen.forum.service.LocalUploadStorage;
import com.cscen.forum.service.UploadStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the {@link UploadStorage} implementation at startup.
 *
 * <p>Mirrors the {@code MailService} "no-op until configured" pattern: if
 * {@code AZURE_STORAGE_CONNECTION_STRING} is present we upload to Azure Blob;
 * otherwise we fall back to local disk. Nothing else in the app changes — the
 * whole Azure migration for uploads is this one env var. (Relaxed binding maps
 * the {@code AZURE_STORAGE_CONNECTION_STRING} env var to the dotted property.)
 */
@Configuration
public class StorageConfig {

    @Bean
    @ConditionalOnProperty(name = "azure.storage.connection-string")
    public UploadStorage azureUploadStorage(
            @Value("${azure.storage.connection-string}") String connectionString,
            @Value("${azure.storage.container:forum-uploads}") String container) {
        return new AzureBlobUploadStorage(connectionString, container);
    }

    @Bean
    @ConditionalOnMissingBean(UploadStorage.class)
    public UploadStorage localUploadStorage() {
        return new LocalUploadStorage();
    }
}
