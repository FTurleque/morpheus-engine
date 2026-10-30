package com.morpheus.architecture;

import com.morpheus.domain.change.lifecycle.ChangeLifecycleBlocker;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleTransitionDecision;
import com.morpheus.domain.reference.ExternalReference;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two decisions on a single domain value are written on their type, in the domain, and no application class makes
 * them again (ADR-0156, on the pattern of ADR-0082).
 *
 * <p>A lifecycle transition is decided by {@code ChangeLifecycleTransitionRequest.decide()} and its outcome is the closed
 * family {@link ChangeLifecycleTransitionDecision}; the resolution state of an external reference is moved by
 * {@link ExternalReference} itself. An application class that reads a constant of {@link ChangeLifecycleBlocker}
 * restates the lifecycle decision: an enum {@code switch} reads the constants too, in the synthetic class {@code javac}
 * writes in the package of the switch, so the rule sees it, and printing {@code blocker.name()} reads none. A class
 * outside the domain that calls {@code ExternalReference.transition} chooses the next resolution state itself. The
 * rules have no exception.</p>
 */
class LifecycleTransitionDecisionInTheDomainArchitectureTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus");

    @Test
    void noApplicationClassReadsAConstantOfALifecycleBlocker() {
        noClasses()
                .that().resideInAPackage("com.morpheus.application..")
                .should().accessFieldWhere(readsAConstantOf(ChangeLifecycleBlocker.class))
                .because("ChangeLifecycleTransitionRequest decides a lifecycle transition and "
                        + "ChangeLifecycleTransitionDecision says whether it waits for an abandonment reason or is "
                        + "blocked by a constraint; an application service asks them (ADR-0156)")
                .check(PRODUCTION);
    }

    @Test
    void onlyTheDomainBuildsALifecycleTransitionDecision() {
        noClasses()
                .that().resideOutsideOfPackage("com.morpheus.domain..")
                .should().callConstructorWhere(DescribedPredicate.describe(
                        "a constructor of a ChangeLifecycleTransitionDecision",
                        call -> call.getTargetOwner().isAssignableTo(ChangeLifecycleTransitionDecision.class)))
                .because("a lifecycle transition is allowed or blocked by the domain's rules only (ADR-0156)")
                .check(PRODUCTION);
    }

    @Test
    void aLifecycleTransitionDecisionIsAllowedOrBlockedAndNothingElse() {
        assertTrue(ChangeLifecycleTransitionDecision.class.isSealed(),
                "the outcome of a lifecycle transition is a closed family (ADR-0156)");
        Set<String> permitted = Arrays.stream(ChangeLifecycleTransitionDecision.class.getPermittedSubclasses())
                .map(Class::getSimpleName)
                .collect(Collectors.toCollection(java.util.TreeSet::new));
        assertEquals(Set.of("Allowed", "Blocked"), permitted,
                "a lifecycle transition is allowed towards a target or blocked by named blockers (ADR-0156)");
    }

    @Test
    void onlyTheDomainMovesTheResolutionStateOfAnExternalReference() {
        noClasses()
                .that().resideOutsideOfPackage("com.morpheus.domain..")
                .should().callMethodWhere(DescribedPredicate.describe(
                        "a call to ExternalReference.transition",
                        call -> call.getTargetOwner().isEquivalentTo(ExternalReference.class)
                                && call.getName().equals("transition")))
                .because("ExternalReference decides whether a lost target leaves it UNRESOLVED or STALE, and with "
                        + "which reason; a resolver reports what it found and the reference moves itself through "
                        + "resolvedAs, withoutResolver or notFound (ADR-0156)")
                .check(PRODUCTION);
    }

    private static DescribedPredicate<JavaFieldAccess> readsAConstantOf(Class<?> enumType) {
        return JavaFieldAccess.Predicates.target(AccessTarget.Predicates.declaredIn(enumType))
                .as("a field access to a constant of " + enumType.getSimpleName());
    }
}
