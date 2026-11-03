package com.morpheus.architecture;

import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.regex.Pattern;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A refusal at the workspace boundary of a scan is reported under its own name, never as an unreadable source
 * (ADR-0157).
 *
 * <p>The scan classes every {@code IOException} it does not recognise as {@code SOURCE_UNREADABLE} and goes on. A
 * boundary refusal thrown as a bare {@code IOException} would therefore be reported as a read failure and let the walk
 * continue through the link it refused. The rule holds the throwing side on the bytecode: the boundary constructs no
 * bare {@code IOException}. The catching side is held on the text, the only place that says which code a catch block
 * reports.</p>
 */
class WorkspaceBoundaryRefusalArchitectureTest {
    private static final String SYNC = "com.morpheus.application.sync";
    private static final String SCAN_BOUNDARY = SYNC + ".ScanWorkspaceBoundary";

    private static final JavaClasses SYNC_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(SYNC);

    private static final Pattern ESCAPE_REPORTED_UNDER_ITS_OWN_CODE = Pattern.compile(
            "catch\\s*\\(\\s*WorkspaceBoundaryEscapeException\\s+\\w+\\s*\\)\\s*\\{[^}]*"
                    + "Code\\.WORKSPACE_BOUNDARY_ESCAPED[^}]*budget\\.exhaust\\(\\)[^}]*FileVisitResult\\.TERMINATE");

    @Test
    void theScanBoundaryRefusesOnlyThroughItsNamedException() {
        noClasses()
                .that().haveFullyQualifiedName(SCAN_BOUNDARY)
                .should().callConstructorWhere(JavaCall.Predicates.target(
                        AccessTarget.Predicates.declaredIn(IOException.class)))
                .because("a boundary refusal thrown as a bare IOException is reported SOURCE_UNREADABLE and the scan "
                        + "goes on; every refusal of the boundary is a WorkspaceBoundaryEscapeException (ADR-0157)")
                .check(SYNC_CLASSES);
    }

    @Test
    void theScannerReportsAnEscapeAfterReadingAsAnEscapeAndStops() {
        String scanner = ProductionSources.readMain("morpheus-application", "LocalSourceInventoryScanner");

        assertTrue(ESCAPE_REPORTED_UNDER_ITS_OWN_CODE.matcher(scanner).find(),
                "LocalSourceInventoryScanner must catch WorkspaceBoundaryEscapeException before any IOException, "
                        + "report it WORKSPACE_BOUNDARY_ESCAPED, exhaust the budget and terminate the walk (ADR-0157)");
    }
}
