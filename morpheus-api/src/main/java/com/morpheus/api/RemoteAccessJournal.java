package com.morpheus.api;

import com.morpheus.application.operability.OperationalEvent;
import com.morpheus.application.operability.OperationalEventCode;
import com.morpheus.application.operability.OperationalEventLevel;
import com.morpheus.application.operability.OperationalMetrics;
import com.morpheus.application.operability.RotatingOperationalLogFile;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Who was authorized to do what on the remote server.
 *
 * <p>The server authenticated a request into a local variable and read only its role: after a write or an
 * administration token was misused, nothing said which principal had acted, or when. The audit kept beside the
 * identity file records what happens to credentials, never what they are used for. This journal is the other record.</p>
 *
 * <p>A record holds the time, the request identifier the response carries, the principal, the role it holds and the
 * role the route requires, the method, the route <em>template</em> and the status. It never holds a token, a
 * fingerprint of one, a path, a query, a header or a body: the template comes from the route table and the method is
 * one the table accepted, so nothing in a record is text the caller chose. A request that fails authentication has no
 * principal and is not recorded; a journal an unauthenticated caller could fill is one it could rotate.</p>
 *
 * <p>A write or an administration request is recorded before it is served, then again with its outcome: a record
 * written only afterwards would be missing for exactly the request that brought the server down. If that first record
 * cannot be written the request is refused. A read is served either way. Every record that cannot be written is
 * counted ({@link #LOST}).</p>
 */
final class RemoteAccessJournal {
    static final String FILE_NAME = "morpheus-remote-access.jsonl";
    static final String REFUSAL = "ACCESS_JOURNAL_UNAVAILABLE";
    static final String LOST = "remote.access.journal.lost";
    static final long MAX_FILE_BYTES = 4L * 1024 * 1024;
    static final int KEPT_GENERATIONS = 4;

    private final RotatingOperationalLogFile file;
    private final OperationalMetrics metrics;

    private RemoteAccessJournal(RotatingOperationalLogFile file, OperationalMetrics metrics) {
        this.file = file;
        this.metrics = metrics;
    }

    /**
     * Opens the journal in the directory of the identity file, as a file of its own.
     *
     * @throws IllegalStateException starting with {@link #REFUSAL} when it cannot be written there: a remote server
     *         does not start without the record of what it will be asked to do
     */
    static RemoteAccessJournal beside(Path identityFile, OperationalMetrics metrics) {
        return open(identityFile.toAbsolutePath().normalize().resolveSibling(FILE_NAME),
                MAX_FILE_BYTES, KEPT_GENERATIONS, metrics);
    }

    static RemoteAccessJournal open(Path file, long maxFileBytes, int keptGenerations, OperationalMetrics metrics) {
        Objects.requireNonNull(metrics, "metrics");
        try {
            return new RemoteAccessJournal(
                    RotatingOperationalLogFile.openFile(file, maxFileBytes, keptGenerations, metrics), metrics);
        } catch (IOException unusable) {
            throw new IllegalStateException(REFUSAL + ": the remote access journal cannot be written beside the "
                    + "identity file (" + unusable.getClass().getSimpleName() + "); the directory must exist and be "
                    + "writable by this account alone", unusable);
        }
    }

    /**
     * Records a request about to be served. A read is not recorded here - its outcome is enough - and is always
     * accepted.
     *
     * @return false when the request changes or administers something and its record could not be written
     */
    boolean accepted(Request request) {
        if (request.required() == MorpheusRemoteRole.READ) {
            return true;
        }
        return write(request, "accepted", OperationalEventLevel.INFO, Map.of());
    }

    /** Records how a request ended. A status the server never sent is recorded as such. */
    void completed(Request request, int status) {
        write(request,
                "completed",
                status == 403 ? OperationalEventLevel.WARNING : OperationalEventLevel.INFO,
                Map.of("status", status < 0 ? "unsent" : Integer.toString(status)));
    }

    private boolean write(Request request, String phase, OperationalEventLevel level, Map<String, String> more) {
        Map<String, String> record = new LinkedHashMap<>(more);
        record.put("phase", phase);
        record.put("requestId", request.requestId());
        record.put("principal", request.principal());
        record.put("roleHeld", request.held().name());
        record.put("roleRequired", request.required().name());
        record.put("method", request.method());
        record.put("route", request.template());
        try {
            file.write(new OperationalEvent(Instant.now(), level, OperationalEventCode.REMOTE_ACCESS, record));
            return true;
        } catch (IOException lost) {
            metrics.increment(LOST);
            return false;
        }
    }

    /** A request once it has a principal and a route: everything a record says about it but its outcome. */
    record Request(
            String requestId,
            String principal,
            MorpheusRemoteRole held,
            MorpheusRemoteRole required,
            String method,
            String template) {
        Request {
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(principal, "principal");
            Objects.requireNonNull(held, "held");
            Objects.requireNonNull(required, "required");
            method = Objects.requireNonNull(method, "method").toUpperCase(Locale.ROOT);
            Objects.requireNonNull(template, "template");
        }
    }
}
