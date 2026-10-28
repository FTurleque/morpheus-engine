package com.morpheus.store.sqlite.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Properties;

/**
 * The databases that published programs wrote, one per released schema level, kept under
 * {@code published-databases/level-<n>/} with the {@code provenance.properties} that names the tag which wrote them.
 *
 * <p>The directory's {@code README.md} says how each one was produced. A released level without its database is
 * refused by name: the release that ships a new level produces its database with its own CLI before declaring it.</p>
 */
final class PublishedDatabases {
    static final String DIRECTORY = "/published-databases/";

    private PublishedDatabases() {
    }

    record Provenance(String version, String tag, String commit, String sha256) {
    }

    static Provenance provenance(int level) {
        Properties declared = new Properties();
        String resource = DIRECTORY + "level-" + level + "/provenance.properties";
        try (InputStream stream = PublishedDatabases.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("released schema level " + level + " has no database written by a "
                        + "published program: produce " + DIRECTORY + "level-" + level + "/ with the CLI of the tag "
                        + "that shipped it, as published-databases/README.md describes, before declaring the level");
            }
            declared.load(stream);
        } catch (IOException failure) {
            throw new IllegalStateException("cannot read test resource " + resource, failure);
        }
        return new Provenance(required(declared, "version", resource), required(declared, "tag", resource),
                required(declared, "commit", resource), required(declared, "sha256", resource));
    }

    /** Copies the database of {@code level} into {@code directory}, refusing one whose bytes are not those recorded. */
    static Path copy(int level, Path directory) throws IOException {
        Provenance provenance = provenance(level);
        String resource = DIRECTORY + "level-" + level + "/morpheus.db";
        byte[] bytes;
        try (InputStream stream = PublishedDatabases.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("missing test resource " + resource);
            }
            bytes = stream.readAllBytes();
        }
        String actual = sha256(bytes);
        if (!actual.equals(provenance.sha256())) {
            throw new IllegalStateException(resource + " is not the database " + provenance.tag()
                    + " wrote: sha256 " + actual + ", recorded " + provenance.sha256());
        }
        return Files.write(Files.createDirectories(directory).resolve("morpheus.db"), bytes);
    }

    private static String required(Properties declared, String key, String resource) {
        String value = declared.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(resource + " does not declare " + key);
        }
        return value.trim();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
