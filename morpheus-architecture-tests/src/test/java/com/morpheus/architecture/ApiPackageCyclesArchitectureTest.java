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
 * The packages of the HTTP adapter form no cycle.
 *
 * <p>Each package of {@code com.morpheus.api} is a slice, the parent package included: it holds the application-facing
 * services and {@code ApiRuntime}. The layering the packages carry is the one measured before the classes moved:
 * the remote server above the local server, the local server above the routers, the routers above the services, the
 * transport plumbing below them all. A dependency back up that order closes a cycle and fails here, naming both
 * packages. A cycle that is accepted is named by the exact pair of types that closes it, with its reason, never by a
 * looser pattern; there is none, and this rule has no exception.</p>
 *
 * <p>A cycle rule over fewer packages holds over less, so the rule first asserts that every package of the layering
 * exists.</p>
 */
class ApiPackageCyclesArchitectureTest {

    private static final String API = "com.morpheus.api";

    /** The packages the layering speaks of, from the top. */
    private static final Set<String> LAYERED_PACKAGES = Set.of(API + ".remote", API + ".local",
            API + ".routes.bodiless", API + ".routes.shareddecoder", API + ".routes.owncontext", API, API + ".transport");

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(API);

    private static final SliceAssignment API_PACKAGES = new SliceAssignment() {
        @Override
        public SliceIdentifier getIdentifierOf(JavaClass javaClass) {
            String packageName = javaClass.getPackageName();
            if (packageName.equals(API) || packageName.startsWith(API + ".")) {
                return SliceIdentifier.of(packageName);
            }
            return SliceIdentifier.ignore();
        }

        @Override
        public String getDescription() {
            return "the packages of " + API;
        }
    };

    @Test
    void thePackagesOfTheHttpAdapterFormNoCycle() {
        Set<String> packages = new TreeSet<>();
        for (JavaClass javaClass : PRODUCTION) {
            if (javaClass.getPackageName().startsWith(API)) {
                packages.add(javaClass.getPackageName());
            }
        }
        assertTrue(packages.containsAll(LAYERED_PACKAGES), () -> "the cycle rule is written over the packages of "
                + API + " " + LAYERED_PACKAGES + "; it holds over less than that when one is missing: " + packages);

        slices().assignedFrom(API_PACKAGES)
                .should().beFreeOfCycles()
                .because("the adapter's packages are layered: remote over local, local over the routers, the "
                        + "routers over the services, the transport plumbing under all of them")
                .check(PRODUCTION);
    }
}
