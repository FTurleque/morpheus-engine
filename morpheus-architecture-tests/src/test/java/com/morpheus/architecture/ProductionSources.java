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
 *
 * <p>The repository root is the nearest ancestor of the working directory whose {@code pom.xml} declares the
 * reactor's {@code <modules>}: a module POM names the reactor as its parent but declares no modules, so the anchor
 * defines the root rather than recognising it by a neighbouring file.</p>
 */
public final class ProductionSources {

    private ProductionSources() {
    }

    /** The source of {@code simpleName} under {@code <module>/src/main/java}. */
    public static Path mainSource(String module, String simpleName) {
        return find(repositoryRoot().resolve(module).resolve("src/main/java"), simpleName);
    }

    /** The source of {@code simpleName} under {@code <module>/src/test/java}. */
    public static Path testSource(String module, String simpleName) {
        return find(repositoryRoot().resolve(module).resolve("src/test/java"), simpleName);
    }

    /** {@link #mainSource} relative to the repository root, with forward slashes, as gates that list offenders print it. */
    public static Path relativeMainSource(String module, String simpleName) {
        Path root = repositoryRoot();
        return Path.of(root.relativize(mainSource(module, simpleName)).toString().replace('\\', '/'));
    }

    /** {@link #testSource} relative to the repository root, with forward slashes. */
    public static Path relativeTestSource(String module, String simpleName) {
        Path root = repositoryRoot();
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

    /** The nearest ancestor of the working directory whose {@code pom.xml} declares the reactor's modules. */
    public static Path repositoryRoot() {
        Path start = Path.of("").toAbsolutePath().normalize();
        for (Path current = start; current != null; current = current.getParent()) {
            Path pom = current.resolve("pom.xml");
            if (Files.isRegularFile(pom) && declaresModules(pom)) {
                return current;
            }
        }
        throw new IllegalStateException("no ancestor of " + start + " has a pom.xml that declares <modules>");
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

    private static boolean declaresModules(Path pom) {
        try {
            return Files.readString(pom).contains("<modules>");
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
