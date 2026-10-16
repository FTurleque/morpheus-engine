package com.morpheus.application.operability;

/**
 * The kinds of failure the product deliberately does not propagate, each under the name its counter and its events
 * carry.
 *
 * <p>A failure that is set aside so that something else can go on - an optional adapter that does not load, an answer
 * that becomes a generic error - used to leave nothing behind: no counter, no event, and after the process ended, no
 * trace that anything had failed. The list is closed so that a counter's name is decided here and not at each catch.
 * A non-zero counter means the product went on without something; {@code docs/developer/OPERABILITY.md} says what, per
 * family.</p>
 */
public enum SuppressedFailureFamily {
    /** An optional reasoning adapter present on the class path could not be loaded or described. */
    ADAPTER_DISCOVERY("adapter-discovery", OperationalEventLevel.WARNING),
    /** An HTTP request ended in a failure nothing had foreseen and was answered with a generic internal error. */
    HTTP_INTERNAL_ERROR("http-internal-error", OperationalEventLevel.ERROR),
    /**
     * While recovering from a failed sync, the state that would say whether the commit or its marker is already
     * visible could not be read, and was taken as "not visible".
     */
    SYNC_RECOVERY_READ("sync-recovery-read", OperationalEventLevel.WARNING),
    /**
     * While answering a lifecycle conflict, the audit record or the current state could not be read: the conflict is
     * reported without telling an already applied mutation from a real collision, or without its current state.
     */
    SQLITE_CONFLICT_READ("sqlite-conflict-read", OperationalEventLevel.WARNING),
    /**
     * A change under a watched root could not be turned into a signal - a directory that could not be registered, a
     * path outside the workspace - and was reported as an overflow, which only asks for a rescan.
     */
    SOURCE_WATCH("source-watch", OperationalEventLevel.WARNING),
    /**
     * An MCP peer failed - it did not start, missed its deadline, answered something unusable - and the caller was
     * given a bare "unavailable", the same answer as a peer that has no active snapshot.
     */
    MCP_PEER("mcp-peer", OperationalEventLevel.WARNING),
    /** An MCP peer could not be closed or aborted cleanly; its process may outlive the operation that started it. */
    MCP_PEER_SHUTDOWN("mcp-peer-shutdown", OperationalEventLevel.WARNING),
    /**
     * The descendants of a live MCP peer process could not be listed on one observation. A process the peer started
     * meanwhile may never be retained, and so may not be terminated with the peer. Or a live process of the peer, its
     * root or a retained descendant, refused its termination signal and may outlive it.
     */
    MCP_PEER_PROCESS_TREE("mcp-peer-process-tree", OperationalEventLevel.WARNING),
    /**
     * Something MORPHEUS staged in the temporary directory could not be deleted after use and remains on disk: the
     * verified copy of a pinned JAR, or the private directory a plugin probe returns its result through.
     */
    STAGED_ARTIFACT_CLEANUP("staged-artifact-cleanup", OperationalEventLevel.WARNING),
    /**
     * The descendants of a process of a plugin probe could not be listed on one observation. A process the plugin
     * started meanwhile may never be retained, and so may not be terminated when the probe ends.
     */
    PLUGIN_PROBE_PROCESS_TREE("plugin-probe-process-tree", OperationalEventLevel.WARNING),
    /**
     * A live process of a plugin probe - the worker or a descendant observed under it - could not be signalled to
     * terminate. It may outlive the probe that started it.
     */
    PLUGIN_PROBE_SHUTDOWN("plugin-probe-shutdown", OperationalEventLevel.WARNING),

    /**
     * The lock or the channel of a database lease could not be released or closed. The lease is reported as returned
     * while the operating system may still hold the lock: the next open of that database can be refused as busy.
     */
    SQLITE_LEASE_RELEASE("sqlite-lease-release", OperationalEventLevel.WARNING);

    private final String key;
    private final OperationalEventLevel level;

    SuppressedFailureFamily(String key, OperationalEventLevel level) {
        this.key = key;
        this.level = level;
    }

    public String key() {
        return key;
    }

    /** The counter that moves each time a failure of this family is set aside. */
    public String counter() {
        return "failure.suppressed." + key;
    }

    OperationalEventLevel level() {
        return level;
    }
}
