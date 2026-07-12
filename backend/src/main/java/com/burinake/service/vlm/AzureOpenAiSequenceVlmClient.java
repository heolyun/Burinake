package com.burinake.service.vlm;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.burinake.config.AzureOpenAiProperties;
import com.burinake.config.AzureStorageProperties;
import com.burinake.dto.YoloResult;
import com.burinake.dto.vlm.VlmAnalysisResponse;
import com.burinake.dto.vlm.VlmCctvMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AzureOpenAiSequenceVlmClient {

    private static final Logger log = LoggerFactory.getLogger(AzureOpenAiSequenceVlmClient.class);

    private static final String SYSTEM_PROMPT = """
            You are 'burinake VLM' for CCTV fire analysis.
            You will receive:
            - yolo_detection_result
            - cctv_metadata
            - a time-ordered image sequence captured immediately before and/or at the moment a 1st-stage YOLO detector signaled possible fire or smoke.

            Your crucial missions:
            1. Strict false alarm filtering:
               - Standard kitchen activities such as steam or smoke from cooking, red glowing neon signs, car taillights, strong sunsets, reflected lights, dust, fog, or camera artifacts can trigger false alarms.
               - Use the temporal image sequence and YOLO bounding boxes together to determine whether the scene is a genuine hazardous fire or a harmless false alarm.
            2. Comprehensive fire inference:
               - If this is a true fire, assess the current risk level, identify the exact fire or smoke source zone, and infer the most likely visible ignition cause.
               - Give special attention to growth, spread, persistence, smoke thickening, and whether fire/smoke appears attached to a physical source.
            3. Automated 119 emergency report text:
               - If this is a true fire, compose a professional Korean narrative emergency report draft using 5W1H.
               - Heavily incorporate CCTV location and captured time metadata so first responders can identify the site immediately.
               - If this is a false alarm, keep emergency_report_korean_narrative as an empty string.

            Language requirements:
            - All human-readable string values must be Korean.
            - Keep fixed enum-like values such as risk_assessment.level in English: HIGH, MEDIUM, LOW, or UNKNOWN.
            - Field names must remain exactly as specified in the schema.

            Output constraints:
            - Return only a valid JSON object.
            - No conversational headers.
            - No markdown blocks.
            - No triple backticks.
            - Follow this exact snake_case schema:
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
            """;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AzureOpenAiProperties openAiProperties;
    private final AzureStorageProperties storageProperties;

    public AzureOpenAiSequenceVlmClient(
            AzureOpenAiProperties openAiProperties,
            AzureStorageProperties storageProperties,
            ObjectMapper objectMapper
    ) {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = objectMapper;
        this.openAiProperties = openAiProperties;
        this.storageProperties = storageProperties;
    }

    public VlmAnalysisResponse analyze(
            YoloResult yoloDetectionResult,
            VlmCctvMetadata cctvMetadata,
            List<String> imageSequencePaths
    ) {
        try {
            return analyzeInternal(yoloDetectionResult, cctvMetadata, imageSequencePaths);
        } catch (Exception ex) {
            log.warn("azure-openai-sequence-vlm-failed reason=analysis-failed", ex);
            throw new IllegalStateException("VLM 분석 실패: " + rootCauseMessage(ex), ex);
        }
    }

    public JsonNode analyzeAsJson(
            YoloResult yoloDetectionResult,
            VlmCctvMetadata cctvMetadata,
            List<String> imageSequencePaths
    ) {
        return objectMapper.valueToTree(analyze(yoloDetectionResult, cctvMetadata, imageSequencePaths));
    }

    private VlmAnalysisResponse analyzeInternal(
            YoloResult yoloDetectionResult,
            VlmCctvMetadata cctvMetadata,
            List<String> imageSequencePaths
    ) throws IOException, InterruptedException {
        if (imageSequencePaths == null || imageSequencePaths.isEmpty()) {
            throw new IllegalArgumentException("VLM image sequence is required");
        }

        if (!StringUtils.hasText(openAiProperties.endpoint()) || !StringUtils.hasText(openAiProperties.apiKey())) {
            throw new IllegalStateException("AZURE_OPENAI_ENDPOINT and AZURE_OPENAI_API_KEY are required");
        }

        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode messages = root.putArray("messages");

        ObjectNode systemMessage = messages.addObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", SYSTEM_PROMPT);

        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        ArrayNode content = userMessage.putArray("content");
        content.addObject()
                .put("type", "text")
                .put("text", buildPromptText(yoloDetectionResult, cctvMetadata));

        for (String imageSequencePath : imageSequencePaths) {
            ImagePayload payload = loadImagePayload(imageSequencePath);
            ObjectNode imageNode = content.addObject();
            imageNode.put("type", "image_url");
            imageNode.putObject("image_url")
                    .put("url", "data:%s;base64,%s".formatted(payload.contentType(), payload.base64()));
        }

        root.put("max_completion_tokens", defaultMaxCompletionTokens());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(normalizeEndpoint(openAiProperties.endpoint()) + "/openai/deployments/%s/chat/completions?api-version=%s".formatted(
                        defaultDeploymentName(),
                        defaultApiVersion()
                )))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/json")
                .header("api-key", openAiProperties.apiKey())
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

        String cleanJson = stripCodeFence(rawContent);
        try {
            return objectMapper.readValue(cleanJson, VlmAnalysisResponse.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse VLM JSON response: " + rawContent, ex);
        }
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return StringUtils.hasText(current.getMessage()) ? current.getMessage() : current.getClass().getSimpleName();
    }

    private String buildPromptText(YoloResult yoloDetectionResult, VlmCctvMetadata cctvMetadata) {
        StringBuilder builder = new StringBuilder();
        builder.append("Please conduct a comprehensive multi-frame analysis on the provided sequential images.\n\n");
        builder.append("[Contextual Data Provided]\n");
        builder.append("- 1st-stage YOLO Detector Raw Output: ").append(safeJson(yoloDetectionResult)).append('\n');
        builder.append("- System CCTV & Time Metadata: ").append(safeJson(cctvMetadata)).append('\n');
        builder.append('\n');
        builder.append("Review the specific regions indicated by YOLO coordinates, analyze the temporal progression, and output the structured data in the required schema.");
        return builder.toString();
    }

    private ImagePayload loadImagePayload(String imageSequencePath) throws IOException, InterruptedException {
        byte[] bytes = loadImageBytes(imageSequencePath);
        String contentType = detectContentType(imageSequencePath, bytes);
        return new ImagePayload(Base64.getEncoder().encodeToString(bytes), contentType);
    }

    private byte[] loadImageBytes(String imageSequencePath) throws IOException, InterruptedException {
        if (!StringUtils.hasText(imageSequencePath)) {
            throw new IllegalArgumentException("imageSequencePath is required");
        }

        if (imageSequencePath.startsWith("http://") || imageSequencePath.startsWith("https://")) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(imageSequencePath))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Failed to download image: " + imageSequencePath);
            }
            return response.body();
        }

        Path localPath = imageSequencePath.startsWith("file:")
                ? Path.of(URI.create(imageSequencePath))
                : Path.of(imageSequencePath);
        if (Files.exists(localPath)) {
            return Files.readAllBytes(localPath);
        }

        for (String localRoot : List.of("burinake-storage", "burinake-fire-events")) {
            Path tempRootLocalPath = Path.of(System.getProperty("java.io.tmpdir"), localRoot).resolve(imageSequencePath);
            if (Files.exists(tempRootLocalPath)) {
                return Files.readAllBytes(tempRootLocalPath);
            }
        }

        return loadFromBlobPath(imageSequencePath);
    }

    private byte[] loadFromBlobPath(String blobPath) throws IOException {
        if (!StringUtils.hasText(storageProperties.connectionString())
                || !StringUtils.hasText(storageProperties.blobContainer())) {
            for (String localRoot : List.of("burinake-storage", "burinake-fire-events")) {
                Path localCandidatePath = Path.of(System.getProperty("java.io.tmpdir"), localRoot).resolve(blobPath);
                if (Files.exists(localCandidatePath)) {
                    return Files.readAllBytes(localCandidatePath);
                }
            }
            throw new IllegalStateException("AZURE_STORAGE_CONNECTION_STRING and AZURE_BLOB_CONTAINER are required");
        }

        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(storageProperties.connectionString())
                .buildClient();
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(storageProperties.blobContainer());
        BlobClient blobClient = containerClient.getBlobClient(blobPath);
        return blobClient.downloadContent().toBytes();
    }

    private String detectContentType(String imageSequencePath, byte[] bytes) {
        String byPath = URLConnection.guessContentTypeFromName(imageSequencePath);
        if (StringUtils.hasText(byPath)) {
            return byPath;
        }
        try {
            String byBytes = URLConnection.guessContentTypeFromStream(new java.io.ByteArrayInputStream(bytes));
            if (StringUtils.hasText(byBytes)) {
                return byBytes;
            }
        } catch (IOException ignored) {
            // Fall through to the default image MIME type.
        }
        return "image/jpeg";
    }

    private String safeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String stripCodeFence(String raw) {
        return raw.trim()
                .replaceAll("^```json\\s*", "")
                .replaceAll("^```\\s*", "")
                .replaceAll("\\s*```$", "")
                .trim();
    }

    private String normalizeEndpoint(String endpoint) {
        return endpoint.replaceAll("/+$", "");
    }

    private String defaultDeploymentName() {
        return StringUtils.hasText(openAiProperties.deploymentName())
                ? openAiProperties.deploymentName()
                : "Burinake-vlm";
    }

    private String defaultApiVersion() {
        return StringUtils.hasText(openAiProperties.apiVersion())
                ? openAiProperties.apiVersion()
                : "2024-02-15-preview";
    }

    private int defaultMaxCompletionTokens() {
        return openAiProperties.maxCompletionTokens() != null
                ? openAiProperties.maxCompletionTokens()
                : 4000;
    }

    private record ImagePayload(String base64, String contentType) {
    }
}
