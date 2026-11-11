package com.morpheus.architecture;

import com.morpheus.domain.constraint.ConstraintApplicability;
import com.morpheus.domain.constraint.ConstraintBlockingMode;
import com.morpheus.domain.constraint.ConstraintSatisfaction;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Whether a constraint blocks a lifecycle state is decided by {@code Constraint}, in the domain; no application class
 * decides it again from the constraint's fields (ADR-0082).
 *
 * <p>The rule refuses an application class that reads a constant of {@link ConstraintApplicability},
 * {@link ConstraintBlockingMode} or {@link ConstraintSatisfaction}: comparing a constraint's field to one of them is how
 * the decision is restated. An enum {@code switch} reads the constants too, in the synthetic class {@code javac}
 * writes for it, and that class belongs to the application package of the switch, so the rule sees it. A projection
 * that prints {@code mode().name()} reads no constant and is not concerned. The rule has no exception.</p>
 */
class ConstraintDecisionInTheDomainArchitectureTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus.application", "com.morpheus.domain");

    @Test
    void noApplicationClassReadsAConstantOfAConstraintsBlockingInputs() {
        noClasses()
                .that().resideInAPackage("com.morpheus.application..")
                .should().accessFieldWhere(readsAConstantOf(
                        ConstraintApplicability.class, ConstraintBlockingMode.class, ConstraintSatisfaction.class))
                .because("the domain's Constraint decides whether a constraint blocks a lifecycle state, whether its "
                        + "blocking is known and whether it counts as a violated explicit blocking policy; an "
                        + "application service asks it and does not restate the decision (ADR-0082, ADR-0119)")
                .check(PRODUCTION);
    }

    private static DescribedPredicate<JavaFieldAccess> readsAConstantOf(Class<?>... enums) {
        DescribedPredicate<AccessTarget> declaredInOne = AccessTarget.Predicates.declaredIn(enums[0]);
        for (int index = 1; index < enums.length; index++) {
            declaredInOne = declaredInOne.or(AccessTarget.Predicates.declaredIn(enums[index]));
        }
        return JavaFieldAccess.Predicates.target(declaredInOne)
                .as("a field access to a constant of a constraint's applicability, blocking mode or satisfaction");
    }
}
