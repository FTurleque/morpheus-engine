package com.morpheus.architecture;

import org.junit.jupiter.api.Tag;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test class of the architecture suite that evaluates no rule on bytecode, reads no production source and
 * loads no production class: it checks documents, scripts, workflows, contracts or the test sources themselves.
 *
 * <p>The class stays in this module, so the executions that {@code architectureTestsMinimum} counts do not move. The
 * marker is the JUnit tag {@value #TAG}: {@code ./mvnw test -pl morpheus-architecture-tests -Dgroups=coherence} runs
 * these classes alone. {@code CoherenceCheckMarkerTest} derives the property from each class's source and refuses a
 * marker that disagrees with it, in either direction.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag(CoherenceCheck.TAG)
public @interface CoherenceCheck {

    /** The JUnit tag the marker carries. */
    String TAG = "coherence";
}
