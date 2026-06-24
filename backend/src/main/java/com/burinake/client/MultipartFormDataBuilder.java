package com.burinake.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class MultipartFormDataBuilder {

    private MultipartFormDataBuilder() {
    }

    public static MultipartBody build(String boundary, Map<String, String> fields, FilePart filePart) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            for (Map.Entry<String, String> entry : fields.entrySet()) {
                writeTextPart(output, boundary, entry.getKey(), entry.getValue());
            }

            if (filePart != null) {
                writeFilePart(output, boundary, filePart);
            }

            output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            return new MultipartBody(output.toByteArray(), "multipart/form-data; boundary=" + boundary);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build multipart body", ex);
        }
    }

    private static void writeTextPart(ByteArrayOutputStream output, String boundary, String name, String value)
            throws IOException {
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        output.write("Content-Type: text/plain; charset=UTF-8\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        output.write(value.getBytes(StandardCharsets.UTF_8));
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFilePart(ByteArrayOutputStream output, String boundary, FilePart filePart)
            throws IOException {
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        output.write((
                "Content-Disposition: form-data; name=\"" + filePart.fieldName()
                        + "\"; filename=\"" + filePart.fileName() + "\"\r\n"
        ).getBytes(StandardCharsets.UTF_8));
        output.write(("Content-Type: " + filePart.contentType() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        output.write(filePart.content());
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    public record FilePart(String fieldName, String fileName, String contentType, byte[] content) {
        public FilePart {
            Objects.requireNonNull(fieldName, "fieldName");
            Objects.requireNonNull(fileName, "fileName");
            Objects.requireNonNull(contentType, "contentType");
            Objects.requireNonNull(content, "content");
        }
    }

    public record MultipartBody(byte[] content, String contentType) {
    }
}
