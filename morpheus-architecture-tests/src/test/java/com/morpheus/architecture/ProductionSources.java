package com.morpheus.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Finds the source file of a class of a reactor module from its simple name, for the gates that read a source as text.
 *
 * <p>A gate names the class it reads, not the folder it is in: a class moved to another package of its module keeps
 * every gate that reads it, and only a rule about its package has to change. The search covers the whole source tree
 * of the module and refuses a name it does not find or finds twice, so a gate never reads the wrong file or none. No
 * module of the reactor declares two classes of the same simple name; the refusal is what keeps that true for the
 * classes a gate reads.</p>
 */
public final class ProductionSources {

    private ProductionSources() {
    }

    /** The source of {@code simpleName} under {@code <module>/src/main/java}. */
    public static Path mainSource(String module, String simpleName) {
        return find(RepositoryRoot.locate().resolve(module).resolve("src/main/java"), simpleName);
    }

    /** The source of {@code simpleName} under {@code <module>/src/test/java}. */
    public static Path testSource(String module, String simpleName) {
        return find(RepositoryRoot.locate().resolve(module).resolve("src/test/java"), simpleName);
    }

    /** {@link #mainSource} relative to the repository root, with forward slashes, as gates that list offenders print it. */
    public static Path relativeMainSource(String module, String simpleName) {
        Path root = RepositoryRoot.locate();
        return Path.of(root.relativize(mainSource(module, simpleName)).toString().replace('\\', '/'));
    }

    /** {@link #testSource} relative to the repository root, with forward slashes. */
    public static Path relativeTestSource(String module, String simpleName) {
        Path root = RepositoryRoot.locate();
        return Path.of(root.relativize(testSource(module, simpleName)).toString().replace('\\', '/'));
    }

    /** The text of the main source of {@code simpleName} in {@code module}. */
    public static String readMain(String module, String simpleName) {
        try {
            return Files.readString(mainSource(module, simpleName));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    static Path find(Path sourceRoot, String simpleName) {
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalStateException("no source tree at " + sourceRoot);
        }
        String fileName = simpleName + ".java";
        List<Path> found;
        try (Stream<Path> tree = Files.walk(sourceRoot)) {
            found = tree.filter(path -> path.getFileName().toString().equals(fileName)).sorted().toList();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        if (found.isEmpty()) {
            throw new IllegalStateException("no source of " + simpleName + " under " + sourceRoot);
        }
        if (found.size() > 1) {
            throw new IllegalStateException(simpleName + " is ambiguous under " + sourceRoot + ": " + found);
        }
        return found.get(0);
    }
}
