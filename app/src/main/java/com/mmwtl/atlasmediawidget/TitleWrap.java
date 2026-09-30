package com.mmwtl.atlasmediawidget;

/**
 * Splits a two-line title into a pyramid: lines of similar width with the lower one never
 * shorter, so the bottom-anchored text column keeps its weight next to the artwork. A short
 * word such as a preposition is not left hanging at the end of the upper line.
 */
final class TitleWrap {
    interface Measure { float width(String text); }

    private TitleWrap() {}

    /** Returns the text with one explicit line break, or unchanged when no two-line split fits. */
    static String pyramid(String text, float maxWidth, Measure measure) {
        if (maxWidth <= 0f || text.indexOf('\n') >= 0 || measure.width(text) <= maxWidth) {
            return text;
        }
        String[] words = text.trim().split(" +");
        String best = null;
        boolean bestPyramid = false;
        float bestDiff = Float.MAX_VALUE;
        for (int split = 1; split < words.length; split++) {
            if (isShortWord(words[split - 1])) continue;
            String head = String.join(" ", java.util.Arrays.copyOfRange(words, 0, split));
            String tail = String.join(" ", java.util.Arrays.copyOfRange(words, split, words.length));
            float headWidth = measure.width(head);
            float tailWidth = measure.width(tail);
            if (headWidth > maxWidth || tailWidth > maxWidth) continue;
            boolean pyramid = headWidth <= tailWidth;
            float diff = Math.abs(tailWidth - headWidth);
            if (best == null || pyramid && !bestPyramid
                    || pyramid == bestPyramid && diff < bestDiff) {
                best = head + "\n" + tail;
                bestPyramid = pyramid;
                bestDiff = diff;
            }
        }
        return best == null ? text : best;
    }

    private static boolean isShortWord(String word) {
        if (word.length() > 2) return false;
        for (int i = 0; i < word.length(); i++) {
            if (!Character.isLetter(word.charAt(i))) return false;
        }
        return true;
    }
}
