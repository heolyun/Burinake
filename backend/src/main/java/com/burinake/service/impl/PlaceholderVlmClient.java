package com.burinake.service.impl;

import com.burinake.client.MultipartFormDataBuilder;
import com.burinake.config.AiProperties;
import com.burinake.dto.RiskLevel;
import com.burinake.dto.VlmResult;
import com.burinake.dto.YoloResult;
import com.burinake.dto.ai.VlmSummarizeResponse;
import com.burinake.service.VlmClient;
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
class HttpVlmClient implements VlmClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public HttpVlmClient(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
        this.baseUrl = aiProperties.vlmBaseUrl();
    }

    @Override
    public VlmResult summarize(Long imageId, String blobPath, YoloResult yoloResult, byte[] imageBytes, String contentType, String originalFilename) {
        try {
            String boundary = "----BurinakeBoundary" + ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
            String yoloJson = objectMapper.writeValueAsString(yoloResult);
            MultipartFormDataBuilder.MultipartBody body = MultipartFormDataBuilder.build(
                    boundary,
                    Map.of(
                            "imageId", imageId.toString(),
                            "blobPath", blobPath,
                            "yoloResult", yoloJson
                    ),
                    new MultipartFormDataBuilder.FilePart(
                            "image",
                            originalFilename,
                            contentType,
                            imageBytes
                    )
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/v1/fire/summary"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", body.contentType())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body.content()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("VLM server returned " + response.statusCode());
            }

            VlmSummarizeResponse parsed = objectMapper.readValue(response.body(), VlmSummarizeResponse.class);
            return new VlmResult(parsed.summary(), parsed.riskLevel(), parsed.recommendedAction());
        } catch (IOException ex) {
            return fallback(yoloResult);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return fallback(yoloResult);
        } catch (Exception ex) {
            return fallback(yoloResult);
        }
    }

    private VlmResult fallback(YoloResult yoloResult) {
        if (yoloResult.detected()) {
            return new VlmResult(
                    "Fire is detected. Smoke or flames appear to be present in the image.",
                    RiskLevel.HIGH,
                    "Evacuate immediately and call emergency services."
            );
        }

        return new VlmResult(
                "No strong fire signs were detected.",
                RiskLevel.LOW,
                "Continue monitoring."
        );
    }
}