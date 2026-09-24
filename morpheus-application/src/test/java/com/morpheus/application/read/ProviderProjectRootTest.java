package com.morpheus.application.read;

import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderProjectRootTest {
    @TempDir
    Path temporary;

    /**
     * The single point spells a root exactly as the readers that already published the workspace did.
     *
     * <p>Two expressions were in use before it existed. The OpenSpec current-specification and change readers
     * normalized the {@code Path} they were given, then called {@code SourceLocator.file(root.toString())}; the
     * OpenSpec content, reference and synthetic readers took {@code request.workspaceRoot()}, which the request
     * record had already normalized. Any input on which the new point disagreed with either would change a root
     * those readers published, and every project registered under it would stop publishing.</p>
     */
    @Test
    void spellsEveryWorkspaceExactlyAsBothFormerReaderExpressionsDid() {
        List<Path> workspaces = List.of(
                Path.of("workspace"),
                Path.of("./workspace/./nested"),
                Path.of("workspace", "..", "sibling"),
                Path.of("workspace\\with\\backslashes"),
                temporary,
                temporary.resolve("a").resolve("..").resolve("b"),
                temporary.resolve("trailing").resolve("."));

        for (Path workspace : workspaces) {
            SourceLocator expected = ProviderProjectRoot.locator(workspace);
            SourceLocator normalizedPathForm = SourceLocator.file(
                    Objects.requireNonNull(workspace).toAbsolutePath().normalize().toString());
            ProviderReadRequest request = ProviderReadRequest.all(workspace, ProjectSpecificationId.generate());
            SourceLocator requestForm = SourceLocator.file(request.workspaceRoot().toString());

            assertEquals(normalizedPathForm, expected, workspace::toString);
            assertEquals(requestForm, expected, workspace::toString);
            assertEquals(expected, ProviderProjectRoot.locator(request.workspaceRoot()), workspace::toString);
        }
    }

    @Test
    void publishesAnAbsoluteSlashSeparatedRootWithoutDotSegments() {
        SourceLocator locator = ProviderProjectRoot.locator(Path.of("workspace", "..", "sibling"));

        assertEquals("file", locator.scheme());
        assertTrue(Path.of(locator.value()).isAbsolute(), locator::value);
        assertFalse(locator.value().contains("\\"), locator::value);
        assertFalse(locator.value().contains("/../"), locator::value);
        assertTrue(locator.value().endsWith("/sibling"), locator::value);
    }

    @Test
    void refusesAMissingWorkspace() {
        NullPointerException failure = assertThrows(NullPointerException.class, () -> ProviderProjectRoot.locator(null));
        assertEquals("workspaceRoot", failure.getMessage());
    }
}
