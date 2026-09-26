package com.morpheus.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Case conversion on a public boundary must not depend on the operator's default locale.
 *
 * <p>{@code String.toUpperCase()} without a locale uses the default one, and Turkish and Azerbaijani map
 * {@code i} to {@code İ}. Every MORPHEUS enum whose name contains an {@code i} -- {@code IMPLEMENTING},
 * {@code SPECIFIED}, {@code MISSING}, {@code INCOMING} -- therefore fails {@code valueOf} on those machines for
 * input that is valid everywhere else. It had already happened asymmetrically: the CLI parsed policy, portfolio
 * and query enums through {@code Locale.ROOT} while the HTTP and MCP surfaces of the same capabilities did not,
 * so one transport accepted an operator's value and the other refused it on the same machine.</p>
 *
 * <p>Nothing about that failure is visible on a developer machine in an English or French locale, which is why
 * it needs a rule rather than a review.</p>
 */
class LocaleIndependentTextContractTest {
    private static final List<String> LOCALE_SENSITIVE = List.of(".toUpperCase()", ".toLowerCase()");

    @Test
    void noProductionSourceConvertsCaseWithoutAnExplicitLocale() throws IOException {
        Path root = repositoryRoot();
        List<String> offenders = new ArrayList<>();

        for (Path source : productionSources(root)) {
            String content = Files.readString(source);
            for (String call : LOCALE_SENSITIVE) {
                if (content.contains(call)) {
                    offenders.add(root.relativize(source) + " -> " + call);
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                () -> "case conversion must pass an explicit Locale, normally Locale.ROOT:\n  "
                        + String.join("\n  ", offenders));
    }

    /**
     * The rule is only worth anything if the intended form is actually in use somewhere, so the sweep above
     * cannot be satisfied by removing every conversion instead of fixing it.
     */
    @Test
    void theIntendedFormIsTheOneInUse() throws IOException {
        Path root = repositoryRoot();
        long rooted = 0;
        for (Path source : productionSources(root)) {
            String content = Files.readString(source);
            rooted += content.split("toUpperCase\\(Locale\\.ROOT\\)|toLowerCase\\(Locale\\.ROOT\\)", -1).length - 1;
        }

        long observed = rooted;
        assertTrue(rooted > 50,
                () -> "expected the repository to normalise text through Locale.ROOT, found " + observed);
    }

    private static List<Path> productionSources(Path root) throws IOException {
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> modules = Files.list(root)) {
            for (Path module : modules.filter(Files::isDirectory).sorted().toList()) {
                Path main = module.resolve("src/main/java");
                if (!Files.isDirectory(main)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(main)) {
                    files.filter(path -> path.getFileName().toString().endsWith(".java")).forEach(sources::add);
                }
            }
        }
        assertTrue(sources.size() > 100, "expected the reactor's production sources to be discovered");
        return sources;
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("contracts/public-surfaces.tsv"))
                    && Files.isRegularFile(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("MORPHEUS repository root not found");
    }
}
