package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ownership rules that find their own subjects.
 *
 * <p>The older contract named the assemblers it protected, so it guarded the ones already fixed and said
 * nothing about the next one somebody writes -- which is how six runtimes ended up assembling seven stores
 * each with no owner at all while the rule sat green beside them. These tests discover the shape instead:
 * anything that acquires several resources before it is finished, and anything that releases several, has to
 * use the shared mechanism whether or not this file has ever heard of it.</p>
 */
class MultiResourceOwnershipContractTest {

    /** A constructor body: the name, the parameter list, then everything up to the matching closing brace. */
    private static final Pattern CONSTRUCTOR = Pattern.compile(
            "\\n(\\s*)(?:private |public |protected )?(\\w+)\\(([^)]*)\\)\\s*\\{(.*?)\\n\\1\\}", Pattern.DOTALL);
    private static final Pattern SQLITE_STORE_ACQUISITION = Pattern.compile("new (Sqlite\\w+)\\(databasePath");
    private static final Pattern CLOSE_BODY = Pattern.compile(
            "\\n(\\s*)public void close\\(\\)[^{]*\\{(.*?)\\n\\1\\}", Pattern.DOTALL);
    private static final Pattern BARE_RELEASE = Pattern.compile(
            "^\\s*\\w+\\.(?:close|shutdownNow|shutdown)\\(\\s*\\);\\s*$", Pattern.MULTILINE);

    /**
     * Anything that opens several SQLite stores while building itself must own them as it goes.
     *
     * <p>A constructor that fails partway never returns, so the object that would have closed what it already
     * opened is never built: the connections, and the database leases behind them, stay held for the life of
     * the process with nothing left able to release them.</p>
     */
    @Test
    void everyAssemblerThatOpensSeveralStoresOwnsThemWhileItBuilds() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path source : mainSources()) {
            String content = Files.readString(source);
            Matcher constructor = CONSTRUCTOR.matcher(content);
            while (constructor.find()) {
                String constructorBody = constructor.group(4);
                if (countMatches(SQLITE_STORE_ACQUISITION, constructorBody) < 2) {
                    continue;
                }
                if (!constructorBody.contains("StartupOwnership")
                        || !constructorBody.contains("owned.transferred();")) {
                    offenders.add(relative(source) + " :: " + constructor.group(2));
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                () -> "these assemblers open several SQLite stores without holding them until assembly finishes,"
                        + " so a failure partway leaks every store opened before it: " + offenders);
    }

    /**
     * Anything that releases several resources must release all of them.
     *
     * <p>Written as a sequence of close calls, the first failure ends the method and everything after it stays
     * held. What gets skipped is whatever is released last, which is usually what matters most.</p>
     */
    @Test
    void everyOwnerOfSeveralResourcesReleasesAllOfThem() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path source : mainSources()) {
            String content = Files.readString(source);
            Matcher close = CLOSE_BODY.matcher(content);
            while (close.find()) {
                String closeBody = close.group(2);
                if (countMatches(BARE_RELEASE, closeBody) >= 2 && !closeBody.contains("ExhaustiveShutdown")) {
                    offenders.add(relative(source));
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                () -> "these close methods release several resources in sequence, so the first failure leaves"
                        + " the rest held; ExhaustiveShutdown releases every one of them: " + offenders);
    }

    /**
     * Every SQLite store initializes through the one owned path.
     *
     * <p>Each store used to open a connection and then migrate it, unwinding from a
     * {@code catch (SQLException | RuntimeException)}. An {@link Error} raised while migrating walked past
     * that catch and left the connection, and the shared database lease behind it, held with no owner.</p>
     */
    @Test
    void everySqliteStoreInitializesThroughTheOwnedSharedPath() throws IOException {
        Path stores = repositoryRoot().resolve("morpheus-store-sqlite/src/main/java/com/morpheus/store/sqlite");
        assertTrue(Files.isRegularFile(stores.resolve("SqliteStoreConnection.java")),
                "the shared owned initialization path must exist");

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> candidates = Files.list(stores)) {
            List<Path> storeSources = candidates
                    .filter(path -> path.getFileName().toString().endsWith("Store.java"))
                    .sorted()
                    .toList();
            assertFalse(storeSources.isEmpty(), "the scan must find the stores it is meant to protect");
            for (Path store : storeSources) {
                String content = Files.readString(store);
                if (!content.contains("SqliteDatabaseSecurity.open")
                        && !content.contains("SqliteStoreConnection")) {
                    continue;
                }
                if (!content.contains("SqliteStoreConnection.openAndMigrate(")) {
                    offenders.add(relative(store) + " (does not use the owned initialization path)");
                } else if (content.contains("closeQuietly(opened)")) {
                    offenders.add(relative(store) + " (still unwinds by hand)");
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                () -> "these stores open a connection and initialize it without an owner, so an Error during"
                        + " migration leaks the connection and its database lease: " + offenders);
    }

    /** The shared primitives must keep releasing on Error, which is the case the hand-written unwinds missed. */
    @Test
    void theSharedPrimitivesKeepReleasingWhenAnErrorUnwindsTheAssembly() throws IOException {
        Path operability = repositoryRoot()
                .resolve("morpheus-application/src/main/java/com/morpheus/application/operability");
        String startup = Files.readString(operability.resolve("StartupOwnership.java"));
        String shutdown = Files.readString(operability.resolve("ExhaustiveShutdown.java"));

        assertTrue(startup.contains("catch (RuntimeException | Error releaseFailure)"),
                "startup ownership must release what it holds when assembly fails on an Error");
        assertTrue(shutdown.contains("catch (RuntimeException | Error failure)"),
                "exhaustive shutdown must keep releasing when one release fails on an Error");
        assertTrue(shutdown.contains("if (primary != failure)"),
                "suppression must stay guarded against a throwable suppressed into itself");
    }

    private static int countMatches(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static String relative(Path source) throws IOException {
        return repositoryRoot().relativize(source).toString().replace('\\', '/');
    }

    private static List<Path> mainSources() throws IOException {
        Path root = repositoryRoot();
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> modules = Files.list(root)) {
            for (Path module : modules.filter(Files::isDirectory).sorted().toList()) {
                Path main = module.resolve("src/main/java");
                if (!Files.isDirectory(main)) {
                    continue;
                }
                try (Stream<Path> walk = Files.walk(main)) {
                    walk.filter(path -> path.toString().endsWith(".java")).sorted().forEach(sources::add);
                }
            }
        }
        assertFalse(sources.isEmpty(), "the scan must find the production sources it is meant to protect");
        return sources;
    }

    private static Path repositoryRoot() throws IOException {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path pom = current.resolve("pom.xml");
            if (Files.isRegularFile(pom)) {
                String content = Files.readString(pom);
                if (content.contains("<artifactId>morpheus-engine</artifactId>")
                        && content.contains("<modules>")) {
                    return current;
                }
            }
            current = current.getParent();
        }
        throw new IOException("cannot locate MORPHEUS repository root");
    }
}
