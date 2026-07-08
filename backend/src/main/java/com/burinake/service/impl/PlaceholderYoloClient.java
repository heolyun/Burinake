package com.burinake.service.impl;

import com.burinake.client.MultipartFormDataBuilder;
import com.burinake.config.AiProperties;
import com.burinake.dto.BoundingBox;
import com.burinake.dto.YoloResult;
import com.burinake.dto.ai.YoloDetectResponse;
import com.burinake.service.YoloClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
class HttpYoloClient implements YoloClient {

    private static final Logger log = LoggerFactory.getLogger(HttpYoloClient.class);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public HttpYoloClient(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
        this.baseUrl = aiProperties.yoloBaseUrl();
    }

    @Override
    public YoloResult analyze(
            Long imageId,
            String blobPath,
            OffsetDateTime capturedAt,
            byte[] imageBytes,
            String contentType,
            String originalFilename
    ) {
        if (!StringUtils.hasText(baseUrl)) {
            log.warn("yolo-request-skipped imageId={} reason=missing-base-url", imageId);
            return new YoloResult(false, null, List.of());
        }

        try {
            long startNanos = System.nanoTime();
            String boundary = "----BurinakeBoundary" + ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
            MultipartFormDataBuilder.MultipartBody body = MultipartFormDataBuilder.build(
                    boundary,
                    Map.of(
                            "imageId", imageId.toString(),
                            "capturedAt", capturedAt.toString()
                    ),
                    new MultipartFormDataBuilder.FilePart(
                            "image",
                            originalFilename,
                            contentType,
                            imageBytes
                    )
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(baseUrl) + "/api/detect"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", body.contentType())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.content()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("yolo-request-complete imageId={} statusCode={} elapsedMs={}", imageId, response.statusCode(), elapsedMillis(startNanos));
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("YOLO server returned " + response.statusCode());
            }

            YoloDetectResponse parsed = objectMapper.readValue(response.body(), YoloDetectResponse.class);
            List<BoundingBox> boxes = parsed.result() != null && parsed.result().boxes() != null
                    ? parsed.result().boxes()
                    : List.of();
            boolean detected = parsed.result() != null && parsed.result().detected();
            return new YoloResult(detected, maxScore(boxes), boxes);
        } catch (IOException ex) {
            return new YoloResult(false, null, List.of());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new YoloResult(false, null, List.of());
        } catch (Exception ex) {
            return new YoloResult(false, null, List.of());
        }
    }

    private String normalizeBaseUrl(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private Double maxScore(List<BoundingBox> boxes) {
        return boxes.stream()
                .map(BoundingBox::score)
                .filter(score -> score != null)
                .max(Double::compareTo)
                .orElse(null);
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
