package com.morpheus.application.query.compact;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Utf8BoundedTextBuilderTest {

    @Test
    void countsUtf8BytesAndRejectsBeforeMutatingTheBuffer() {
        Utf8BoundedTextBuilder builder = new Utf8BoundedTextBuilder(4);
        builder.append("€");

        assertEquals(3, builder.utf8Bytes());
        assertThrows(Utf8BoundedTextBuilder.LimitExceededException.class, () -> builder.append("ab"));
        assertEquals("€", builder.toString());
        assertEquals(3, builder.utf8Bytes());

        builder.append('a');
        assertEquals("€a", builder.toString());
        assertEquals(4, builder.utf8Bytes());
    }

    @Test
    void countsValidSupplementaryCodePointAsFourUtf8Bytes() {
        Utf8BoundedTextBuilder builder = new Utf8BoundedTextBuilder(4);

        builder.append("😀");

        assertEquals(4, builder.utf8Bytes());
        assertEquals("😀", builder.toString());
    }
}
