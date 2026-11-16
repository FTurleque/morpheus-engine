package com.morpheus.architecture;

import com.morpheus.application.read.ProviderIngestionLimitException;
import com.morpheus.architecture.budgetcatch.fixture.BudgetCatchFixtures;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.TryCatchBlock;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A budget refusal raised inside a provider is caught by a clause of its own, never by a broader one (ADR-0028).
 *
 * <p>{@link ProviderIngestionLimitException} extends {@link IllegalArgumentException}, which is how every surface
 * answers a refused read with the status of an invalid input. The same inheritance makes a reader's
 * {@code catch (IOException | IllegalArgumentException ...)} take a budget refusal for a malformed file: the read then
 * fails under a generic message, without the {@code ingestion-budget} group and with the file the read stopped at
 * named as the culprit, when ADR-0028 says a budget refusal passes unchanged and unattributed. The rule: in the main
 * classes of a provider ({@code com.morpheus.provider..}, {@code com.morpheus.sdk.provider..}), a {@code try} that can
 * raise a budget refusal and catches {@link IllegalArgumentException} or one of its supertypes also catches
 * {@link ProviderIngestionLimitException}. {@code javac} refuses a subclass clause after its superclass, so a clause
 * of its own is always placed before the broader one, and the exception table sends the refusal to it first. What
 * that clause does is the reader's: relay it unchanged, or turn it into the {@code ingestion-budget} result that a
 * content reader returns; each reader's tests hold that behaviour.</p>
 *
 * <p><b>Why the bytecode.</b> The intention is about the exception table and the call graph: which handler receives
 * a refusal thrown by a method called, possibly several calls down, from the {@code try}. A reading of the text sees
 * the catch clauses but not that {@code normalize(...)} reads a document three calls later. A {@code try} "can raise
 * a budget refusal" when an access it contains reaches a construction of {@link ProviderIngestionLimitException}
 * through calls between imported {@code com.morpheus} classes, a call of an interface or superclass method counting
 * as a call of every override that reaches it. No constant is involved, so nothing escapes the bytecode by inlining.</p>
 *
 * <p><b>Lambdas.</b> A lambda body is compiled into a method of its own, and ArchUnit attributes its accesses to the
 * declaring method without placing them in the {@code try} that contains the lambda. A {@code try} therefore also
 * counts as reaching the budget when it calls a method that takes a {@code @FunctionalInterface} while its method
 * declares a lambda that reaches the budget. This over-approximates: a lambda declared outside the {@code try} of
 * that method is counted too, and the remedy, a clause of its own, is harmless there.</p>
 *
 * <p><b>Try-with-resources.</b> {@code javac} compiles that statement with a handler of its own that catches
 * {@link Throwable} around the body, closes the resource and rethrows. No one wrote that clause and it relays the
 * refusal, so a block that catches {@link Throwable} alone in a method that calls {@code Throwable.addSuppressed} on
 * or before its first line is taken for that handler and not judged.</p>
 *
 * <p><b>Not covered, stated rather than implied.</b> A refusal reached through reflection, through a class this
 * module does not import, or through a functional parameter the method did not receive as a lambda it declares (a
 * field holding a {@code Supplier}) is not seen. A hand-written {@code catch (Throwable ...)} placed after a
 * try-with-resources in the same method is taken for that statement's handler and not judged. A {@code try} declared
 * inside a lambda is judged like any other.</p>
 *
 * <p><b>Planted violation, besides the fixtures.</b> Removing the clause
 * {@code catch (ProviderIngestionLimitException limit) { return budgetRefusal(request, limit); }} from
 * {@code StructuredMarkdownSpecificationContentReader#read} makes {@link #aProviderCatchesABudgetRefusalInAClauseOfItsOwn()}
 * fail, naming that method and {@code java.lang.IllegalArgumentException}.</p>
 */
class ProviderIngestionLimitCatchArchitectureTest {

    private static final String[] PROVIDER_PACKAGES = {"com.morpheus.provider..", "com.morpheus.sdk.provider.."};
    private static final String FIXTURE_PACKAGE = BudgetCatchFixtures.class.getPackageName();

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus");

    @Test
    void aProviderCatchesABudgetRefusalInAClauseOfItsOwn() {
        classes().that().resideInAnyPackage(PROVIDER_PACKAGES)
                .should(catchABudgetRefusalInAClauseOfItsOwn(PRODUCTION))
                .because("a budget refusal is an IllegalArgumentException, and a broader clause would report it as a "
                        + "malformed file, attributed and without the ingestion-budget group (ADR-0028)")
                .check(PRODUCTION);
    }

    /** The rule has something to judge in every built-in provider, so a reach analysis gone blind cannot pass. */
    @Test
    void everyBuiltInProviderHasATryThatReachesTheBudgetAndCatchesABroaderType() {
        Set<JavaCodeUnit> reach = unitsThatCanRaiseABudgetRefusal(PRODUCTION);
        JavaClass limit = PRODUCTION.get(ProviderIngestionLimitException.class);
        for (String provider : List.of("openspec", "markdown", "synthetic")) {
            String providerPackage = "com.morpheus.provider." + provider;
            long judged = PRODUCTION.stream()
                    .filter(javaClass -> javaClass.getPackageName().equals(providerPackage))
                    .flatMap(javaClass -> javaClass.getCodeUnits().stream())
                    .flatMap(codeUnit -> codeUnit.getTryCatchBlocks().stream())
                    .filter(block -> !broaderClauses(block, limit).isEmpty() && reachesTheBudget(block, reach))
                    .count();
            assertTrue(judged > 0, "no try of " + providerPackage + " reaches the budget under a broader clause: "
                    + "the reach analysis no longer sees the budget calls of that provider");
        }
    }

    @Test
    void theRuleRefusesEveryFixtureThatLeavesTheRefusalToABroaderClauseAndNoOther() {
        JavaClasses fixtures = new ClassFileImporter()
                .importPackages(FIXTURE_PACKAGE, ProviderIngestionLimitException.class.getPackageName());
        ArchCondition<JavaClass> condition = catchABudgetRefusalInAClauseOfItsOwn(fixtures);
        SortedSet<String> refused = new TreeSet<>();
        fixtures.stream()
                .filter(javaClass -> javaClass.getPackageName().equals(FIXTURE_PACKAGE))
                .forEach(javaClass -> {
                    ConditionEvents events = ConditionEvents.Factory.create();
                    condition.check(javaClass, events);
                    if (events.containViolation()) {
                        refused.add(javaClass.getSimpleName());
                    }
                });

        assertEquals(new TreeSet<>(List.of(
                        "SwallowsTheRefusalInACatchOfThrowable",
                        "SwallowsTheRefusalOfADirectBudgetCall",
                        "SwallowsTheRefusalOfAReadInAMultiCatch",
                        "SwallowsTheRefusalThroughAHelper",
                        "SwallowsTheRefusalThroughALambda")),
                refused,
                "a broader clause around a budget call, direct, multi-caught, through a helper or a lambda, or a "
                        + "written catch of Throwable, is refused; a clause of its own, a catch around no budget call, "
                        + "an unrelated type and the handler of a try-with-resources are not");
    }

    private static ArchCondition<JavaClass> catchABudgetRefusalInAClauseOfItsOwn(JavaClasses imported) {
        Set<JavaCodeUnit> reach = unitsThatCanRaiseABudgetRefusal(imported);
        JavaClass limit = imported.get(ProviderIngestionLimitException.class);
        return new ArchCondition<>("catch a budget refusal in a clause of its own before a broader one") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                javaClass.getCodeUnits().stream()
                        .flatMap(codeUnit -> codeUnit.getTryCatchBlocks().stream())
                        .forEach(block -> violation(block, limit, reach).ifPresent(message ->
                                events.add(SimpleConditionEvent.violated(block, message))));
            }
        };
    }

    private static Optional<String> violation(TryCatchBlock block, JavaClass limit, Set<JavaCodeUnit> reach) {
        List<String> broader = broaderClauses(block, limit);
        if (broader.isEmpty() || block.getCaughtThrowables().contains(limit)
                || isTheHandlerOfATryWithResources(block) || !reachesTheBudget(block, reach)) {
            return Optional.empty();
        }
        return Optional.of(block.getOwner().getFullName() + " catches " + String.join(" | ", broader)
                + " around a call that can raise a budget refusal, with no clause of its own for "
                + limit.getSimpleName() + " " + block.getSourceCodeLocation());
    }

    private static List<String> broaderClauses(TryCatchBlock block, JavaClass limit) {
        return block.getCaughtThrowables().stream()
                .filter(caught -> !caught.equals(limit) && limit.isAssignableTo(caught.getName()))
                .map(JavaClass::getName)
                .sorted()
                .toList();
    }

    /**
     * The handler {@code javac} writes for a try-with-resources catches {@link Throwable}, closes the resource,
     * attaches a failure of the close by {@code addSuppressed} on the line of the {@code try}, and rethrows: it is not a
     * clause anyone wrote. It is told apart by that call, on a line no later than the first line of the block.
     */
    private static boolean isTheHandlerOfATryWithResources(TryCatchBlock block) {
        if (!block.getCaughtThrowables().stream().map(JavaClass::getName).toList()
                .equals(List.of(Throwable.class.getName()))) {
            return false;
        }
        int firstLine = block.getSourceCodeLocation().getLineNumber();
        return block.getOwner().getMethodCallsFromSelf().stream()
                .anyMatch(call -> call.getTarget().getOwner().isEquivalentTo(Throwable.class)
                        && call.getName().equals("addSuppressed")
                        && call.getLineNumber() <= firstLine);
    }

    private static boolean reachesTheBudget(TryCatchBlock block, Set<JavaCodeUnit> reach) {
        if (block.getAccessesContainedInTryBlock().stream().anyMatch(access -> reaches(access, reach))) {
            return true;
        }
        boolean passesALambda = block.getAccessesContainedInTryBlock().stream()
                .flatMap(access -> target(access).stream())
                .anyMatch(target -> target.getRawParameterTypes().stream()
                        .anyMatch(type -> type.isAnnotatedWith(FunctionalInterface.class)));
        return passesALambda && block.getOwner().getAccessesFromSelf().stream()
                .anyMatch(access -> access.isDeclaredInLambda() && reaches(access, reach));
    }

    private static boolean reaches(JavaAccess<?> access, Set<JavaCodeUnit> reach) {
        return target(access).filter(reach::contains).isPresent();
    }

    private static Optional<JavaCodeUnit> target(JavaAccess<?> access) {
        if (access instanceof JavaCodeUnitAccess<?> codeUnitAccess) {
            return codeUnitAccess.getTarget().resolveMember().map(JavaCodeUnit.class::cast);
        }
        return Optional.empty();
    }

    /**
     * Every imported code unit from which a construction of the refusal is reachable, walking calls backwards; a
     * reaching override makes the method it overrides reach too, since a call of that method may dispatch to it.
     */
    private static Set<JavaCodeUnit> unitsThatCanRaiseABudgetRefusal(JavaClasses imported) {
        Set<JavaCodeUnit> reach = new LinkedHashSet<>();
        Deque<JavaCodeUnit> pending = new ArrayDeque<>();
        for (JavaConstructor constructor : imported.get(ProviderIngestionLimitException.class).getConstructors()) {
            pending.add(constructor);
        }
        while (!pending.isEmpty()) {
            JavaCodeUnit codeUnit = pending.removeFirst();
            if (!reach.add(codeUnit)) {
                continue;
            }
            codeUnit.getAccessesToSelf().forEach(access -> pending.add(access.getOrigin()));
            if (codeUnit instanceof JavaMethod method) {
                pending.addAll(overridden(method));
            }
        }
        return reach;
    }

    private static Collection<JavaMethod> overridden(JavaMethod method) {
        String[] parameters = method.getRawParameterTypes().stream().map(JavaClass::getName).toArray(String[]::new);
        List<JavaMethod> overridden = new ArrayList<>();
        Stream.concat(method.getOwner().getAllRawSuperclasses().stream(), method.getOwner().getAllRawInterfaces().stream())
                .forEach(supertype -> supertype.tryGetMethod(method.getName(), parameters).ifPresent(overridden::add));
        return overridden;
    }
}
