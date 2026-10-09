package com.morpheus.provider.openspec;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The fenced code blocks of one OpenSpec Markdown file, marked line by line by the rules of upstream OpenSpec's
 * {@code buildCodeFenceMask}, the format these files are written for: a fence opens on a run of three or more
 * backticks or tildes after any whitespace, whatever follows, and closes only on a run of its own character at least
 * as long as the opening one with nothing but whitespace after it. Whitespace is JavaScript's {@code \s}. A fence that
 * never closes masks the rest of the file, as upstream does; unlike upstream, the line that opened it is kept, so the
 * caller can say that it read a file whose structure escaped it.
 *
 * <p>Both the current specification reader and the delta reader decide structure through this mask, so a heading
 * shown as an example inside a fence never starts or ends anything in either.</p>
 *
 * @param fenced          one entry per line, delimiters included
 * @param unclosedOpening the line that opened a fence still open at the end of the file, {@code -1} if none
 * @param unclosedRun     the run that opened that fence, empty if none
 */
record OpenSpecCodeFences(boolean[] fenced, int unclosedOpening, String unclosedRun) {
    private static final String UPSTREAM_WHITESPACE =
            "[\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]";
    private static final Pattern OPENING_FENCE = Pattern.compile(
            "^" + UPSTREAM_WHITESPACE + "*(`{3,}|~{3,})");
    private static final Pattern CLOSING_FENCE = Pattern.compile(
            "^" + UPSTREAM_WHITESPACE + "*(`{3,}|~{3,})" + UPSTREAM_WHITESPACE + "*$");

    static OpenSpecCodeFences of(List<String> lines) {
        boolean[] fenced = new boolean[lines.size()];
        String opening = null;
        int openingIndex = -1;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (opening == null) {
                Matcher fence = OPENING_FENCE.matcher(line);
                if (fence.lookingAt()) {
                    opening = fence.group(1);
                    openingIndex = index;
                    fenced[index] = true;
                }
                continue;
            }
            fenced[index] = true;
            Matcher fence = CLOSING_FENCE.matcher(line);
            if (fence.matches()
                    && fence.group(1).charAt(0) == opening.charAt(0)
                    && fence.group(1).length() >= opening.length()) {
                opening = null;
            }
        }
        return opening == null
                ? new OpenSpecCodeFences(fenced, -1, "")
                : new OpenSpecCodeFences(fenced, openingIndex, opening);
    }

    boolean isFenced(int index) {
        return fenced[index];
    }

    boolean hasUnclosedFence() {
        return unclosedOpening >= 0;
    }
}
