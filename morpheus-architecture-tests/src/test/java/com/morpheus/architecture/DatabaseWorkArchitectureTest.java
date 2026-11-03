package com.morpheus.architecture;

import com.morpheus.api.local.DatabaseCallWorkers;
import com.morpheus.api.local.DatabaseWorkOffloadingHttpServer;
import com.morpheus.store.sqlite.runtime.SqliteServerMaintenance;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Database work of both HTTP servers runs on the database workers, never on a request's virtual thread (ADR-0159).
 *
 * <p>The SQLite driver waits for a busy database inside native code, where a virtual thread keeps its carrier, and
 * there are as many carriers as processors. The local server hands every handler that can reach the database to the
 * workers; the remote facade hands them the SQLite work it does itself. Both go through one mechanism,
 * {@link DatabaseCallWorkers}, and the work is a {@link DatabaseCallWorkers.DatabaseCall} that only the workers run.
 * These are properties of calls in the bytecode, which a rule sees through any spelling and across every class of the
 * adapter, where a scan of one source file sees only what that file spells.</p>
 */
class DatabaseWorkArchitectureTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus");

    /** The exclusive lease is taken once, by the remote bootstrap, before the facade serves any request. */
    private static final String TAKEN_AT_START_UP = "acquireServerLease";

    private static final DescribedPredicate<JavaMethodCall> SQLITE_MAINTENANCE_WORK = DescribedPredicate.describe(
            "SQLite maintenance work other than the lease taken at start-up",
            call -> call.getTargetOwner().getName().equals(SqliteServerMaintenance.class.getName())
                    && !call.getName().equals(TAKEN_AT_START_UP));

    /** A bridge the compiler writes for a narrower result type forwards a call; it does not run one. */
    private static final DescribedPredicate<JavaMethodCall> RUNS_A_DATABASE_CALL = DescribedPredicate.describe(
            "runs a database call",
            call -> call.getName().equals("run")
                    && call.getTargetOwner().isAssignableTo(DatabaseCallWorkers.DatabaseCall.class)
                    && !call.getOrigin().getModifiers().contains(JavaModifier.BRIDGE));

    private static final DescribedPredicate<JavaMethodCall> BUILDS_A_POOL_OF_PLATFORM_THREADS =
            DescribedPredicate.describe(
                    "builds a pool of platform threads",
                    call -> call.getTargetOwner().getName().equals(Thread.class.getName())
                            && call.getName().equals("ofPlatform")
                            || call.getTargetOwner().getName().equals(Executors.class.getName())
                            && call.getName().equals("newFixedThreadPool"));

    @Test
    void theSqliteMaintenanceOfTheHttpAdapterIsCalledOnlyFromADatabaseCall() {
        noClasses()
                .that().resideInAPackage("com.morpheus.api..")
                .and(DescribedPredicate.not(assignableTo(DatabaseCallWorkers.DatabaseCall.class)))
                .should().callMethodWhere(SQLITE_MAINTENANCE_WORK)
                .because("a SQLite call made on a request's virtual thread holds a carrier for as long as the driver "
                        + "waits on a busy database; the facade's SQLite work is a database call, named so that this "
                        + "rule sees it - a lambda's body would belong to the class that writes it (ADR-0159)")
                .check(PRODUCTION);
    }

    @Test
    void onlyTheDatabaseWorkersRunADatabaseCall() {
        noClasses()
                .that(DescribedPredicate.not(belongToAnyOf(DatabaseCallWorkers.class)))
                .should().callMethodWhere(RUNS_A_DATABASE_CALL)
                .because("a database call run anywhere but on a worker waits on the database on its caller's thread "
                        + "(ADR-0159)")
                .check(PRODUCTION);
    }

    @Test
    void theLocalServerHandsItsHandlersToTheDatabaseWorkers() {
        classes()
                .that().haveFullyQualifiedName(DatabaseWorkOffloadingHttpServer.class.getName())
                .should().callMethod(DatabaseCallWorkers.class, "runToItsEnd", DatabaseCallWorkers.DatabaseCall.class)
                .because("the local server runs every handler that can reach the database on a database worker; its "
                        + "routes that answer without the database are registered outside it (ADR-0159)")
                .check(PRODUCTION);
    }

    @Test
    void theDatabaseWorkersAreBuiltByOneClass() {
        noClasses()
                .that().resideInAPackage("com.morpheus.api..")
                .and(DescribedPredicate.not(belongToAnyOf(DatabaseCallWorkers.class)))
                .should().callMethodWhere(BUILDS_A_POOL_OF_PLATFORM_THREADS)
                .because("the local server and the remote facade use one mechanism for database work, with one size "
                        + "and one bounded admission, not a copy of it (ADR-0159)")
                .check(PRODUCTION);
    }
}
