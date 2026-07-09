package com.burinake.service.impl;

import com.burinake.client.MultipartFormDataBuilder;
import com.burinake.config.AiProperties;
import com.burinake.config.AzureOpenAiProperties;
import com.burinake.dto.RiskLevel;
import com.burinake.dto.VlmResult;
import com.burinake.dto.YoloResult;
import com.burinake.dto.ai.VlmSummarizeResponse;
import com.burinake.service.VlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
class AzureOpenAiVlmClient implements VlmClient {

    private static final Logger log = LoggerFactory.getLogger(AzureOpenAiVlmClient.class);

    private static final String DEFAULT_SYSTEM_PROMPT = """
            You are 'burinake VLM', a highly advanced AI core designed for a smart CCTV-based fire management and emergency dispatch system.
            You will receive one or more sequential frames captured immediately before and after a 1st-stage detector (YOLO) signaled a possible fire or smoke event.

            Output requirements:
            - Return only valid JSON.
            - Use this exact snake_case schema:
              {
                "fire_confirmed": true,
                "confidence": 0.86,
                "detected_bbox": {"x": 0, "y": 0, "width": 0, "height": 0, "label": "", "source": ""},
                "timeline_summary": [{"frame_index": 0, "time_offset_s": 0, "observation": ""}],
                "visual_cause": {"most_likely": "", "likely_ignition_mechanisms": [""], "confidence_explanation": ""},
                "fire_location_detail": {"cctv_id": "", "site_metadata_location": "", "captured_at": "", "precise_zone": ""},
                "risk_assessment": {"level": "", "rationale": "", "current_fire_size_estimate": "", "people_presence": ""},
                "recommended_actions": [""],
                "notes": "",
                "emergency_report_korean_narrative": ""
              }
            - If this is a false alarm, set fire_confirmed to false, keep emergency_report_korean_narrative empty, and keep recommended_actions minimal.
            - Keep the response concise, factual, and consistent with the provided YOLO and CCTV metadata.
            """;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AiProperties aiProperties;
    private final AzureOpenAiProperties azureOpenAiProperties;

    public AzureOpenAiVlmClient(
            AiProperties aiProperties,
            AzureOpenAiProperties azureOpenAiProperties,
            ObjectMapper objectMapper
    ) {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
        this.aiProperties = aiProperties;
        this.azureOpenAiProperties = azureOpenAiProperties;
    }

    @Override
    public VlmResult summarize(
            Long imageId,
            String blobPath,
            String cctvName,
            String cctvNum,
            String source,
            OffsetDateTime capturedAt,
            YoloResult yoloResult,
            byte[] imageBytes,
            String contentType,
            String originalFilename
    ) {
        return summarizeSequence(
                imageId,
                blobPath,
                cctvName,
                cctvNum,
                source,
                capturedAt,
                yoloResult,
                List.of(imageBytes),
                contentType,
                originalFilename
        );
    }

    public VlmResult summarizeSequence(
            Long imageId,
            String blobPath,
            String cctvName,
            String cctvNum,
            String source,
            OffsetDateTime capturedAt,
            YoloResult yoloResult,
            List<byte[]> imageSequence,
            String contentType,
            String originalFilename
    ) {
        if (imageSequence == null || imageSequence.isEmpty()) {
            return fallback(yoloResult);
        }

        if (supportsAzureOpenAi()) {
            try {
                return summarizeWithAzureOpenAi(
                        imageId,
                        blobPath,
                        cctvName,
                        cctvNum,
                        source,
                        capturedAt,
                        yoloResult,
                        imageSequence,
                        contentType,
                        originalFilename
                );
            } catch (Exception ex) {
                if (StringUtils.hasText(aiProperties.vlmBaseUrl())) {
                    try {
                        return summarizeWithLocalVlmServer(
                                imageId,
                                blobPath,
                                yoloResult,
                                imageSequence,
                                contentType,
                                originalFilename
                        );
                    } catch (Exception localFallbackEx) {
                        log.warn("azure-openai-vlm-fallback reason=local-vlm-failed-after-azure-failed", localFallbackEx);
                        return fallback(yoloResult);
                    }
                }

                log.warn("azure-openai-vlm-fallback reason=azure-failed", ex);
                return fallback(yoloResult);
            }
        }

        if (StringUtils.hasText(aiProperties.vlmBaseUrl())) {
            try {
                return summarizeWithLocalVlmServer(
                        imageId,
                        blobPath,
                        yoloResult,
                        imageSequence,
                        contentType,
                        originalFilename
                );
            } catch (Exception ex) {
                log.warn("azure-openai-vlm-fallback reason=local-vlm-failed", ex);
                return fallback(yoloResult);
            }
        }

        return fallback(yoloResult);
    }

    private VlmResult summarizeWithAzureOpenAi(
            Long imageId,
            String blobPath,
            String cctvName,
            String cctvNum,
            String source,
            OffsetDateTime capturedAt,
            YoloResult yoloResult,
            List<byte[]> imageSequence,
            String contentType,
            String originalFilename
    ) throws IOException, InterruptedException {
        String endpoint = normalizeEndpoint(azureOpenAiProperties.endpoint());
        String deploymentName = defaultDeploymentName();
        String apiVersion = defaultApiVersion();
        int maxCompletionTokens = defaultMaxCompletionTokens();

        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode messages = root.putArray("messages");

        ObjectNode systemMessage = messages.addObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", DEFAULT_SYSTEM_PROMPT);

        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        ArrayNode content = userMessage.putArray("content");
        content.addObject()
                .put("type", "text")
                .put("text", buildPromptText(imageId, blobPath, cctvName, cctvNum, source, capturedAt, yoloResult));

        for (int index = 0; index < imageSequence.size(); index++) {
            byte[] frame = Objects.requireNonNull(imageSequence.get(index), "imageSequence");
            String mimeType = normalizeContentType(contentType);
            String dataUrl = "data:%s;base64,%s".formatted(mimeType, Base64.getEncoder().encodeToString(frame));
            ObjectNode imageNode = content.addObject();
            imageNode.put("type", "image_url");
            ObjectNode imageUrlNode = imageNode.putObject("image_url");
            imageUrlNode.put("url", dataUrl);
        }

        root.put("max_completion_tokens", maxCompletionTokens);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("%s/openai/deployments/%s/chat/completions?api-version=%s".formatted(
                        endpoint,
                        deploymentName,
                        apiVersion
                )))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("api-key", requireApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(root), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("Azure OpenAI returned " + response.statusCode() + ": " + response.body());
        }

        JsonNode responseRoot = objectMapper.readTree(response.body());
        String rawContent = responseRoot.path("choices").path(0).path("message").path("content").asText("");
        if (!StringUtils.hasText(rawContent)) {
            log.warn("azure-openai-vlm-fallback reason=empty-response imageId={}", imageId);
            return fallback(yoloResult);
        }

        return parseVlmResponse(rawContent, yoloResult);
    }

    private VlmResult summarizeWithLocalVlmServer(
            Long imageId,
            String blobPath,
            YoloResult yoloResult,
            List<byte[]> imageSequence,
            String contentType,
            String originalFilename
    ) throws IOException, InterruptedException {
        if (imageSequence.isEmpty()) {
            return fallback(yoloResult);
        }

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
                        normalizeContentType(contentType),
                        imageSequence.get(0)
                )
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(aiProperties.vlmBaseUrl().replaceAll("/+$", "") + "/api/v1/fire/summary"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", body.contentType())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.content()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("VLM server returned " + response.statusCode() + ": " + response.body());
        }

        return objectMapper.readValue(response.body(), VlmResult.class);
    }

    private VlmResult parseVlmResponse(String rawContent, YoloResult yoloResult) {
        String cleanJsonText = rawContent
                .trim()
                .replaceAll("^```json\\s*", "")
                .replaceAll("^```\\s*", "")
                .replaceAll("\\s*```$", "")
                .trim();

        try {
            return objectMapper.readValue(cleanJsonText, VlmResult.class);
        } catch (Exception ex) {
            return fallback(yoloResult);
        }
    }

    private String buildPromptText(
            Long imageId,
            String blobPath,
            String cctvName,
            String cctvNum,
            String source,
            OffsetDateTime capturedAt,
            YoloResult yoloResult
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("Please conduct a comprehensive multi-frame analysis on the provided sequential images.\n\n");
        builder.append("[Contextual Data Provided]\n");
        builder.append("- 1st-stage YOLO Detector Raw Output: ")
                .append(safeJson(yoloResult))
                .append('\n');
        builder.append("- Fire event metadata:\n");
        builder.append("  - imageId: ").append(imageId).append('\n');
        builder.append("  - blobPath: ").append(blobPath).append('\n');
        if (StringUtils.hasText(cctvName)) {
            builder.append("  - cctvName: ").append(cctvName).append('\n');
        }
        if (StringUtils.hasText(cctvNum)) {
            builder.append("  - cctvNum: ").append(cctvNum).append('\n');
        }
        if (StringUtils.hasText(source)) {
            builder.append("  - source: ").append(source).append('\n');
        }
        if (capturedAt != null) {
            builder.append("  - capturedAt: ").append(capturedAt).append('\n');
        }
        builder.append('\n');
        builder.append("Review the specific regions indicated by YOLO coordinates, analyze the temporal progression, and output the structured data.");
        return builder.toString();
    }

    private String safeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private boolean supportsAzureOpenAi() {
        return StringUtils.hasText(azureOpenAiProperties.endpoint())
                && StringUtils.hasText(azureOpenAiProperties.apiKey());
    }

    private String requireApiKey() {
        if (!StringUtils.hasText(azureOpenAiProperties.apiKey())) {
            throw new IllegalStateException("AZURE_OPENAI_API_KEY is required");
        }
        return azureOpenAiProperties.apiKey();
    }

    private String normalizeEndpoint(String endpoint) {
        if (!StringUtils.hasText(endpoint)) {
            throw new IllegalStateException("AZURE_OPENAI_ENDPOINT is required");
        }
        return endpoint.replaceAll("/+$", "");
    }

    private String defaultDeploymentName() {
        return StringUtils.hasText(azureOpenAiProperties.deploymentName())
                ? azureOpenAiProperties.deploymentName()
                : "Burinake-vlm";
    }

    private String defaultApiVersion() {
        return StringUtils.hasText(azureOpenAiProperties.apiVersion())
                ? azureOpenAiProperties.apiVersion()
                : "2024-02-15-preview";
    }

    private int defaultMaxCompletionTokens() {
        return azureOpenAiProperties.maxCompletionTokens() != null
                ? azureOpenAiProperties.maxCompletionTokens()
                : 4000;
    }

    private String normalizeContentType(String contentType) {
        if (StringUtils.hasText(contentType)) {
            return contentType;
        }
        return "image/jpeg";
    }

    private VlmResult fallback(YoloResult yoloResult) {
        if (yoloResult.detected()) {
            return new VlmResult(
                    "화재 징후가 확인되었습니다. 연기나 불꽃으로 보이는 영역이 있습니다.",
                    RiskLevel.HIGH,
                    "즉시 대피하고 119에 신고하세요."
            );
        }

        return new VlmResult(
                "화재 징후가 뚜렷하지 않습니다. 현재 입력 기준으로는 이상 징후가 보이지 않습니다.",
                RiskLevel.LOW,
                "추가 모니터링을 권장합니다."
        );
    }
}
