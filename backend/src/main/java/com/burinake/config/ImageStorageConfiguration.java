package com.burinake.config;

import com.burinake.service.ImageStorageService;
import com.burinake.service.impl.AzureBlobImageStorageService;
import com.burinake.service.impl.LocalImageStorageService;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class ImageStorageConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ImageStorageConfiguration.class);

    @Bean
    public ImageStorageService imageStorageService(AzureStorageProperties azureStorageProperties) {
        if (StringUtils.hasText(azureStorageProperties.connectionString())) {
            log.info("Using Azure Blob Storage for image storage. container={}", azureStorageProperties.blobContainer());
            return new AzureBlobImageStorageService(azureStorageProperties);
        }

        Path localRoot = Path.of(System.getProperty("java.io.tmpdir"), "burinake-storage");
        log.warn("AZURE_STORAGE_CONNECTION_STRING is not set. Using local file storage. rootPath={}", localRoot);
        return new LocalImageStorageService(localRoot);
    }
}
