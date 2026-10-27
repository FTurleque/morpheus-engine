package com.morpheus.architecture.d2;

import com.morpheus.architecture.RepositoryRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A dependency held on a release line on purpose still receives the patches of that line.
 *
 * <p>The root POM keeps Reactor on the line the MCP SDK is built on. It stayed on the first release of that line for
 * nineteen months: the update bot could only propose the next line, which the POM says to refuse, so Reactor was
 * refused whole, patches included. The bot is told to ignore changes of line for that one artifact, and this holds the
 * instruction and its reason together -- one without the other is the defect.</p>
 */
class DependencyLineCurrencyTest {
    private static final String REACTOR_LINE_COMMENT = "Keep the transport on that compatible train.";
    private static final Pattern REACTOR_VERSION =
            Pattern.compile("<reactor-bom\\.version>(\\d+)\\.(\\d+)\\.(\\d+)</reactor-bom\\.version>");
    private static final Pattern REACTOR_IGNORE = Pattern.compile(
            "- dependency-name: \"?io\\.projectreactor:reactor-bom\"?\\s*\\R\\s*update-types:\\s*\\[([^\\]]*)]");

    @Test
    void reactorIsOnAPatchedReleaseOfTheLineThePomHolds() throws IOException {
        String pom = Files.readString(RepositoryRoot.locate().resolve("pom.xml"));
        Matcher version = REACTOR_VERSION.matcher(pom);
        assertTrue(version.find(), "the root POM must pin reactor-bom.version as a three-part release");
        assertTrue(pom.contains(REACTOR_LINE_COMMENT),
                "the root POM must keep the comment that says why Reactor is held on a line");

        assertEquals("2024.0", version.group(1) + "." + version.group(2),
                "the comment of the root POM names the Reactor 2024.0.x line; a change of line follows an MCP SDK "
                        + "upgrade and is decided with it");
        assertTrue(Integer.parseInt(version.group(3)) > 0,
                "Reactor must not sit on the first release of its line while that line publishes patches");
    }

    @Test
    void theUpdateBotProposesThePatchesOfTheHeldLineAndNothingElse() throws IOException {
        Path root = RepositoryRoot.locate();
        boolean lineIsHeld = Files.readString(root.resolve("pom.xml")).contains(REACTOR_LINE_COMMENT);
        Matcher ignore = REACTOR_IGNORE.matcher(Files.readString(root.resolve(".github/dependabot.yml")));

        if (!lineIsHeld) {
            assertFalse(ignore.find(), "the root POM no longer holds Reactor on a line: remove the ignore entry for "
                    + "io.projectreactor:reactor-bom from .github/dependabot.yml, or the bot never proposes a new line");
            return;
        }
        assertTrue(ignore.find(), "the root POM holds Reactor on a line and .github/dependabot.yml has no ignore "
                + "entry for io.projectreactor:reactor-bom: the bot would propose a change of line, and refusing it "
                + "refuses the patches too");
        String updateTypes = ignore.group(1);
        assertTrue(updateTypes.contains("version-update:semver-major")
                        && updateTypes.contains("version-update:semver-minor"),
                "the ignore entry must cover major and minor updates of the Reactor BOM: " + updateTypes);
        assertFalse(updateTypes.contains("version-update:semver-patch"),
                "the ignore entry must leave the patches of the line open: " + updateTypes);
    }
}
