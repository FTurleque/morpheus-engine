package com.morpheus.cli;

/** Stable process exit codes for scriptable MORPHEUS CLI consumers. */
public enum CliExitCode {
    SUCCESS(0),
    USAGE(2),
    NOT_FOUND(3),
    STATE_ERROR(4),
    IO_ERROR(5),
    INTERNAL_ERROR(10);

    private final int code;

    CliExitCode(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }
}