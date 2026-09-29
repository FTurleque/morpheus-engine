package com.morpheus.architecture.m21;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The one reader of {@code contracts/public-surfaces.tsv} for the architecture tests.
 *
 * <p>It reads by lines, never by {@code '\n'}: the file is CRLF in a Windows working tree and LF in the index, and a
 * reader that split on the newline character alone would carry a {@code '\r'} into the last column. Comment lines
 * (starting with {@code #}) and blank lines are skipped; every other line is returned split on tabs with trailing empty
 * columns kept, so a row with a missing column is visible to the caller instead of being silently shortened. It asserts
 * nothing about the rows: what a row must contain is each caller's rule.</p>
 */
public final class PublicSurfaceManifest {
    public static final String MANIFEST = "contracts/public-surfaces.tsv";
    public static final int CAPABILITY = 0;
    public static final int INTENT = 1;
    public static final int CLI = 2;
    public static final int MCP = 3;
    public static final int HTTP = 4;
    public static final int NOTES = 5;

    /**
     * The sentinels the manifest may carry in place of a surface, as written in {@code .claude/rules/governance.md}.
     * A cell that starts with {@code EXPLICITLY_} and is not one of these is an invented vocabulary, not a declared absence.
     */
    public static final java.util.Set<String> SENTINELS = java.util.Set.of(
            "EXPLICITLY_NOT_EXPOSED",
            "EXPLICITLY_LOCAL_ONLY",
            "EXPLICITLY_REMOTE_ONLY",
            "EXPLICITLY_OFFLINE_ONLY");
    public static final String SENTINEL_PREFIX = "EXPLICITLY_";

    private PublicSurfaceManifest() {
    }

    public static List<String[]> rows(Path root) throws IOException {
        return rows(Files.readAllLines(root.resolve(MANIFEST)));
    }

    public static List<String[]> rows(List<String> lines) {
        List<String[]> rows = new ArrayList<>();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            rows.add(line.split("\t", -1));
        }
        return List.copyOf(rows);
    }
}
