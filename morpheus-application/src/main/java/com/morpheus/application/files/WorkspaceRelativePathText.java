package com.morpheus.application.files;

import java.nio.file.Path;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * How a failure names a workspace-relative path: its name components joined with {@code /}, on every platform.
 *
 * <p>The platform separator would make the same refusal read differently on Windows, where boundary filters reject
 * any backslash as a possible server location, so the cause would be withheld there and relayed elsewhere. The text is
 * built from the components rather than by substituting characters, because on Linux a backslash is a legal character
 * of a file name: {@code docs\proof.md} is one name there, and rewriting it would name another file.</p>
 *
 * <p>This is the text of a refusal, which names a file as the operator will find it. It is not a locator: a
 * {@code SourceLocator} designates a source stably across platforms and therefore does rewrite a backslash, so its
 * value and this text differ for a name that contains one on Linux. A refusal that needs the text of a path must not
 * be built from a locator, and a locator must not be built from this text.</p>
 *
 * <p>A path with a root is not relative and is left as the platform wrote it; rewriting it would present an absolute
 * path as a relative one.</p>
 */
public final class WorkspaceRelativePathText {

    private WorkspaceRelativePathText() {
    }

    public static String of(Path relativePath) {
        Objects.requireNonNull(relativePath, "relativePath");
        if (relativePath.getRoot() != null) {
            return relativePath.toString();
        }
        StringJoiner text = new StringJoiner("/");
        for (Path name : relativePath) {
            text.add(name.toString());
        }
        return text.toString();
    }
}
