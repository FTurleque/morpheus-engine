package com.morpheus.provider.openspec;

import com.morpheus.application.files.WorkspaceRelativePathText;
import com.morpheus.application.read.ProviderIngestionLimitException;
import com.morpheus.application.security.ServerLocationDisclosure;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Attributes a failure raised while normalizing one OpenSpec file to that file, named relative to the workspace root.
 *
 * <p>The attribution is made where the file is known, not reconstructed from the failure text afterwards: the
 * text may come from the platform, whose {@link java.nio.file.NoSuchFileException} carries the absolute pathname
 * and nothing else, and a pathname partially scrubbed out of a sentence is still a pathname. A cause whose text
 * names a server location is therefore replaced by its type, never rewritten.</p>
 *
 * <p>Only the three failures a file's content or its reading can cause are attributed, and each keeps its category:
 * an {@link IllegalArgumentException} stays one, as do an {@link IllegalStateException} and an
 * {@link UncheckedIOException} over the same {@link java.io.IOException}, so every surface that maps a failure to a
 * status or an exit code answers as it did without the attribution. Anything else is a defect or a failure of a
 * collaborator such as the identity store, not of the file being read, and passes through unchanged: naming a file
 * there would accuse an innocent one. A budget refusal is not attributed either; it already names its source
 * relatively and its callers discard the whole read on it.</p>
 *
 * <p>The name is a refusal text, not a locator, and the two are written by different rules. A
 * {@link com.morpheus.domain.source.SourceLocator} designates a source stably, so that two platforms reading the same
 * file record the same one: it rewrites a backslash to a slash, and the readers record it in the deltas and
 * evidence they publish. A refusal names the file as the operator will find it, so it substitutes nothing and goes
 * through {@link WorkspaceRelativePathText}. The two agree for every path these readers produce that holds no
 * backslash (they always begin with {@code openspec/} and end with a {@code .md} name, so the locator's trim has
 * nothing to remove). They differ for a name that has a backslash on a platform where it is a legal character:
 * the delta read from {@code openspec/specs/a\b/spec.md} carries the locator
 * {@code file:openspec/specs/a/b/spec.md}, and the refusal names {@code openspec/specs/a\b/spec.md}.
 * That difference is intended and is the reason this class builds no text from a locator (ADR-0028, amendment of
 * 30 September 2026).</p>
 *
 * <p>A backslash also makes {@link ServerLocationDisclosure} take a text for a possible server location, and the
 * surfaces differ in whether they ask. The CLI {@code sync} relays the message as written, so it names the exact file
 * and the cause. The HTTP {@code sync}, local and remote, answers through a filter that replaces the whole message
 * by the failure's type: {@code InvalidOpenSpecSource}, neither file nor cause. The content reader, hence
 * {@code composition sync}, reports the failure by its type alone too
 * ({@code OpenSpec content reader failed for group current: InvalidOpenSpecSource}), and the publication refusal
 * then counts that text as shown, not as withheld, because it no longer holds a backslash. Before, all three named
 * the rewritten path, which does not exist, and kept the cause. The exact path stays in the diagnostic's
 * {@code source}, which no CLI, HTTP or MCP surface serializes today. The amendment of ADR-0028 records why this
 * is kept and names the broader finding, a withheld refusal that becomes a bare type without saying so.</p>
 */
final class OpenSpecSourceAttribution {

    private OpenSpecSourceAttribution() {
    }

    /** A failure that names the workspace-relative file it was raised for. */
    interface AttributedFailure {
        String source();

        /** The simple name of the failure that was attributed, which the attribution itself is not. */
        String failureType();
    }

    static void attribute(Path workspaceRoot, Path file, Runnable work) {
        attribute(workspaceRoot, file, () -> {
            work.run();
            return null;
        });
    }

    static <T> T attribute(Path workspaceRoot, Path file, Supplier<T> work) {
        try {
            return work.get();
        } catch (ProviderIngestionLimitException limit) {
            throw limit;
        } catch (IllegalArgumentException failure) {
            if (failure instanceof AttributedFailure) {
                throw failure;
            }
            String source = source(workspaceRoot, file);
            throw new InvalidOpenSpecSource(source, source + ": " + relayable(failure), failure,
                    failure.getClass().getSimpleName());
        } catch (IllegalStateException failure) {
            if (failure instanceof AttributedFailure) {
                throw failure;
            }
            String source = source(workspaceRoot, file);
            throw new UnreadableOpenSpecSource(source, source + ": " + relayable(failure), failure,
                    failure.getClass().getSimpleName());
        } catch (UncheckedIOException failure) {
            if (failure instanceof AttributedFailure) {
                throw failure;
            }
            String source = source(workspaceRoot, file);
            throw new UncheckedOpenSpecSource(source, source + ": " + relayable(failure), failure.getCause(),
                    failure.getClass().getSimpleName());
        }
    }

    private static String source(Path workspaceRoot, Path file) {
        return WorkspaceRelativePathText.of(workspaceRoot.relativize(file));
    }

    /** The failure's own text when it names no server location, its type otherwise. */
    static String relayable(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank() || !ServerLocationDisclosure.isSafeToRelay(message)) {
            return failure.getClass().getSimpleName();
        }
        return message;
    }

    static String failureType(Throwable failure) {
        return failure instanceof AttributedFailure attributed
                ? attributed.failureType()
                : failure.getClass().getSimpleName();
    }

    static final class InvalidOpenSpecSource extends IllegalArgumentException implements AttributedFailure {
        private final String source;
        private final String failureType;

        private InvalidOpenSpecSource(String source, String message, Throwable cause, String failureType) {
            super(message, cause);
            this.source = source;
            this.failureType = failureType;
        }

        @Override
        public String source() {
            return source;
        }

        @Override
        public String failureType() {
            return failureType;
        }
    }

    static final class UncheckedOpenSpecSource extends UncheckedIOException implements AttributedFailure {
        private final String source;
        private final String failureType;

        private UncheckedOpenSpecSource(String source, String message, IOException cause, String failureType) {
            super(message, cause);
            this.source = source;
            this.failureType = failureType;
        }

        @Override
        public String source() {
            return source;
        }

        @Override
        public String failureType() {
            return failureType;
        }
    }

    static final class UnreadableOpenSpecSource extends IllegalStateException implements AttributedFailure {
        private final String source;
        private final String failureType;

        private UnreadableOpenSpecSource(String source, String message, Throwable cause, String failureType) {
            super(message, cause);
            this.source = source;
            this.failureType = failureType;
        }

        @Override
        public String source() {
            return source;
        }

        @Override
        public String failureType() {
            return failureType;
        }
    }
}
