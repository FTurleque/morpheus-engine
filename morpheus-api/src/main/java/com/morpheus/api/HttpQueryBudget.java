package com.morpheus.api;

import java.util.function.Function;

/**
 * Explicit budget for HTTP query strings, shared by every MORPHEUS query parser.
 *
 * <p>MORPHEUS bounds request bodies, proxied responses, MCP frames and identity files before it materializes
 * them. Query strings were the one boundary input that was split, percent-decoded and mapped first and bounded
 * afterwards -- so a caller decided how much work the server did before the server had an opinion. Every check
 * here is expressed on the <em>raw</em> text and runs before the allocation it guards: percent-decoding can only
 * shrink a slice, so a raw slice within budget cannot decode into one that is not.</p>
 *
 * <p>The limits are the smallest that leave every published route unaffected. No route accepts more than four
 * parameters, no parameter name exceeds twenty characters, and the largest legitimate values are filesystem
 * paths and a requirement search term.</p>
 */
final class HttpQueryBudget {
    static final int MAX_QUERY_BYTES = 16 * 1024;
    static final int MAX_PARAMETERS = 16;
    static final int MAX_PARAMETER_NAME_BYTES = 128;
    static final int MAX_PARAMETER_VALUE_BYTES = 8 * 1024;

    private HttpQueryBudget() {
    }

    static void requireBoundedQuery(String rawQuery, Function<String, RuntimeException> failure) {
        if (exceedsUtf8(rawQuery, MAX_QUERY_BYTES)) {
            throw failure.apply("query string exceeds " + MAX_QUERY_BYTES + " bytes");
        }
    }

    static void requireBoundedParameterCount(int parameters, Function<String, RuntimeException> failure) {
        if (parameters > MAX_PARAMETERS) {
            throw failure.apply("query string exceeds " + MAX_PARAMETERS + " parameters");
        }
    }

    static void requireBoundedParameterName(String rawName, Function<String, RuntimeException> failure) {
        if (exceedsUtf8(rawName, MAX_PARAMETER_NAME_BYTES)) {
            throw failure.apply("query parameter name exceeds " + MAX_PARAMETER_NAME_BYTES + " bytes");
        }
    }

    static void requireBoundedParameterValue(String rawValue, Function<String, RuntimeException> failure) {
        if (exceedsUtf8(rawValue, MAX_PARAMETER_VALUE_BYTES)) {
            throw failure.apply("query parameter value exceeds " + MAX_PARAMETER_VALUE_BYTES + " bytes");
        }
    }

    /**
     * Whether {@code value} encodes to more than {@code maxBytes} in UTF-8, without encoding it.
     *
     * <p>{@code getBytes(UTF_8).length} would allocate the very array these bounds exist to refuse, and counting
     * the whole string would do the entire walk an oversized input is asking for. The count stops at the first
     * character that puts the total past the budget, so the work stays bounded by the budget rather than by the
     * input.</p>
     */
    static boolean exceedsUtf8(String value, int maxBytes) {
        if (value.length() > maxBytes) return true;
        int length = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current < 0x80) {
                length += 1;
            } else if (current < 0x800) {
                length += 2;
            } else if (Character.isHighSurrogate(current) && index + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(index + 1))) {
                length += 4;
                index++;
            } else {
                length += 3;
            }
            if (length > maxBytes) return true;
        }
        return false;
    }
}
