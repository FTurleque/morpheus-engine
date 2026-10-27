package com.morpheus.architecture;

import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.task.ImplementationTask;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A domain type shown on more than one transport is projected to its public shape once, by
 * {@code com.morpheus.application.payload.PublicPayloads}, and no transport adapter declares a projection of its own.
 *
 * <p>Intent: no method of a transport adapter takes a shared domain type and returns its payload. The rule reads the
 * parameter types of the members of {@code com.morpheus.api..}, {@code com.morpheus.mcp..} and
 * {@code com.morpheus.cli..}, so a renamed copy is seen and a class can move between packages of an adapter without
 * touching it. It does not see a shape built inline in a lambda: the compiler gives that body a synthetic method,
 * which the rule leaves out, and {@link PublicPayloadConvergenceTest} is what compares the answers.</p>
 *
 * <p>{@link #SHARED_TYPES} is the inventory: the types more than one transport shows, each with those transports.
 * It only grows. {@link #ADAPTER_SHAPES_DIFFERING_FROM_THE_SHARED_ONE} names the adapter methods that project a shared
 * type to a shape of their own, with the difference; converging them changes a public answer, so each is a decision
 * of the maintainer, and the list only shrinks.</p>
 */
class SharedDomainProjectionArchitectureTest {

    enum Transport { HTTP, MCP, CLI }

    /** The domain types more than one transport shows, with the transports that show them. Only grows. */
    static final Map<Class<?>, Set<Transport>> SHARED_TYPES = Map.of(
            Specification.class, Set.of(Transport.HTTP, Transport.MCP),
            Requirement.class, Set.of(Transport.HTTP, Transport.MCP),
            Scenario.class, Set.of(Transport.HTTP, Transport.MCP),
            ChangeProposal.class, Set.of(Transport.HTTP, Transport.MCP, Transport.CLI),
            Constraint.class, Set.of(Transport.HTTP, Transport.MCP, Transport.CLI),
            AcceptanceCriterion.class, Set.of(Transport.HTTP, Transport.MCP, Transport.CLI),
            DesignDecision.class, Set.of(Transport.HTTP, Transport.MCP, Transport.CLI),
            ImplementationTask.class, Set.of(Transport.HTTP, Transport.MCP, Transport.CLI));

    /** Adapter methods that project a shared type to a shape of their own, by full name, each with how it differs. */
    static final Map<String, String> ADAPTER_SHAPES_DIFFERING_FROM_THE_SHARED_ONE = Map.of(
            "com.morpheus.cli.command.MorpheusChangeCli.changeView(com.morpheus.domain.change.ChangeProposal)",
            "the CLI change has no projectId and writes an absent key as null, where HTTP and MCP write \"\"",
            "com.morpheus.cli.command.MorpheusAcceptanceCriteriaCli.view(com.morpheus.domain.acceptance.AcceptanceCriterion)",
            "the CLI acceptance criterion writes an absent requirementId or changeId as null, where HTTP and MCP "
                    + "write \"\"");

    static final String PROJECTIONS = "com.morpheus.application.payload";

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus");

    private static final DescribedPredicate<JavaMethod> PROJECTS_A_SHARED_DOMAIN_TYPE =
            DescribedPredicate.describe("take a shared domain type as their only parameter",
                    method -> !method.getModifiers().contains(JavaModifier.SYNTHETIC)
                            && method.getRawParameterTypes().size() == 1
                            && SHARED_TYPES.keySet().stream()
                            .anyMatch(type -> method.getRawParameterTypes().get(0).isEquivalentTo(type))
                            && !ADAPTER_SHAPES_DIFFERING_FROM_THE_SHARED_ONE.containsKey(method.getFullName()));

    @Test
    void noMethodOfATransportAdapterProjectsASharedDomainTypeOfItsOwn() {
        methods().that(PROJECTS_A_SHARED_DOMAIN_TYPE)
                .should().beDeclaredInClassesThat()
                .resideOutsideOfPackages("com.morpheus.api..", "com.morpheus.mcp..", "com.morpheus.cli..")
                .because("the public shape of a domain type shown on more than one transport is written once, in "
                        + PROJECTIONS + ".PublicPayloads, so a key added to it reaches every transport")
                .check(PRODUCTION);
    }

    @Test
    void theProjectionsProjectEverySharedType() {
        Set<String> projected = new TreeSet<>();
        for (JavaMethod method : PRODUCTION.get(PROJECTIONS + ".PublicPayloads").getMethods()) {
            if (method.getRawParameterTypes().size() == 1) {
                projected.add(method.getRawParameterTypes().get(0).getName());
            }
        }
        Set<String> shared = new TreeSet<>();
        SHARED_TYPES.keySet().forEach(type -> shared.add(type.getName()));
        assertTrue(projected.containsAll(shared),
                () -> "every shared domain type has its projection in PublicPayloads; missing: " + difference(shared, projected));
    }

    @Test
    void everyListedAdapterShapeStillExistsAndSaysHowItDiffers() {
        Set<String> declared = new TreeSet<>();
        PRODUCTION.forEach(javaClass -> javaClass.getMethods().forEach(method -> declared.add(method.getFullName())));
        Set<String> stale = new TreeSet<>();
        ADAPTER_SHAPES_DIFFERING_FROM_THE_SHARED_ONE.forEach((method, difference) -> {
            assertFalse(difference.isBlank(), "a listed adapter shape says how it differs: " + method);
            if (!declared.contains(method)) {
                stale.add(method);
            }
        });
        assertEquals(Set.of(), stale,
                "these listed adapter shapes no longer exist: remove them from ADAPTER_SHAPES_DIFFERING_FROM_THE_SHARED_ONE");
    }

    @Test
    void theProjectionsDependOnTheDomainAndThePayloadBuilderOnly() {
        noClasses().that().resideInAPackage(PROJECTIONS + "..")
                .should().dependOnClassesThat(resideInAPackage("com.morpheus..")
                        .and(DescribedPredicate.not(resideInAnyPackage(
                                "com.morpheus.domain..", PayloadMapConstructionArchitectureTest.FOUNDATION + "..",
                                PROJECTIONS + ".."))))
                .because("a projection is a pure function of a domain value, written with PayloadMap")
                .check(PRODUCTION);
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new TreeSet<>(left);
        result.removeAll(right);
        return result;
    }
}
