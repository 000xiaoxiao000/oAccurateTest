package com.oAT.web.api.ai;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AITokenUsageEstimator {

    private AITokenUsageEstimator() {
    }

    public static Map<String, Object> estimate(String input, String output) {
        int inputTokens = estimateTextTokens(input);
        int outputTokens = estimateTextTokens(output);
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("inputTokens", inputTokens);
        usage.put("outputTokens", outputTokens);
        usage.put("totalTokens", inputTokens + outputTokens);
        usage.put("estimated", true);
        return usage;
    }

    private static int estimateTextTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int tokens = 0;
        int latinRunLength = 0;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (Character.isWhitespace(current)) {
                tokens += flushLatinRun(latinRunLength);
                latinRunLength = 0;
                continue;
            }
            if (isCjk(current)) {
                tokens += flushLatinRun(latinRunLength) + 1;
                latinRunLength = 0;
                continue;
            }
            if (Character.isLetterOrDigit(current)) {
                latinRunLength++;
                continue;
            }
            tokens += flushLatinRun(latinRunLength) + 1;
            latinRunLength = 0;
        }
        tokens += flushLatinRun(latinRunLength);
        return Math.max(1, tokens);
    }

    private static int flushLatinRun(int length) {
        if (length <= 0) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(length / 4.0));
    }

    private static boolean isCjk(char current) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(current);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES;
    }
}
