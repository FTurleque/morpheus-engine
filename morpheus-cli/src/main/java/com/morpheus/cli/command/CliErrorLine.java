package com.morpheus.cli.command;

import com.morpheus.application.foundation.FailureText;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;

import java.io.PrintStream;
import java.util.Arrays;

/**
 * The error line of the CLI, {@code MORPHEUS error [<code>]: <message>}, or its JSON form under {@code --json}, and the
 * text a failure contributes to it, formed by {@code FailureText.of}.
 *
 * <p>The line is what a caller's script reads, so every command and every server launcher writes it here: the base
 * frame of {@link CliCommandRun}, and the families that keep their own mapping of a failure to an exit code through
 * {@link #refuse}. Only the exit code differs between them, never the line.</p>
 */
public final class CliErrorLine {
    private static final CanonicalJsonSerializer JSON = new CanonicalJsonSerializer();

    private CliErrorLine() {
    }

    /**
     * Writes the line of a refused invocation, as a JSON object when {@code args} carries {@code --json}, and returns
     * the exit code. The arguments are read raw because a refusal often comes before they are parsed.
     */
    public static int refuse(String[] args, PrintStream err, CliExitCode code, String message) {
        write(err, Arrays.asList(args).contains("--json"), code, message);
        return code.code();
    }

    /** {@link #refuse(String[], PrintStream, CliExitCode, String)} with the text of {@code failure}. */
    public static int refuse(String[] args, PrintStream err, CliExitCode code, Throwable failure) {
        return refuse(args, err, code, FailureText.of(failure));
    }

    /** {@link #refuse(String[], PrintStream, CliExitCode, String)} with the text of {@code failure} after {@code context}. */
    public static int refuse(String[] args, PrintStream err, CliExitCode code, String context, Throwable failure) {
        return refuse(args, err, code, context + FailureText.of(failure));
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
