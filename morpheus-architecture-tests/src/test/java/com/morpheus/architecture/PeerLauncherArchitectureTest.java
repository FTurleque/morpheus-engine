package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An MCP peer is started from a launcher named in full, in both integrations.
 *
 * <p>MINOS and NEXUS were started with the bare command {@code java}, which the operating system looks up on the
 * search path of the MORPHEUS process: another {@code java} placed first on that path would have been started as the
 * peer. The behaviour is held by the tests of each integration; what is held here is that the two settings classes,
 * which may not share code, both keep the invariant where it cannot be skipped -- in the constructor -- and that
 * neither falls back to a bare command again. The assertions are textual because what they forbid is a literal a
 * launch would be given, which no rule on dependencies can name.</p>
 */
class PeerLauncherArchitectureTest {
    private static final List<String> SETTINGS = List.of(
            "morpheus-integration-minos/src/main/java/com/morpheus/integration/minos/MinosIntegrationSettings.java",
            "morpheus-integration-nexus/src/main/java/com/morpheus/integration/nexus/NexusIntegrationSettings.java");
    private static final Pattern BARE_JAVA_LITERAL = Pattern.compile("\"java(?:\\.exe)?\"");

    @Test
    void bothIntegrationsRefuseALauncherTheSearchPathWouldResolve() throws IOException {
        for (String settings : SETTINGS) {
            String content = Files.readString(RepositoryRoot.locate().resolve(settings));

            assertTrue(content.contains("javaCommand = requireAbsoluteLauncher("),
                    () -> settings + " must refuse a launcher that is not absolute in its compact constructor");
            assertTrue(content.contains("System.getProperty(\"java.home\")"),
                    () -> settings + " must take its default launcher from the runtime MORPHEUS runs on");
            assertTrue(content.contains(".toRealPath()"),
                    () -> settings + " must launch and report the file a configured launcher resolves to");
            assertFalse(content.contains("orElse(\"java\")"),
                    () -> settings + " must not fall back to a command the search path resolves");
        }
    }

    @Test
    void theOnlyBareLauncherNamesAreTheTwoFilesLookedForUnderTheRuntime() throws IOException {
        for (String settings : SETTINGS) {
            String content = Files.readString(RepositoryRoot.locate().resolve(settings));
            String withoutTheRuntimeLookup = content
                    .replace("bin.resolve(\"java.exe\")", "")
                    .replace("bin.resolve(\"java\")", "");

            assertFalse(BARE_JAVA_LITERAL.matcher(withoutTheRuntimeLookup).find(),
                    () -> settings + " names a bare java command outside the lookup under java.home");
        }
    }
}
