package com.morpheus.application.query.compact;

import java.util.Objects;

/**
 * String accumulator that enforces a UTF-8 byte ceiling before mutating its backing buffer.
 *
 * <p>Valid surrogate pairs are counted as one four-byte UTF-8 code point. Isolated surrogates are conservatively
 * counted as three bytes so malformed input can never make the byte budget underestimate the eventual payload.</p>
 */
public final class Utf8BoundedTextBuilder {
    private final int maximumBytes;
    private final StringBuilder delegate = new StringBuilder();
    private int utf8Bytes;

    public Utf8BoundedTextBuilder(int maximumBytes) {
        if (maximumBytes < 0) {
            throw new IllegalArgumentException("maximumBytes must be non-negative");
        }
        this.maximumBytes = maximumBytes;
    }

    public Utf8BoundedTextBuilder append(char value) {
        requireCapacity(utf8Length(value));
        delegate.append(value);
        return this;
    }

    public Utf8BoundedTextBuilder append(CharSequence value) {
        Objects.requireNonNull(value, "value");
        return append(value, 0, value.length());
    }

    public Utf8BoundedTextBuilder append(CharSequence value, int start, int end) {
        Objects.requireNonNull(value, "value");
        if (start < 0 || end < start || end > value.length()) {
            throw new IndexOutOfBoundsException("invalid append range: " + start + ".." + end);
        }
        int bytes = utf8Length(value, start, end);
        requireCapacity(bytes);
        delegate.append(value, start, end);
        return this;
    }

    public Utf8BoundedTextBuilder append(Object value) {
        return append(String.valueOf(value));
    }

    public int utf8Bytes() {
        return utf8Bytes;
    }

    public int maximumBytes() {
        return maximumBytes;
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    private void requireCapacity(int additionalBytes) {
        if (additionalBytes > maximumBytes - utf8Bytes) {
            throw new LimitExceededException(maximumBytes);
        }
        utf8Bytes += additionalBytes;
    }

    private static int utf8Length(char value) {
        if (value <= 0x7F) return 1;
        if (value <= 0x7FF) return 2;
        return 3;
    }

    private static int utf8Length(CharSequence value, int start, int end) {
        int bytes = 0;
        for (int index = start; index < end; index++) {
            char character = value.charAt(index);
            int encoded;
            if (character <= 0x7F) {
                encoded = 1;
            } else if (character <= 0x7FF) {
                encoded = 2;
            } else if (Character.isHighSurrogate(character)
                    && index + 1 < end
                    && Character.isLowSurrogate(value.charAt(index + 1))) {
                encoded = 4;
                index++;
            } else {
                encoded = 3;
            }
            if (bytes > Integer.MAX_VALUE - encoded) {
                return Integer.MAX_VALUE;
            }
            bytes += encoded;
        }
        return bytes;
    }

    /** Output-budget failures are transport/resource failures, not knowledge-state conflicts. */
    public static final class LimitExceededException extends RuntimeException {
        private LimitExceededException(int maximumBytes) {
            super("UTF-8 output exceeds " + maximumBytes + " bytes");
        }
    }
}
