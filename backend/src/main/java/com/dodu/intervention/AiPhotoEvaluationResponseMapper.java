package com.dodu.intervention;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/** Converts the AI endpoint's label response into the Dodu attempt outcome contract. */
public final class AiPhotoEvaluationResponseMapper {
    private final ObjectMapper objectMapper;

    public AiPhotoEvaluationResponseMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PhotoEvaluationRecoveryHandler.RecoveryResult map(int status, String body) {
        if (status == 408 || status == 429 || status >= 500) {
            return PhotoEvaluationRecoveryHandler.RecoveryResult.retryable("AI_HTTP_" + status);
        }
        if (status < 200 || status >= 300) {
            return PhotoEvaluationRecoveryHandler.RecoveryResult.technicalFailure("AI_HTTP_" + status);
        }
        try {
            JsonNode response = objectMapper.readTree(body);
            Optional<String> label = findLabel(response);
            if (label.isEmpty()) return PhotoEvaluationRecoveryHandler.RecoveryResult.retryable("AI_RESPONSE_INVALID");
            String modelVersion = findText(response, "model_version").orElse(null);
            return switch (label.get()) {
                case "laptop", "book" -> PhotoEvaluationRecoveryHandler.RecoveryResult.completed(modelVersion);
                case "other" -> PhotoEvaluationRecoveryHandler.RecoveryResult.rejected("AI_OTHER", modelVersion);
                default -> PhotoEvaluationRecoveryHandler.RecoveryResult.retryable("AI_LABEL_UNSUPPORTED");
            };
        } catch (IOException | RuntimeException ignored) {
            return PhotoEvaluationRecoveryHandler.RecoveryResult.retryable("AI_RESPONSE_INVALID");
        }
    }

    private Optional<String> findLabel(JsonNode node) {
        if (node == null) return Optional.empty();
        if (node.isTextual()) {
            return switch (node.textValue()) {
                case "laptop", "book", "other" -> Optional.of(node.textValue());
                default -> Optional.empty();
            };
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                Optional<String> found = findLabel(child);
                if (found.isPresent()) return found;
            }
        } else if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Optional<String> found = findLabel(fields.next().getValue());
                if (found.isPresent()) return found;
            }
        }
        return Optional.empty();
    }

    private Optional<String> findText(JsonNode node, String fieldName) {
        if (node == null) return Optional.empty();
        if (node.isObject() && node.has(fieldName) && node.get(fieldName).isTextual()) {
            return Optional.of(node.get(fieldName).textValue());
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                Optional<String> found = findText(child, fieldName);
                if (found.isPresent()) return found;
            }
        } else if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Optional<String> found = findText(fields.next().getValue(), fieldName);
                if (found.isPresent()) return found;
            }
        }
        return Optional.empty();
    }
}
