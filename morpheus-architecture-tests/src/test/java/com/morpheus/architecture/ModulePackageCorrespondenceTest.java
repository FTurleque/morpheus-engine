package com.morpheus.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Rules select classes by package; the reactor, the POMs and the documentation speak of modules. This test holds the
 * two together: a package prefix belongs to the modules that declare it.
 *
 * <p>{@link #ROOTS} gives each reactor module that carries sources under {@code src/main/java} its root package. The
 * module list is read from the root POM, and the packages from the source directories: declaring a module makes its
 * entry mandatory, and an entry outlives no module. An entry whose root is not the module name read mechanically
 * ({@code morpheus-store-sqlite} is {@code com.morpheus.store.sqlite}) states why, and a root that lies inside another
 * module's root is named in {@link #NESTED_INSIDE}. The comparison is textual by nature: no rule on bytecode sees a
 * module.</p>
 */
class ModulePackageCorrespondenceTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus");

    private static final Pattern MODULE = Pattern.compile("<module>([^<]+)</module>");

    private static final String MCP_TRANSPORT = "morpheus-mcp-transport";

    /** The root package of a module, and why it is not the module name read mechanically ({@code ""} when it is). */
    private record ModuleRoot(String rootPackage, String reason) {
    }

    private static final Map<String, ModuleRoot> ROOTS = new TreeMap<>(Map.ofEntries(
            Map.entry("morpheus-domain", new ModuleRoot("com.morpheus.domain", "")),
            Map.entry("morpheus-application", new ModuleRoot("com.morpheus.application", "")),
            Map.entry("morpheus-provider-sdk", new ModuleRoot("com.morpheus.sdk.provider",
                    "the package is published: a plugin registers "
                            + "META-INF/services/com.morpheus.sdk.provider.MorpheusProviderPlugin, so it cannot move")),
            Map.entry("morpheus-provider-testkit", new ModuleRoot("com.morpheus.sdk.provider.testkit",
                    "the testkit extends the SDK's published package by design")),
            Map.entry("morpheus-provider-reference", new ModuleRoot("com.morpheus.provider.reference", "")),
            Map.entry("morpheus-provider-openspec", new ModuleRoot("com.morpheus.provider.openspec", "")),
            Map.entry("morpheus-provider-markdown", new ModuleRoot("com.morpheus.provider.markdown", "")),
            Map.entry("morpheus-provider-synthetic", new ModuleRoot("com.morpheus.provider.synthetic", "")),
            Map.entry("morpheus-store-memory", new ModuleRoot("com.morpheus.store.memory", "")),
            Map.entry("morpheus-store-sqlite", new ModuleRoot("com.morpheus.store.sqlite", "")),
            Map.entry(MCP_TRANSPORT, new ModuleRoot("com.morpheus.transport.mcp",
                    "the mechanical name com.morpheus.mcp.transport lies inside the MCP adapter's package, which the "
                            + "MINOS and NEXUS integrations, the transport's clients, may not reach")),
            Map.entry("morpheus-integration-minos", new ModuleRoot("com.morpheus.integration.minos", "")),
            Map.entry("morpheus-integration-nexus", new ModuleRoot("com.morpheus.integration.nexus", "")),
            Map.entry("morpheus-mcp", new ModuleRoot("com.morpheus.mcp", "")),
            Map.entry("morpheus-api", new ModuleRoot("com.morpheus.api", "")),
            Map.entry("morpheus-cli", new ModuleRoot("com.morpheus.cli", ""))));

    /** A module whose root lies inside another module's root, mapped to that other module. */
    private static final Map<String, String> NESTED_INSIDE = Map.of(
            "morpheus-provider-testkit", "morpheus-provider-sdk");

    @Test
    void everyReactorModuleWithProductionSourcesDeclaresItsRootPackage() throws IOException {
        Set<String> undeclared = new TreeSet<>(sourcePackagesByModule().keySet());
        undeclared.removeAll(ROOTS.keySet());
        assertEquals(Set.of(), undeclared,
                "these reactor modules carry production sources but declare no root package in ROOTS");
    }

    @Test
    void everyDeclaredRootBelongsToAReactorModuleWithProductionSources() throws IOException {
        Set<String> orphans = new TreeSet<>(ROOTS.keySet());
        orphans.removeAll(sourcePackagesByModule().keySet());
        assertEquals(Set.of(), orphans,
                "these entries of ROOTS name no reactor module with production sources: remove them");
    }

    @Test
    void everyProductionPackageSitsUnderTheRootOfItsOwnModule() throws IOException {
        Map<String, String> misplaced = new TreeMap<>();
        for (Map.Entry<String, Set<String>> module : sourcePackagesByModule().entrySet()) {
            for (String sourcePackage : module.getValue()) {
                String owner = ownerOf(sourcePackage);
                if (!module.getKey().equals(owner)) {
                    misplaced.put(module.getKey() + " " + sourcePackage,
                            owner.isEmpty() ? "under no declared root" : "under the root of " + owner);
                }
            }
        }
        assertEquals(Map.of(), misplaced,
                "each production package must sit under its module's declared root, and under no more specific "
                        + "root of another module");
    }

    @Test
    void noRootEqualsOrLiesInsideAnotherModulesRootUnlessDeclared() {
        Map<String, String> nestings = new TreeMap<>();
        for (Map.Entry<String, ModuleRoot> inner : ROOTS.entrySet()) {
            for (Map.Entry<String, ModuleRoot> outer : ROOTS.entrySet()) {
                if (!inner.getKey().equals(outer.getKey())
                        && isUnder(inner.getValue().rootPackage(), outer.getValue().rootPackage())) {
                    nestings.put(inner.getKey(), outer.getKey());
                }
            }
        }
        assertEquals(new TreeMap<>(NESTED_INSIDE), nestings,
                "a module's root may equal or lie inside another module's root only where NESTED_INSIDE declares it, "
                        + "and NESTED_INSIDE declares only nestings that exist (inner module = outer module)");
    }

    @Test
    void everyRootThatIsNotTheModuleNameReadMechanicallyStatesWhy() {
        Map<String, String> unexplained = new TreeMap<>();
        for (Map.Entry<String, ModuleRoot> entry : ROOTS.entrySet()) {
            boolean departs = !entry.getValue().rootPackage().equals(mechanicalRoot(entry.getKey()));
            boolean reasoned = !entry.getValue().reason().isBlank();
            if (departs != reasoned) {
                unexplained.put(entry.getKey(), departs
                        ? "departs from " + mechanicalRoot(entry.getKey()) + " without a reason"
                        : "carries a reason although it is the mechanical reading");
            }
        }
        assertEquals(Map.of(), unexplained,
                "an entry states a reason exactly when its root is not the module name read mechanically");
    }

    @Test
    void theSharedMcpTransportResidesOutsideTheIntegrationsAndTheMcpAdapter() {
        classes()
                .that(areShippedBy(MCP_TRANSPORT))
                .should().resideOutsideOfPackages("com.morpheus.integration..", "com.morpheus.mcp..")
                .because("the bounded STDIO transport is shared by the MCP adapter and the MINOS and NEXUS "
                        + "integrations: a rule written for either must not reach it by accident")
                .check(PRODUCTION);
    }

    private static DescribedPredicate<JavaClass> areShippedBy(String module) {
        return DescribedPredicate.describe("are shipped by " + module,
                javaClass -> javaClass.getSource()
                        .map(source -> source.getUri().toString().contains("/" + module + "/"))
                        .orElse(false));
    }

    private static String ownerOf(String sourcePackage) {
        String owner = "";
        int longest = -1;
        for (Map.Entry<String, ModuleRoot> entry : ROOTS.entrySet()) {
            String root = entry.getValue().rootPackage();
            if (isUnder(sourcePackage, root) && root.length() > longest) {
                owner = entry.getKey();
                longest = root.length();
            }
        }
        return owner;
    }

    private static boolean isUnder(String packageName, String root) {
        return packageName.equals(root) || packageName.startsWith(root + ".");
    }

    private static String mechanicalRoot(String module) {
        return "com." + module.replace('-', '.');
    }

    private static Map<String, Set<String>> sourcePackagesByModule() throws IOException {
        Path root = RepositoryRoot.locate();
        Matcher matcher = MODULE.matcher(Files.readString(root.resolve("pom.xml")));
        Map<String, Set<String>> modules = new TreeMap<>();
        while (matcher.find()) {
            String module = matcher.group(1).trim();
            Path sources = root.resolve(module).resolve("src/main/java");
            if (!Files.isDirectory(sources)) {
                continue;
            }
            Set<String> packages = new TreeSet<>();
            try (Stream<Path> files = Files.walk(sources)) {
                files.filter(path -> path.toString().endsWith(".java"))
                        .forEach(path -> packages.add(
                                sources.relativize(path.getParent()).toString().replace('\\', '.').replace('/', '.')));
            }
            if (!packages.isEmpty()) {
                modules.put(module, packages);
            }
        }
        return modules;
    }
}
