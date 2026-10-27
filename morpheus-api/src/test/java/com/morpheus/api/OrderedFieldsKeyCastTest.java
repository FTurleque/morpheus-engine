package com.morpheus.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderedFieldsKeyCastTest {

    @Test
    void aKeyOfAnotherTypeAnswersAClassCastFailureRatherThanANamedRefusal() {
        ClassCastException failure = assertThrows(ClassCastException.class, () -> OrderedFields.map(7, "seven"));

        assertTrue(failure.getMessage().contains("java.lang.Integer"), failure.getMessage());
    }
}
