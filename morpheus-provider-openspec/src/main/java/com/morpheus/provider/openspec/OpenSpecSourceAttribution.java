package com.morpheus.provider.openspec;

import com.morpheus.application.read.ProviderIngestionLimitException;
import com.morpheus.application.security.ServerLocationDisclosure;
import com.morpheus.domain.source.SourceLocator;

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
 * <p>The attributed failure keeps the category of its cause: invalid content stays an
 * {@link IllegalArgumentException}, anything else becomes an {@link IllegalStateException}. A budget refusal is not
 * attributed; it already names its source relatively and its callers discard the whole read on it.</p>
 */
final class OpenSpecSourceAttribution {

    private OpenSpecSourceAttribution() {
    }

    /** A failure that names the workspace-relative file it was raised for. */
    interface AttributedFailure {
        String source();
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
        } catch (RuntimeException failure) {
            if (failure instanceof AttributedFailure) {
                throw failure;
            }
            String source = SourceLocator.file(workspaceRoot.relativize(file).toString()).value();
            String message = source + ": " + relayable(failure);
            if (failure instanceof IllegalArgumentException) {
                throw new InvalidOpenSpecSource(source, message, failure);
            }
            throw new UnreadableOpenSpecSource(source, message, failure);
        }
    }

    /** The failure's own text when it names no server location, its type otherwise. */
    static String relayable(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank() || !ServerLocationDisclosure.isSafeToRelay(message)) {
            return failure.getClass().getSimpleName();
        }
        return message;
    }

    /** The type reported for a failure: the attributed cause's, since the attribution is not itself a cause. */
    static String failureType(Throwable failure) {
        Throwable reported = failure instanceof AttributedFailure && failure.getCause() != null
                ? failure.getCause()
                : failure;
        return reported.getClass().getSimpleName();
    }

    static final class InvalidOpenSpecSource extends IllegalArgumentException implements AttributedFailure {
        private final String source;

        private InvalidOpenSpecSource(String source, String message, Throwable cause) {
            super(message, cause);
            this.source = source;
        }

        @Override
        public String source() {
            return source;
        }
    }

    static final class UnreadableOpenSpecSource extends IllegalStateException implements AttributedFailure {
        private final String source;

        private UnreadableOpenSpecSource(String source, String message, Throwable cause) {
            super(message, cause);
            this.source = source;
        }

        @Override
        public String source() {
            return source;
        }
    }
}
