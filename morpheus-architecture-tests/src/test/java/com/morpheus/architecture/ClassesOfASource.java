package com.morpheus.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;

import java.util.List;
import java.util.Set;

/**
 * Selects the classes compiled from named production sources: each top-level class and every class nested in it,
 * anonymous and local classes included. A rule that replaces a ban on the text of a source sees what that source
 * declares, not only its top-level class.
 */
public final class ClassesOfASource {

    private ClassesOfASource() {
    }

    public static DescribedPredicate<JavaClass> declaredIn(String... topLevelSimpleNames) {
        Set<String> names = Set.of(topLevelSimpleNames);
        return DescribedPredicate.describe("declared in the source of " + List.of(topLevelSimpleNames),
                javaClass -> names.contains(topLevel(javaClass).getSimpleName()));
    }

    private static JavaClass topLevel(JavaClass javaClass) {
        JavaClass outer = javaClass;
        while (outer.getEnclosingClass().isPresent()) {
            outer = outer.getEnclosingClass().get();
        }
        return outer;
    }
}
