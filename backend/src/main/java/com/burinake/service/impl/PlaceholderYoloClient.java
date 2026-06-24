package com.burinake.service.impl;

import com.burinake.client.MultipartFormDataBuilder;
import com.burinake.config.AiProperties;
import com.burinake.dto.YoloResult;
import com.burinake.dto.ai.YoloAnalyzeResponse;
import com.burinake.service.YoloClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

@Service
class HttpYoloClient implements YoloClient {

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
    public YoloResult analyze(Long imageId, String blobPath, byte[] imageBytes, String contentType, String originalFilename) {
        try {
            String boundary = "----BurinakeBoundary" + ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
            MultipartFormDataBuilder.MultipartBody body = MultipartFormDataBuilder.build(
                    boundary,
                    Map.of(
                            "imageId", imageId.toString(),
                            "blobPath", blobPath
                    ),
                    new MultipartFormDataBuilder.FilePart(
                            "image",
                            originalFilename,
                            contentType,
                            imageBytes
                    )
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/fire/analyze"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", body.contentType())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.content()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("YOLO server returned " + response.statusCode());
            }

            YoloAnalyzeResponse parsed = objectMapper.readValue(response.body(), YoloAnalyzeResponse.class);
            return new YoloResult(parsed.detected(), parsed.confidence(), parsed.boxes());
        } catch (IOException ex) {
            return new YoloResult(false, null, List.of());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new YoloResult(false, null, List.of());
        } catch (Exception ex) {
            return new YoloResult(false, null, List.of());
        }
    }
}
