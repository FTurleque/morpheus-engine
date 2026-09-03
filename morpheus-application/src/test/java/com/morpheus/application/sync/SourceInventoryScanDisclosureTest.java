package com.morpheus.application.sync;

import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;

import java.nio.file.AccessDeniedException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A scan failure carries text MORPHEUS did not write.
 *
 * <p>The platform puts the pathname in it — {@link AccessDeniedException} reports the pathname and nothing
 * else — and a source outside the workspace renders absolute. The projection a caller outside the machine
 * receives keeps the stable code and drops anything that names a location, on either platform's path shape,
 * because a partially scrubbed pathname is still a pathname.</p>
 */
class SourceInventoryScanDisclosureTest {
    private static final String WINDOWS_SENTINEL = "C:\\secret\\server\\workspace\\classified\\spec.md";
    private static final String POSIX_SENTINEL = "/srv/morpheus/private/workspace/classified/spec.md";

    @Test
    void aPlatformFailureMessageNamingAPathnameIsNotRelayed() {
        for (String sentinel : List.of(WINDOWS_SENTINEL, POSIX_SENTINEL)) {
            SourceInventoryScanResult.Failure failure = new SourceInventoryScanResult.Failure(
                    Optional.of("openspec/spec.md"),
                    SourceInventoryScanResult.Failure.Code.SOURCE_UNREADABLE,
                    new AccessDeniedException(sentinel).getMessage());

            SourceInventoryScanResult.PublicView view = failure.publicView();

            assertEquals(SourceInventoryScanResult.Failure.Code.SOURCE_UNREADABLE, view.code());
            assertTrue(view.detail().isEmpty(), () -> "a pathname message must be dropped, not relayed: " + view);
            assertFalse(view.toString().contains(sentinel), () -> "rendered view leaked the pathname: " + view);
            assertFalse(view.toString().contains("classified"),
                    () -> "rendered view leaked a server directory name: " + view);
            assertTrue(view.toString().contains("SOURCE_UNREADABLE"), view::toString);
        }
    }

    @Test
    void aSourceRenderedAbsolutelyIsNotRelayedEither() {
        for (String sentinel : List.of(WINDOWS_SENTINEL, POSIX_SENTINEL)) {
            SourceInventoryScanResult.Failure failure = new SourceInventoryScanResult.Failure(
                    Optional.of(sentinel),
                    SourceInventoryScanResult.Failure.Code.WORKSPACE_BOUNDARY_ESCAPED,
                    "source escapes the workspace");

            SourceInventoryScanResult.PublicView view = failure.publicView();

            assertTrue(view.source().isEmpty(), () -> "an absolute source must be dropped: " + view);
            assertFalse(view.toString().contains(sentinel), () -> "rendered view leaked the source: " + view);
            assertEquals(
                    SourceInventoryScanResult.Failure.Code.WORKSPACE_BOUNDARY_ESCAPED + ": source escapes the workspace",
                    view.toString());
        }
    }

    /** MORPHEUS's own bounded diagnostics name no location, so they survive the projection intact. */
    @Test
    void aMorpheusAuthoredDiagnosticSurvivesTheProjection() {
        SourceInventoryScanResult.Failure failure = new SourceInventoryScanResult.Failure(
                Optional.of("openspec/deep"),
                SourceInventoryScanResult.Failure.Code.SCAN_LIMIT_EXCEEDED,
                "source scan depth exceeds limit 32");

        SourceInventoryScanResult.PublicView view = failure.publicView();

        assertEquals(Optional.of("openspec/deep"), view.source());
        assertEquals(Optional.of("source scan depth exceeds limit 32"), view.detail());
        assertEquals("SCAN_LIMIT_EXCEEDED at openspec/deep: source scan depth exceeds limit 32", view.toString());
    }

    /** The whole result projects in the order the failures are already sorted in. */
    @Test
    void theResultProjectsEveryFailureDeterministically() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        SourceInventoryScanResult scan = SourceInventoryScanResult.incomplete(projectId, List.of(
                new SourceInventoryScanResult.Failure(
                        Optional.of("openspec/b.md"),
                        SourceInventoryScanResult.Failure.Code.SOURCE_UNREADABLE,
                        new AccessDeniedException(POSIX_SENTINEL).getMessage()),
                new SourceInventoryScanResult.Failure(
                        Optional.of("openspec/a.md"),
                        SourceInventoryScanResult.Failure.Code.SOURCE_ROOT_MISSING,
                        "source root does not exist")));

        assertEquals(
                "[SOURCE_ROOT_MISSING at openspec/a.md: source root does not exist, SOURCE_UNREADABLE at openspec/b.md]",
                scan.publicFailures().toString());
    }
}
