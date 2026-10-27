package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An MCP peer is started from a launcher named in full, in both integrations.
 *
 * <p>A bare command {@code java} is looked up by the operating system on the search path of the MORPHEUS process:
 * another {@code java} placed first on that path would be started as the peer. The behaviour is held by the tests of
 * the resolver and of each integration; what is held here is that the one implementation of the launch settings,
 * shared by both peers, keeps the invariant where it cannot be skipped -- in the constructor of the shared result,
 * which each settings type builds in its own constructor -- and that neither it nor a settings type falls back to a
 * bare command again. The assertions are textual because what they forbid is a literal a launch would be given, which
 * no rule on dependencies can name.</p>
 */
class PeerLauncherArchitectureTest {
    private static final String SECURITY = "morpheus-application/src/main/java/com/morpheus/application/security/";
    private static final String SHARED_SETTINGS = SECURITY + "PeerLaunchSettings.java";
    private static final String RESOLVER = SECURITY + "PeerLaunchSettingsResolver.java";
    private static final List<String> SETTINGS = List.of(
            "morpheus-integration-minos/src/main/java/com/morpheus/integration/minos/MinosIntegrationSettings.java",
            "morpheus-integration-nexus/src/main/java/com/morpheus/integration/nexus/NexusIntegrationSettings.java");
    private static final Pattern BARE_JAVA_LITERAL = Pattern.compile("\"java(?:\\.exe)?\"");

    @Test
    void bothIntegrationsRefuseALauncherTheSearchPathWouldResolve() throws IOException {
        String shared = Files.readString(RepositoryRoot.locate().resolve(SHARED_SETTINGS));
        String resolver = Files.readString(RepositoryRoot.locate().resolve(RESOLVER));

        assertTrue(shared.contains("javaCommand = requireAbsoluteLauncher("),
                () -> SHARED_SETTINGS + " must refuse a launcher that is not absolute in its compact constructor");
        assertTrue(resolver.contains("System.getProperty(\"java.home\")"),
                () -> RESOLVER + " must take its default launcher from the runtime MORPHEUS runs on");
        assertTrue(resolver.contains(".toRealPath()"),
                () -> RESOLVER + " must launch and report the file a configured launcher resolves to");
        for (String settings : SETTINGS) {
            String content = Files.readString(RepositoryRoot.locate().resolve(settings));
            assertTrue(content.contains("PeerLaunchSettings launch = new PeerLaunchSettings("),
                    () -> settings + " must build its components through the shared settings in its compact "
                            + "constructor, so that it cannot hold a launcher the shared settings refuse");
        }
        for (String file : implementationAndSettings()) {
            String content = Files.readString(RepositoryRoot.locate().resolve(file));
            assertFalse(content.contains("orElse(\"java\")"),
                    () -> file + " must not fall back to a command the search path resolves");
        }
    }

    @Test
    void theOnlyBareLauncherNamesAreTheTwoFilesLookedForUnderTheRuntime() throws IOException {
        for (String file : implementationAndSettings()) {
            String content = Files.readString(RepositoryRoot.locate().resolve(file));
            String withoutTheRuntimeLookup = content
                    .replace("bin.resolve(\"java.exe\")", "")
                    .replace("bin.resolve(\"java\")", "");

            assertFalse(BARE_JAVA_LITERAL.matcher(withoutTheRuntimeLookup).find(),
                    () -> file + " names a bare java command outside the lookup under java.home");
        }
    }

    private static List<String> implementationAndSettings() {
        return List.of(SHARED_SETTINGS, RESOLVER, SETTINGS.get(0), SETTINGS.get(1));
    }

}
