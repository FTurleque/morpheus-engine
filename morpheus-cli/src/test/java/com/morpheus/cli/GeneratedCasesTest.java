package com.morpheus.cli;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedCasesTest {

    @Test
    void aFactoryThatBuiltNoTestFailsAndNamesItself() {
        AssertionError failure = assertThrows(AssertionError.class,
                () -> GeneratedCases.nonEmpty("theFactoryUnderTest", Stream.empty()));

        assertTrue(failure.getMessage().contains("theFactoryUnderTest"), failure.getMessage());
        assertTrue(failure.getMessage().contains("generated no test"), failure.getMessage());
    }

    @Test
    void aFactoryThatBuiltTestsHandsThemOverInOrder() {
        DynamicTest first = DynamicTest.dynamicTest("first", () -> { });
        DynamicTest second = DynamicTest.dynamicTest("second", () -> { });

        List<DynamicTest> handedOver = GeneratedCases.nonEmpty("factory", Stream.of(first, second)).toList();

        assertEquals(List.of(first, second), handedOver);
    }
}
