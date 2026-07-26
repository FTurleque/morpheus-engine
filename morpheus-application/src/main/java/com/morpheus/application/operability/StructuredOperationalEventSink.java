package com.morpheus.application.operability;

import java.io.PrintStream;
import java.util.Map;
import java.util.Objects;

/** Local JSON-lines sink. It writes only to the caller-provided stream and performs mandatory redaction first. */
public final class StructuredOperationalEventSink implements OperationalEventSink {
    private final PrintStream output;
    private final SensitiveValueRedactor redactor;

    public StructuredOperationalEventSink(PrintStream output) {
        this(output, new SensitiveValueRedactor());
    }

    public StructuredOperationalEventSink(PrintStream output, SensitiveValueRedactor redactor) {
        this.output = Objects.requireNonNull(output, "output");
        this.redactor = Objects.requireNonNull(redactor, "redactor");
    }

    @Override
    public synchronized void emit(OperationalEvent event) {
        Objects.requireNonNull(event, "event");
        Map<String, String> attributes = redactor.redact(event.attributes());
        StringBuilder json = new StringBuilder(256);
        json.append('{')
                .append("\"timestamp\":\"").append(escape(event.occurredAt().toString())).append("\",")
                .append("\"level\":\"").append(event.level().name()).append("\",")
                .append("\"code\":\"").append(event.code().name()).append("\",")
                .append("\"attributes\":{");
        boolean first = true;
        for (Map.Entry<String, String> attribute : attributes.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('\"').append(escape(attribute.getKey())).append("\":\"")
                    .append(escape(attribute.getValue())).append('\"');
        }
        json.append("}}");
        output.println(json);
    }

    private String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append("\\u%04x".formatted((int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
