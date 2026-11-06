package com.morpheus.architecture;

import com.morpheus.architecture.EnforcementVerdict.Kind;
import com.morpheus.architecture.EnforcementVerdict.Outcome;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.TestTag;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Feeds {@link EnforcementVerdict} from the JUnit platform, in the single Surefire run of this module.
 *
 * <p>Registered as a service in {@code META-INF/services/org.junit.platform.launcher.TestExecutionListener}, it
 * changes neither the selection, nor the order, nor the reports: it reads each finished node and, at the end of the
 * plan, writes {@value EnforcementVerdict#PROOF_FILE} and prints the two verdict lines.</p>
 */
public final class EnforcementVerdictListener implements TestExecutionListener {

    private final List<Outcome> outcomes = new ArrayList<>();
    private TestPlan plan;

    @Override
    public synchronized void testPlanExecutionStarted(TestPlan testPlan) {
        plan = testPlan;
        outcomes.clear();
    }

    @Override
    public synchronized void executionFinished(TestIdentifier identifier, TestExecutionResult result) {
        outcomes.add(new Outcome(nameOf(identifier), kindOf(identifier),
                EnforcementVerdict.carriesTheTag(identifier, plan::getParent, EnforcementVerdictListener::tagNames,
                        CoherenceCheck.TAG),
                result.getStatus() == TestExecutionResult.Status.FAILED));
    }

    @Override
    public synchronized void testPlanExecutionFinished(TestPlan testPlan) {
        EnforcementVerdict verdict = new EnforcementVerdict(outcomes);
        Path proof = RepositoryRoot.locate().resolve(EnforcementVerdict.PROOF_FILE);
        try {
            Files.createDirectories(proof.getParent());
            Files.write(proof, verdict.proofLines(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("the enforcement verdict could not be written to " + proof, failure);
        }
        verdict.consoleLines().forEach(System.out::println);
    }

    private static Set<String> tagNames(TestIdentifier identifier) {
        return identifier.getTags().stream().map(TestTag::getName).collect(Collectors.toUnmodifiableSet());
    }

    private static Kind kindOf(TestIdentifier identifier) {
        if (identifier.isTest()) {
            return Kind.TEST;
        }
        return identifier.getSource().filter(ClassSource.class::isInstance).isPresent()
                ? Kind.TEST_CLASS
                : Kind.OTHER_CONTAINER;
    }

    private String nameOf(TestIdentifier identifier) {
        TestSource source = identifier.getSource().orElse(null);
        if (source instanceof ClassSource type) {
            return type.getClassName();
        }
        boolean generated = plan.getParent(identifier)
                .flatMap(TestIdentifier::getSource)
                .filter(MethodSource.class::isInstance)
                .isPresent();
        if (!generated && source instanceof MethodSource method) {
            return method.getClassName() + "#" + method.getMethodName() + "(" + method.getMethodParameterTypes() + ")";
        }
        return plan.getParent(identifier)
                .map(parent -> nameOf(parent) + " > " + identifier.getDisplayName())
                .orElse(identifier.getDisplayName());
    }
}
