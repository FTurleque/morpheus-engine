package com.morpheus.architecture;

/**
 * The code of a Java source, for the gates that read the test sources of the suite as text.
 *
 * <p>Comments, text blocks and character literals are blanked; string literals are blanked or kept, as the caller
 * asks. Blanking replaces each character by a space and keeps line breaks, so an offset or a line number in the result
 * is the same in the source. A text block is blanked in both modes: the suite writes its fixtures in text blocks, and
 * a fixture that holds the thing a gate looks for is not an instance of it.</p>
 */
final class TestSourceText {

    private TestSourceText() {
    }

    /** The source with comments and every literal blanked: only code remains. */
    static String code(String source) {
        return blanked(source, false);
    }

    /** The source with comments, text blocks and character literals blanked, and string literals kept as written. */
    static String codeAndStrings(String source) {
        return blanked(source, true);
    }

    private static String blanked(String source, boolean keepStrings) {
        StringBuilder result = new StringBuilder(source.length());
        int index = 0;
        while (index < source.length()) {
            if (source.startsWith("//", index)) {
                index = blankComment(source, result, index, source.indexOf('\n', index), "\n");
            } else if (source.startsWith("/*", index)) {
                index = blankComment(source, result, index, source.indexOf("*/", index + 2), "*/");
            } else if (source.startsWith("\"\"\"", index)) {
                index = literal(source, result, index, "\"\"\"", false);
            } else if (source.charAt(index) == '"') {
                index = literal(source, result, index, "\"", keepStrings);
            } else if (source.charAt(index) == '\'') {
                index = literal(source, result, index, "'", false);
            } else {
                result.append(source.charAt(index++));
            }
        }
        return result.toString();
    }

    private static int blankComment(String source, StringBuilder result, int from, int end, String terminator) {
        int stop = end < 0 ? source.length() : (terminator.equals("\n") ? end : end + terminator.length());
        blank(source, result, from, stop);
        return stop;
    }

    private static int literal(String source, StringBuilder result, int from, String delimiter, boolean keep) {
        int index = from + delimiter.length();
        while (index < source.length() && !source.startsWith(delimiter, index)) {
            index += source.charAt(index) == '\\' ? 2 : 1;
        }
        int stop = Math.min(source.length(), index + delimiter.length());
        if (keep) {
            result.append(source, from, stop);
        } else {
            blank(source, result, from, stop);
        }
        return stop;
    }

    private static void blank(String source, StringBuilder result, int from, int stop) {
        for (int position = from; position < stop; position++) {
            result.append(source.charAt(position) == '\n' ? '\n' : ' ');
        }
    }
}
