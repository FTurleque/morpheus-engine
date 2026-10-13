package com.morpheus.cli;

import com.morpheus.application.operability.LocalOperationalRuntime;
import com.morpheus.application.operability.OperationalEvent;
import com.morpheus.application.operability.OperationalEventCode;
import com.morpheus.application.operability.OperationalEventLevel;
import com.morpheus.application.operability.OperationalEventSink;
import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.helpers.LegacyAbstractLogger;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.helpers.NOPMDCAdapter;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The one logging binding MORPHEUS ships: what the libraries it embeds log at warning level and above goes to the
 * operational sink, and nowhere when no operational log is written.
 *
 * <p>The product shipped {@code slf4j-nop}: a handshake with an MCP peer that timed out, a stream that broke, a frame
 * that did not parse - whatever the MCP SDK and its dependencies reported was thrown away, whether or not the operator
 * had asked for logs. This provider replaces it. With the operational log off it does exactly what {@code slf4j-nop}
 * did; with it on, each record becomes a {@code THIRD_PARTY_LOG} event through the same sink and the same redaction as
 * the product's own events. Nothing is ever written to standard output, which is the protocol stream of the MCP
 * server.</p>
 */
public final class OperationalSlf4jServiceProvider implements SLF4JServiceProvider {
    static final int MAX_MESSAGE_CHARACTERS = 2_048;

    private final IMarkerFactory markers = new BasicMarkerFactory();
    private final MDCAdapter diagnosticContext = new NOPMDCAdapter();
    private ILoggerFactory loggers;

    @Override
    public ILoggerFactory getLoggerFactory() {
        return loggers;
    }

    @Override
    public IMarkerFactory getMarkerFactory() {
        return markers;
    }

    @Override
    public MDCAdapter getMDCAdapter() {
        return diagnosticContext;
    }

    @Override
    public String getRequestedApiVersion() {
        return "2.0.99";
    }

    @Override
    public void initialize() {
        boolean written = LocalOperationalRuntime.writesEvents();
        OperationalEventSink sink = LocalOperationalRuntime.sink();
        loggers = name -> new SinkLogger(name, sink, written);
    }

    /** A logger that forwards warnings and errors to a sink, or accepts nothing when no event is written. */
    static final class SinkLogger extends LegacyAbstractLogger {
        private static final long serialVersionUID = 1L;

        private final transient OperationalEventSink sink;
        private final boolean written;

        SinkLogger(String name, OperationalEventSink sink, boolean written) {
            this.name = Objects.requireNonNull(name, "name");
            this.sink = Objects.requireNonNull(sink, "sink");
            this.written = written;
        }

        @Override
        public boolean isTraceEnabled() {
            return false;
        }

        @Override
        public boolean isDebugEnabled() {
            return false;
        }

        @Override
        public boolean isInfoEnabled() {
            return false;
        }

        @Override
        public boolean isWarnEnabled() {
            return written;
        }

        @Override
        public boolean isErrorEnabled() {
            return written;
        }

        @Override
        protected String getFullyQualifiedCallerName() {
            return null;
        }

        @Override
        protected void handleNormalizedLoggingCall(
                Level level, Marker marker, String messagePattern, Object[] arguments, Throwable throwable) {
            Map<String, String> attributes = new LinkedHashMap<>();
            attributes.put("logger", name);
            attributes.put("message", bounded(MessageFormatter.basicArrayFormat(messagePattern, arguments)));
            if (throwable != null) {
                attributes.put("failureType", throwable.getClass().getSimpleName());
            }
            sink.emit(new OperationalEvent(
                    Instant.now(),
                    level == Level.ERROR ? OperationalEventLevel.ERROR : OperationalEventLevel.WARNING,
                    OperationalEventCode.THIRD_PARTY_LOG,
                    attributes));
        }

        private static String bounded(String message) {
            String text = message == null ? "" : message;
            return text.length() <= MAX_MESSAGE_CHARACTERS
                    ? text
                    : text.substring(0, MAX_MESSAGE_CHARACTERS) + "...";
        }
    }
}
