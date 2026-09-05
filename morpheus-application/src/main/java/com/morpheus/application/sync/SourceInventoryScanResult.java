package com.morpheus.application.sync;

import com.morpheus.application.security.ServerLocationDisclosure;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A scan is usable only when it is complete; partial observations never become a synchronization baseline. */
public record SourceInventoryScanResult(
        ProjectSpecificationId projectId,
        Optional<SourceInventory> inventory,
        List<Failure> failures) {

    public SourceInventoryScanResult {
        Objects.requireNonNull(projectId, "projectId");
        inventory = Objects.requireNonNull(inventory, "inventory");
        failures = Objects.requireNonNull(failures, "failures").stream()
                .peek(failure -> Objects.requireNonNull(failure, "failures item"))
                .sorted()
                .toList();
        if (inventory.isPresent() != failures.isEmpty()) {
            throw new IllegalArgumentException("complete scan requires inventory and no failures; incomplete scan requires failures only");
        }
        inventory.ifPresent(value -> {
            if (!value.projectId().equals(projectId)) {
                throw new IllegalArgumentException("scan inventory belongs to another project");
            }
        });
    }

    public static SourceInventoryScanResult complete(SourceInventory inventory) {
        Objects.requireNonNull(inventory, "inventory");
        return new SourceInventoryScanResult(inventory.projectId(), Optional.of(inventory), List.of());
    }

    public static SourceInventoryScanResult incomplete(ProjectSpecificationId projectId, List<Failure> failures) {
        return new SourceInventoryScanResult(projectId, Optional.empty(), failures);
    }

    public boolean complete() {
        return inventory.isPresent();
    }

    /** Every failure as it may leave the machine, in the same deterministic order. */
    public List<PublicView> publicFailures() {
        return failures.stream().map(Failure::publicView).toList();
    }

    /**
     * Why a scan could not complete, named by a stable code and described in free text.
     *
     * <p>The code is MORPHEUS's own and is safe to relay anywhere. {@code source} and {@code message} are not:
     * a source outside the workspace renders as an absolute pathname, and the platform writes the pathname into
     * the message of a filesystem failure -- {@link java.nio.file.AccessDeniedException} reports nothing else.
     * A caller answering somebody outside the machine projects through {@link #publicView()}; the full text
     * stays for the operator running MORPHEUS locally, who owns those paths already.</p>
     */
    public record Failure(Optional<String> source, Code code, String message) implements Comparable<Failure> {
        public Failure {
            source = Objects.requireNonNull(source, "source").map(String::trim);
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
            message = message.trim();
            if (message.isEmpty()) {
                throw new IllegalArgumentException("failure message must not be blank");
            }
        }

        /** What the scan could not do. Stable across releases: callers branch on it, and it names no location. */
        public enum Code {
            SOURCE_ROOT_MISSING,
            SYMLINK_NOT_FOLLOWED,
            WORKSPACE_BOUNDARY_ESCAPED,
            SCAN_LIMIT_EXCEEDED,
            SOURCE_CHANGED_DURING_SCAN,
            SOURCE_OBSERVED_TWICE,
            SOURCE_UNREADABLE
        }

        /**
         * The projection for a caller outside the machine: the code always, and the other two fields only when
         * they name no filesystem location. Nothing is stripped and relayed -- a partially scrubbed pathname is
         * still a pathname -- so a value that locates anything is dropped rather than rewritten.
         */
        public PublicView publicView() {
            return new PublicView(
                    source.filter(ServerLocationDisclosure::isSafeToRelay),
                    code,
                    ServerLocationDisclosure.isSafeToRelay(message)
                            ? Optional.of(message)
                            : Optional.empty());
        }

        @Override
        public int compareTo(Failure other) {
            int sourceOrder = source.orElse("").compareTo(other.source.orElse(""));
            if (sourceOrder != 0) {
                return sourceOrder;
            }
            int codeOrder = code.compareTo(other.code);
            return codeOrder != 0 ? codeOrder : message.compareTo(other.message);
        }
    }

    /** A scan failure as it may leave the machine. Rendering is deterministic and names no server location. */
    public record PublicView(Optional<String> source, Failure.Code code, Optional<String> detail) {
        public PublicView {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(detail, "detail");
        }

        @Override
        public String toString() {
            StringBuilder rendered = new StringBuilder(code.name());
            source.ifPresent(value -> rendered.append(" at ").append(value));
            detail.ifPresent(value -> rendered.append(": ").append(value));
            return rendered.toString();
        }
    }
}
