package com.burinake.service.impl;

import java.time.LocalDate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

final class StoragePathBuilder {

    private StoragePathBuilder() {
    }

    static String snapshotPath(MultipartFile file, Long imageId, LocalDate capturedDate) {
        return path("snapshots", imageId, capturedDate, "original", extension(file, ".jpg"));
    }

    static String fireEventPath(MultipartFile file, Long imageId, LocalDate capturedDate) {
        return path("fire-events", imageId, capturedDate, "evidence", extension(file, ".jpg"));
    }

    static String reportPath(MultipartFile file, Long reportId, LocalDate createdDate) {
        return path("reports", reportId, createdDate, "report", extension(file, ".pdf"));
    }

    private static String path(String folder, Long id, LocalDate date, String basename, String extension) {
        return "%s/%d/%02d/%02d/%s/%s%s".formatted(
                folder,
                date.getYear(),
                date.getMonthValue(),
                date.getDayOfMonth(),
                id,
                basename,
                extension
        );
    }

    private static String extension(MultipartFile file, String defaultExtension) {
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename)) {
            return defaultExtension;
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return defaultExtension;
        }
        String extension = filename.substring(dotIndex).toLowerCase();
        return extension.length() > 16 ? defaultExtension : extension;
    }
}
