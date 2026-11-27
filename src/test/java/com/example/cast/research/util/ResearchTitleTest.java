package com.example.cast.research.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResearchTitleTest {

    @Test
    void generatesTitleFromQuestion() {
        assertEquals(
                "Youth rent support",
                ResearchTitle.fromQuestion(
                        "Youth rent support?")
        );
    }

    @Test
    void normalizesWhitespace() {
        assertEquals(
                "Youth rent support",
                ResearchTitle.fromQuestion(
                        "  Youth\t rent\n support  ")
        );
    }

    @Test
    void normalizesUnicodeWhitespace() {
        assertEquals(
                "Youth rent",
                ResearchTitle.fromQuestion(
                        "Youth\u00A0\u3000rent")
        );
    }

    @Test
    void removesTrailingPunctuation() {
        assertEquals(
                "Youth rent",
                ResearchTitle.fromQuestion(
                        "Youth rent?!\uFF1F\uFF01\u3002")
        );
    }

    @Test
    void preservesInternalPunctuation() {
        assertEquals(
                "Rent: eligibility, amount",
                ResearchTitle.fromQuestion(
                        "Rent: eligibility, amount?")
        );
    }

    @Test
    void preservesExactlyFiftyCharacters() {
        String question = "a".repeat(50);

        assertEquals(
                question,
                ResearchTitle.fromQuestion(question)
        );
    }

    @Test
    void truncatesLongQuestion() {
        String question = "a".repeat(51);

        assertEquals(
                "a".repeat(49) + "\u2026",
                ResearchTitle.fromQuestion(question)
        );
    }

    @Test
    void handlesKoreanCharacters() {
        String question = "\uAC00".repeat(60);

        assertEquals(
                "\uAC00".repeat(49) + "\u2026",
                ResearchTitle.fromQuestion(question)
        );
    }

    @Test
    void doesNotSplitSupplementaryCharacters() {
        String emoji = "\uD83D\uDE00";
        String question = emoji.repeat(60);

        String title =
                ResearchTitle.fromQuestion(question);

        assertEquals(
                emoji.repeat(49) + "\u2026",
                title
        );

        assertEquals(
                50,
                title.codePointCount(0, title.length())
        );
    }

    @Test
    void removesWhitespaceBeforeEllipsis() {
        String question =
                "a".repeat(48) + " " + "b".repeat(10);

        assertEquals(
                "a".repeat(48) + "\u2026",
                ResearchTitle.fromQuestion(question)
        );
    }

    @Test
    void handlesNullQuestion() {
        assertEquals(
                "\uB9AC\uC11C\uCE58",
                ResearchTitle.fromQuestion(null)
        );
    }

    @Test
    void handlesBlankQuestion() {
        assertEquals(
                "\uB9AC\uC11C\uCE58",
                ResearchTitle.fromQuestion(" \t\n ")
        );
    }

    @Test
    void handlesPunctuationOnlyQuestion() {
        assertEquals(
                "\uB9AC\uC11C\uCE58",
                ResearchTitle.fromQuestion("?!...")
        );
    }

    @Test
    void resolvesMissingStoredTitle() {
        assertEquals(
                "Youth rent",
                ResearchTitle.resolve(
                        null, "Youth rent?")
        );
    }

    @Test
    void resolvesBlankStoredTitle() {
        assertEquals(
                "Youth rent",
                ResearchTitle.resolve(
                        " \t ", "Youth rent?")
        );
    }

    @Test
    void preservesExistingStoredTitle() {
        assertEquals(
                "Existing custom title",
                ResearchTitle.resolve(
                        "Existing custom title",
                        "Different question?")
        );
    }

    @Test
    void preservesExistingLongStoredTitle() {
        String storedTitle = "a".repeat(100);

        assertEquals(
                storedTitle,
                ResearchTitle.resolve(
                        storedTitle, "Question?")
        );
    }
}
