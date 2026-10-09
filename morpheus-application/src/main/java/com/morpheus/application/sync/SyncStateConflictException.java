package com.morpheus.application.sync;

/**
 * A synchronization-state write was refused because the state moved since the writer read it.
 *
 * <p>Synchronization state is an authority: the pending full-rebuild flag it carries is what forces the next
 * sync off the incremental path. A stale writer that could overwrite it would silently clear that flag, which is
 * why the write carries the revision it read and a mismatch is this named failure, not a storage error.</p>
 */
public final class SyncStateConflictException extends RuntimeException {
    private final long expectedRevision;
    private final long currentRevision;

    public SyncStateConflictException(String projectId, long expectedRevision, long currentRevision) {
        super("stale synchronization state revision for " + projectId + ": expected " + expectedRevision
                + " but current is " + currentRevision);
        this.expectedRevision = expectedRevision;
        this.currentRevision = currentRevision;
    }

    public long expectedRevision() {
        return expectedRevision;
    }

    public long currentRevision() {
        return currentRevision;
    }
}
