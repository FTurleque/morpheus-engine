package com.morpheus.cli.command;

import com.morpheus.application.query.compact.CanonicalJsonSerializer;

import java.io.PrintStream;
import java.util.Optional;

/**
 * The error line of the CLI, {@code MORPHEUS error [<code>]: <message>}, or its JSON form under {@code --json}, and the
 * text a failure contributes to it: its message, or the simple name of its type when the message is absent or blank.
 *
 * <p>The line is what a caller's script reads, so every sub-command writes it here. The commands that answer with a
 * prefix of their own ({@code MORPHEUS server usage error: ...}) take the failure's text from here too.</p>
 */
public final class CliErrorLine {
    private static final CanonicalJsonSerializer JSON = new CanonicalJsonSerializer();

    private CliErrorLine() {
    }

    public static String failureText(Throwable failure) {
        return Optional.ofNullable(failure.getMessage()).filter(message -> !message.isBlank())
                .orElse(failure.getClass().getSimpleName());
    }

    /** Writes the line of {@code failure} in text and returns the exit code it answers. */
    public static int report(PrintStream err, CliExitCode code, Throwable failure) {
        write(err, false, code, failureText(failure));
        return code.code();
    }

    public static void write(PrintStream err, boolean json, CliExitCode code, String message) {
        if (json) {
            err.println(JSON.toJson(new ErrorView(code.code(), code.name(), message)));
        } else {
            err.println("MORPHEUS error [" + code.code() + "]: " + message);
        }
    }

    private record ErrorView(int exitCode, String code, String message) {}
}
