package com.example.cast.research.util;

public final class ResearchTitle {

    private static final int MAX_LENGTH = 50;

    private ResearchTitle() {}

    public static String fromQuestion(String question) {
        String normalized = question == null
                ? ""
                : question.replaceAll("(?U)\\s+", " ").strip();

        normalized = normalized
                .replaceAll("[?!.\\uFF1F\\uFF01\\u3002]+$", "")
                .strip();

        if (normalized.isEmpty()) {
            return "\uB9AC\uC11C\uCE58";
        }

        int length = normalized.codePointCount(
                0, normalized.length());

        if (length <= MAX_LENGTH) {
            return normalized;
        }

        int end = normalized.offsetByCodePoints(
                0, MAX_LENGTH - 1);

        return normalized.substring(0, end)
                .stripTrailing() + "\u2026";
    }

    public static String resolve(
            String storedTitle, String question
    ) {
        if (storedTitle != null
                && !storedTitle.isBlank()) {
            return storedTitle;
        }

        return fromQuestion(question);
    }
}
