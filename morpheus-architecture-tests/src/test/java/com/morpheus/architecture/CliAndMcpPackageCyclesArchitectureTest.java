package com.morpheus.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The packages of the CLI adapter form no cycle, and neither do those of the MCP adapter.
 *
 * <p>In {@code morpheus-cli} the launcher and the dispatcher ({@code com.morpheus.cli}) sit above the wiring of the
 * servers it starts ({@code .launch}), which sits above the capability classes and the plumbing they share
 * ({@code .command}). In {@code morpheus-mcp} the server ({@code com.morpheus.mcp.server}) sits above the tool classes,
 * their runtime and their plumbing ({@code com.morpheus.mcp}). A dependency back up either order closes a cycle and
 * fails here, naming both packages; there is no accepted cycle. A cycle rule over fewer packages holds over less, so
 * each rule first asserts that every package of its layering exists.</p>
 */
class CliAndMcpPackageCyclesArchitectureTest {

    private static final String CLI = "com.morpheus.cli";
    private static final String MCP = "com.morpheus.mcp";

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(CLI, MCP);

    @Test
    void thePackagesOfTheCliAdapterFormNoCycle() {
        assertNoCycle(CLI, Set.of(CLI, CLI + ".launch", CLI + ".command"),
                "the launcher and the dispatcher over the server wiring, the wiring over the capability classes");
    }

    @Test
    void thePackagesOfTheMcpAdapterFormNoCycle() {
        assertNoCycle(MCP, Set.of(MCP + ".server", MCP),
                "the server over the tool classes, their runtime and their plumbing");
    }

    private static void assertNoCycle(String adapter, Set<String> layered, String layering) {
        Set<String> packages = new TreeSet<>();
        for (JavaClass javaClass : PRODUCTION) {
            if (inAdapter(javaClass.getPackageName(), adapter)) {
                packages.add(javaClass.getPackageName());
            }
        }
        assertTrue(packages.containsAll(layered), () -> "the cycle rule is written over the packages of " + adapter
                + " " + new TreeSet<>(layered) + "; it holds over less than that when one is missing: " + packages);

        slices().assignedFrom(packagesOf(adapter))
                .should().beFreeOfCycles()
                .because("the packages of " + adapter + " are layered: " + layering)
                .check(PRODUCTION);
    }

    private static boolean inAdapter(String packageName, String adapter) {
        return packageName.equals(adapter) || packageName.startsWith(adapter + ".");
    }

    private static SliceAssignment packagesOf(String adapter) {
        return new SliceAssignment() {
            @Override
            public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
                return inAdapter(javaClass.getPackageName(), adapter)
                        ? SliceIdentifier.of(javaClass.getPackageName())
                        : SliceIdentifier.ignore();
            }

            @Override
            public String getDescription() {
                return "the packages of " + adapter;
            }
        };
    }
}
