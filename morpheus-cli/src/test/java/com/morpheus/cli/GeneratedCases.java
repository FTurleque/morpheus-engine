package com.morpheus.cli;

import org.junit.jupiter.api.DynamicTest;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * What a test factory returns, once it is known to have built something.
 *
 * <p>A factory whose source of cases comes back empty generates no test and reports no failure. Every factory of this
 * module passes its stream through {@link #nonEmpty}, so that case fails and names the factory.
 * {@code TestFactoryAssertsItsSourceTest} refuses a factory that does not.</p>
 */
final class GeneratedCases {

    private GeneratedCases() {
    }

    static Stream<DynamicTest> nonEmpty(String factory, Stream<DynamicTest> tests) {
        List<DynamicTest> cases = tests.toList();
        assertFalse(cases.isEmpty(), () -> "the test factory " + factory + " generated no test: its source of cases was empty");
        return cases.stream();
    }
}
