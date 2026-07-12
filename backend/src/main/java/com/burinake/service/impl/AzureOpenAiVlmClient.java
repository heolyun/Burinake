package com.burinake.service.impl;

import com.burinake.client.MultipartFormDataBuilder;
import com.burinake.config.AiProperties;
import com.burinake.config.AzureOpenAiProperties;
import com.burinake.dto.VlmResult;
import com.burinake.dto.YoloResult;
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
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
class AzureOpenAiVlmClient implements VlmClient {

    private static final String SYSTEM_PROMPT = """
            You are 'burinake VLM' for CCTV fire analysis.
            Return only valid JSON matching the VlmResult schema.
            All human-readable string values must be Korean.
            If analysis cannot be completed, do not invent a result.
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
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalArgumentException("VLM image bytes are required");
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
                        imageBytes,
                        contentType
                );
            } catch (Exception ex) {
                if (StringUtils.hasText(aiProperties.vlmBaseUrl())) {
                    try {
                        return summarizeWithLocalVlmServer(imageId, blobPath, yoloResult, imageBytes, contentType, originalFilename);
                    } catch (Exception localEx) {
                        throw new IllegalStateException("Azure OpenAI and local VLM both failed. azureError="
                                + rootCauseMessage(ex) + ", localError=" + rootCauseMessage(localEx), localEx);
                    }
                }
                throw new IllegalStateException("Azure OpenAI VLM failed: " + rootCauseMessage(ex), ex);
            }
        }

        if (StringUtils.hasText(aiProperties.vlmBaseUrl())) {
            try {
                return summarizeWithLocalVlmServer(imageId, blobPath, yoloResult, imageBytes, contentType, originalFilename);
            } catch (Exception ex) {
                throw new IllegalStateException("Local VLM failed: " + rootCauseMessage(ex), ex);
            }
        }

        throw new IllegalStateException("No VLM provider is configured");
    }

    private VlmResult summarizeWithAzureOpenAi(
            Long imageId,
            String blobPath,
            String cctvName,
            String cctvNum,
            String source,
            OffsetDateTime capturedAt,
            YoloResult yoloResult,
            byte[] imageBytes,
            String contentType
    ) throws IOException, InterruptedException {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode messages = root.putArray("messages");

        messages.addObject()
                .put("role", "system")
                .put("content", SYSTEM_PROMPT);

        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        ArrayNode content = userMessage.putArray("content");
        content.addObject()
                .put("type", "text")
                .put("text", buildPromptText(imageId, blobPath, cctvName, cctvNum, source, capturedAt, yoloResult));
        content.addObject()
                .put("type", "image_url")
                .putObject("image_url")
                .put("url", "data:%s;base64,%s".formatted(
                        normalizeContentType(contentType),
                        Base64.getEncoder().encodeToString(imageBytes)
                ));

        root.put("max_completion_tokens", defaultMaxCompletionTokens());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("%s/openai/deployments/%s/chat/completions?api-version=%s".formatted(
                        normalizeEndpoint(azureOpenAiProperties.endpoint()),
                        defaultDeploymentName(),
                        defaultApiVersion()
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
            throw new IllegalStateException("Azure OpenAI returned empty VLM response");
        }
        return parseVlmResponse(rawContent);
    }

    private VlmResult summarizeWithLocalVlmServer(
            Long imageId,
            String blobPath,
            YoloResult yoloResult,
            byte[] imageBytes,
            String contentType,
            String originalFilename
    ) throws IOException, InterruptedException {
        String boundary = "----BurinakeBoundary" + ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
        MultipartFormDataBuilder.MultipartBody body = MultipartFormDataBuilder.build(
                boundary,
                Map.of(
                        "imageId", imageId.toString(),
                        "blobPath", blobPath,
                        "yoloResult", objectMapper.writeValueAsString(yoloResult)
                ),
                new MultipartFormDataBuilder.FilePart(
                        "image",
                        StringUtils.hasText(originalFilename) ? originalFilename : "image.jpg",
                        normalizeContentType(contentType),
                        imageBytes
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

    private VlmResult parseVlmResponse(String rawContent) {
        String cleanJsonText = rawContent
                .trim()
                .replaceAll("^```json\\s*", "")
                .replaceAll("^```\\s*", "")
                .replaceAll("\\s*```$", "")
                .trim();

        try {
            return objectMapper.readValue(cleanJsonText, VlmResult.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse VLM JSON response: " + rawContent, ex);
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
        builder.append("Analyze this CCTV fire detection image and return structured JSON.\n");
        builder.append("imageId: ").append(imageId).append('\n');
        builder.append("blobPath: ").append(blobPath).append('\n');
        builder.append("cctvName: ").append(cctvName).append('\n');
        builder.append("cctvNum: ").append(cctvNum).append('\n');
        builder.append("source: ").append(source).append('\n');
        builder.append("capturedAt: ").append(capturedAt).append('\n');
        builder.append("yoloResult: ").append(safeJson(yoloResult)).append('\n');
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
        return StringUtils.hasText(contentType) ? contentType : "image/jpeg";
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return StringUtils.hasText(current.getMessage()) ? current.getMessage() : current.getClass().getSimpleName();
    }
}
