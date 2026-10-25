package com.morpheus.architecture;

import com.morpheus.cli.CliCommandPaths;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every command path the CLI declares is claimed by exactly one class of the dispatch, and the dispatch is read from
 * the launcher itself.
 *
 * <p>{@code MorpheusMain.run} holds an ordered table of capability classes and serves the arguments with the first
 * one whose {@code handles} claims them; what none claims goes to {@code MorpheusCli}. The chain is the set of classes
 * whose {@code handles} the compiled {@code run} references or calls, so a class added to the table or taken out of it
 * is seen without being named here. Each declared path is offered to every class of the chain, whatever order the
 * table gives them: a path two classes claim fails, and so does one that none claims unless it is one of the paths
 * {@code MorpheusCli} still serves.</p>
 *
 * <p>{@link #SERVED_BY_THE_DEFAULT_BRANCH} is the default branch that made {@code MorpheusCli} the place where a
 * command lands without anybody deciding it. It only shrinks: a command extracted into a capability class leaves it.
 * {@code api} and {@code mcp} are declared paths that {@code MorpheusMain.main} starts before {@code run} is reached,
 * so the dispatch of {@code run} does not concern them.</p>
 */
class CliCommandDispatchIsExhaustiveTest {

    private static final String LAUNCHER = "com.morpheus.cli.MorpheusMain";

    /** The declared paths no capability class claims, which {@code MorpheusCli} serves. */
    static final Set<String> SERVED_BY_THE_DEFAULT_BRANCH = Set.of(
            "help",
            "projects list",
            "projects add",
            "sync",
            "analyze-change");

    private static final Set<String> STARTED_BEFORE_THE_DISPATCH = Set.of("api", "mcp");

    private static final JavaClasses CLI = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus.cli");

    @Test
    void everyDeclaredPathIsClaimedByExactlyOneClassOfTheDispatch() throws ReflectiveOperationException {
        List<Class<?>> chain = chain();
        assertTrue(chain.size() >= 10, "the dispatch of MorpheusMain.run asks too few classes to be read: " + chain);

        Map<String, List<String>> claimants = new TreeMap<>();
        for (String path : CliCommandPaths.paths()) {
            if (STARTED_BEFORE_THE_DISPATCH.contains(path)) {
                continue;
            }
            List<String> claiming = new ArrayList<>();
            for (Class<?> candidate : chain) {
                if (handles(candidate, path.split(" "))) {
                    claiming.add(candidate.getSimpleName());
                }
            }
            claimants.put(path, claiming);
        }

        Map<String, List<String>> claimedTwice = new TreeMap<>();
        Set<String> unclaimed = new TreeSet<>();
        claimants.forEach((path, claiming) -> {
            if (claiming.size() > 1) {
                claimedTwice.put(path, claiming);
            } else if (claiming.isEmpty()) {
                unclaimed.add(path);
            }
        });
        assertEquals(Map.of(), claimedTwice, "command paths that two classes of the dispatch claim");
        assertEquals(new TreeSet<>(SERVED_BY_THE_DEFAULT_BRANCH), unclaimed,
                "the declared paths that no capability class claims must be exactly those MorpheusCli serves: a path "
                        + "missing here has no claimant, a path listed and claimed has left the default branch and "
                        + "leaves this list");

        Set<String> vacant = new TreeSet<>();
        for (Class<?> candidate : chain) {
            if (claimants.values().stream().noneMatch(claiming -> claiming.contains(candidate.getSimpleName()))) {
                vacant.add(candidate.getSimpleName());
            }
        }
        assertEquals(Set.of(), vacant, "classes of the dispatch that claim no declared path");
    }

    /**
     * The classes whose {@code handles(String[])} the compiled {@code MorpheusMain.run} references, as an entry of its
     * table, or calls.
     */
    private static List<Class<?>> chain() throws ClassNotFoundException {
        JavaClass launcher = CLI.get(LAUNCHER);
        Set<String> owners = new TreeSet<>();
        for (JavaMethod method : launcher.getMethods()) {
            if (!method.getName().equals("run")) {
                continue;
            }
            method.getMethodReferencesFromSelf().stream()
                    .filter(reference -> reference.getName().equals("handles"))
                    .forEach(reference -> owners.add(reference.getTargetOwner().getName()));
            method.getMethodCallsFromSelf().stream()
                    .filter(call -> call.getName().equals("handles"))
                    .forEach(call -> owners.add(call.getTargetOwner().getName()));
        }
        List<Class<?>> chain = new ArrayList<>();
        for (String owner : owners) {
            chain.add(Class.forName(owner));
        }
        return List.copyOf(chain);
    }

    private static boolean handles(Class<?> candidate, String[] args) throws ReflectiveOperationException {
        Method handles = candidate.getDeclaredMethod("handles", String[].class);
        handles.setAccessible(true);
        try {
            return (boolean) handles.invoke(null, (Object) args);
        } catch (InvocationTargetException refused) {
            throw new AssertionError(candidate.getSimpleName() + ".handles failed on " + List.of(args),
                    refused.getCause());
        }
    }
}
