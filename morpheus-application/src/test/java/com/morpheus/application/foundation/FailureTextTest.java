package com.morpheus.application.foundation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FailureTextTest {

    @Test
    void aFailureIsWordedByItsMessageWhateverItsDeclaredType() {
        Throwable throwable = new Error("boom");
        Exception exception = new IOException("boom");
        RuntimeException runtime = new IllegalStateException("boom");

        assertEquals("boom", FailureText.of(throwable));
        assertEquals("boom", FailureText.of(exception));
        assertEquals("boom", FailureText.of(runtime));
    }

    @Test
    void aFailureWithoutAMessageIsWordedByTheSimpleNameOfItsType() {
        assertEquals("IllegalStateException", FailureText.of(new IllegalStateException()));
        assertEquals("UncheckedIOException", FailureText.of(new UncheckedIOException(null, new IOException())));
    }

    @Test
    void aFailureWithAnEmptyOrBlankMessageIsWordedByTheSimpleNameOfItsType() {
        assertEquals("IllegalArgumentException", FailureText.of(new IllegalArgumentException("")));
        assertEquals("IllegalArgumentException", FailureText.of(new IllegalArgumentException(" \t\n")));
    }

    @Test
    void aMessageThatNamesAServerLocationIsKeptAsItIs() {
        assertEquals("cannot read /home/alice/private/spec.md",
                FailureText.of(new IllegalStateException("cannot read /home/alice/private/spec.md")));
    }

    @Test
    void aMessageIsKeptWithItsSurroundingSpaces() {
        assertEquals("  busy ", FailureText.of(new IllegalStateException("  busy ")));
    }
}
