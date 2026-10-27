package com.morpheus.api;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The repository the tests of this module run in, and the shared M0 fixtures it holds.
 *
 * <p>The root is the nearest ancestor of the working directory whose {@code pom.xml} declares the reactor's
 * {@code <modules>}. That file defines the root instead of recognising it by a neighbour: a module POM names the
 * reactor as its parent but declares no modules, and moving the convergence manifest, the distribution directory or
 * the ADR registry does not move it. Surefire starts from the module directory and an IDE may start from the root or
 * below it; a Git worktree nested in another checkout is found before the checkout that holds it.</p>
 *
 * <p>Each test module that reads files of the repository has one copy of this class, and no other locator.
 * {@code RepositoryRootOwnershipTest} in {@code morpheus-architecture-tests} keeps the copies identical and
 * refuses a private climb from the working directory.</p>
 */
public final class RepositoryRoot {

    /** What marks the root, as the failure names it. */
    static final String ANCHOR = "a pom.xml that declares <modules>";

    private RepositoryRoot() {
    }

    /** The directory of the shared M0 fixture {@code name}, under {@code experiments/m0/fixtures}. */
    public static Path fixture(String name) {
        Path fixture = locate().resolve("experiments/m0/fixtures").resolve(name);
        if (!Files.isDirectory(fixture)) {
            throw new IllegalStateException("no M0 fixture " + name + " at " + fixture);
        }
        return fixture;
    }

    public static Path locate() {
        return from(Path.of("").toAbsolutePath().normalize());
    }

    static Path from(Path start) {
        for (Path current = start; current != null; current = current.getParent()) {
            Path pom = current.resolve("pom.xml");
            if (Files.isRegularFile(pom) && declaresModules(pom)) {
                return current;
            }
        }
        throw new IllegalStateException("no ancestor of " + start + " has " + ANCHOR);
    }

    private static boolean declaresModules(Path pom) {
        try {
            return Files.readString(pom).contains("<modules>");
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
