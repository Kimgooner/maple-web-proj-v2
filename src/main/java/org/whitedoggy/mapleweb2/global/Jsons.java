package org.whitedoggy.mapleweb2.global;

import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Optional;

public final class Jsons {

    private Jsons() {
    }

    public static boolean empty(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode();
    }

    public static String text(JsonNode node, String field) {
        if (empty(node)) {
            return "";
        }
        return node.path(field).asText("");
    }

    public static Optional<String> optionalText(JsonNode node, String field) {
        if (empty(node) || !node.hasNonNull(field)) {
            return Optional.empty();
        }
        String value = node.get(field).asText();
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    public static Optional<Integer> optionalInt(JsonNode node, String field) {
        if (empty(node) || !node.hasNonNull(field)) {
            return Optional.empty();
        }
        return Optional.of(node.get(field).asInt());
    }

    public static int parseInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        String normalized = value.replace(",", "").trim();
        if (normalized.endsWith("%")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        try {
            return (int) Math.floor(Double.parseDouble(normalized));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0.0;
        }
        String normalized = value.replace(",", "").trim();
        if (normalized.endsWith("%")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    public static Iterable<JsonNode> array(JsonNode node, String field) {
        if (empty(node)) {
            return List.of();
        }
        JsonNode arrayNode = node.path(field);
        if (!arrayNode.isArray()) {
            return List.of();
        }
        return arrayNode;
    }
}
