package com.spotlight.util;

import java.util.Arrays;
import java.util.List;

public class ProfanityFilter {

    // THE FIX: Dictionary words must be purely alphabetical
    // because our filter converts numbers to letters before checking!
    private static final List<String> BLOCKED_WORDS = Arrays.asList(
            "badword", "offensive", "inappropriate", "swearword"
    );

    /**
     * Checks if the given text contains any blocked words.
     */
    public static boolean containsProfanity(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        // 1. Convert to lowercase and translate "leet-speak" numbers back to letters
        String normalizedText = text.toLowerCase()
                .replace("1", "i")
                .replace("3", "e")
                .replace("4", "a")
                .replace("0", "o")
                .replace("@", "a")
                .replace("$", "s");

        // 2. Strip out all remaining punctuation and spaces
        String strippedText = normalizedText.replaceAll("[^a-z]", "");

        // 3. Check if the stripped text CONTAINS any of the blocked words
        for (String word : BLOCKED_WORDS) {
            if (strippedText.contains(word)) {
                return true;
            }
        }

        return false;
    }
}