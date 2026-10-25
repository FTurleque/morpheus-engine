package com.morpheus.cli.command;

import java.util.Objects;

/** A refusal a command decides itself, with the exit code it answers. */
public final class CliFailure extends RuntimeException {
    private final CliExitCode exitCode;

    private CliFailure(CliExitCode exitCode, String message) {
        super(message);
        this.exitCode = Objects.requireNonNull(exitCode, "exitCode");
    }

    public static CliFailure usage(String message) {
        return new CliFailure(CliExitCode.USAGE, message);
    }

    public static CliFailure notFound(String message) {
        return new CliFailure(CliExitCode.NOT_FOUND, message);
    }

    public static CliFailure state(String message) {
        return new CliFailure(CliExitCode.STATE_ERROR, message);
    }

    public CliExitCode exitCode() {
        return exitCode;
    }
}
