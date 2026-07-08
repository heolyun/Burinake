package com.burinake.controller;

import com.burinake.dto.FireDetectionResponse;
import com.burinake.service.FireDetectionService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Profile("dev")
@RestController
public class FireDetectionDevController {

    private static final List<String> SUPPORTED_EXTENSIONS = List.of(".jpg", ".jpeg", ".png", ".webp");

    private final FireDetectionService fireDetectionService;
    private final String defaultTestImageDir;

    public FireDetectionDevController(
            FireDetectionService fireDetectionService,
            @Value("${burinake.test-image-dir:/app/test-images}") String defaultTestImageDir
    ) {
        this.fireDetectionService = fireDetectionService;
        this.defaultTestImageDir = defaultTestImageDir;
    }

    @PostMapping(value = "/api/v1/dev/fire-detections/sequence", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<DevSequenceResponse> analyzeSequence(
            @RequestParam(value = "folderPath", required = false) String folderPath,
            @RequestParam(value = "cctvName", required = false, defaultValue = "CCTV_TEST") String cctvName,
            @RequestParam(value = "cctvNum", required = false, defaultValue = "1") String cctvNum,
            @RequestParam(value = "source", required = false, defaultValue = "snapshot2-test") String source,
            @RequestParam(value = "capturedAt", required = false) OffsetDateTime capturedAt,
            @RequestParam(value = "intervalSeconds", required = false, defaultValue = "1") long intervalSeconds,
            @RequestParam(value = "limit", required = false, defaultValue = "5") int limit,
            @RequestParam(value = "dryRun", required = false, defaultValue = "false") boolean dryRun
    ) throws IOException {
        Path resolvedFolder = resolveFolder(folderPath);
        List<Path> imageFiles = listImageFiles(resolvedFolder);
        int effectiveLimit = limit <= 0 ? imageFiles.size() : Math.min(limit, imageFiles.size());
        List<Path> selectedFiles = imageFiles.subList(0, effectiveLimit);
        OffsetDateTime baseTime = capturedAt != null ? capturedAt : OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        List<FrameResult> results = new ArrayList<>();
        if (!dryRun) {
            for (int i = 0; i < selectedFiles.size(); i++) {
                Path imagePath = selectedFiles.get(i);
                OffsetDateTime frameTime = baseTime.plusSeconds(intervalSeconds * i);
                MultipartFile multipartFile = new PathMultipartFile(imagePath);
                FireDetectionResponse response = fireDetectionService.detectFire(
                        multipartFile,
                        cctvName,
                        cctvNum,
                        source,
                        frameTime
                );
                results.add(new FrameResult(
                        i + 1,
                        imagePath.getFileName().toString(),
                        frameTime,
                        response
                ));
            }
        }

        return ResponseEntity.ok(new DevSequenceResponse(
                resolvedFolder.toString(),
                imageFiles.size(),
                effectiveLimit,
                dryRun,
                cctvName,
                cctvNum,
                source,
                baseTime,
                intervalSeconds,
                selectedFiles.stream().map(path -> path.getFileName().toString()).toList(),
                results
        ));
    }

    private Path resolveFolder(String folderPath) {
        String candidate = folderPath;
        if (candidate == null || candidate.isBlank()) {
            candidate = defaultTestImageDir;
        }
        return Path.of(candidate);
    }

    private List<Path> listImageFiles(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            throw new IllegalArgumentException("folderPath is not a directory: " + folder);
        }

        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            for (Path path : stream) {
                if (Files.isRegularFile(path) && isSupportedImage(path)) {
                    files.add(path);
                }
            }
        }

        files.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return files;
    }

    private boolean isSupportedImage(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        for (String extension : SUPPORTED_EXTENSIONS) {
            if (name.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    public record DevSequenceResponse(
            String folderPath,
            int imageCount,
            int selectedCount,
            boolean dryRun,
            String cctvName,
            String cctvNum,
            String source,
            OffsetDateTime baseCapturedAt,
            long intervalSeconds,
            List<String> selectedFileNames,
            List<FrameResult> frames
    ) {
    }

    public record FrameResult(
            int frameIndex,
            String fileName,
            OffsetDateTime capturedAt,
            FireDetectionResponse response
    ) {
    }

    private static final class PathMultipartFile implements MultipartFile {
        private final Path path;

        private PathMultipartFile(Path path) {
            this.path = path;
        }

        @Override
        public String getName() {
            return "image";
        }

        @Override
        public String getOriginalFilename() {
            return path.getFileName().toString();
        }

        @Override
        public String getContentType() {
            String contentType = null;
            try {
                contentType = Files.probeContentType(path);
            } catch (IOException ignored) {
                // Fall through to the default image MIME type.
            }
            return contentType != null ? contentType : "image/jpeg";
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public long getSize() {
            try {
                return Files.size(path);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to read file size: " + path, ex);
            }
        }

        @Override
        public byte[] getBytes() throws IOException {
            return Files.readAllBytes(path);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return Files.newInputStream(path);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            Files.copy(path, dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
