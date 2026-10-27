package com.morpheus.cli.command;

import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.store.KnowledgeStoreException;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;

/**
 * The frame of the commands that answer {@code MORPHEUS error [code]: message}: the global options and the layout are
 * read, the command runs, and a refusal becomes its exit code and its error line, in text or in JSON.
 *
 * <p>A {@link CliFailure} answers its own code; an {@link IllegalArgumentException} is a usage error; a
 * {@link KnowledgeStoreException} or an {@link IllegalStateException} a state error; any other runtime failure an
 * internal error. The line names the failure's message, or its type when it has none.</p>
 */
public final class CliCommandRun {
    private static final CanonicalJsonSerializer JSON = new CanonicalJsonSerializer();
    private static final String KEY_SNAPSHOT_ID = "snapshotId=";

    private final boolean json;
    private final CliLayout layout;
    private final PrintStream out;

    private CliCommandRun(boolean json, CliLayout layout, PrintStream out) {
        this.json = json;
        this.layout = layout;
        this.out = out;
    }

    /** What a command does once its words and the layout are known. */
    @FunctionalInterface
    public interface Command {
        int run(List<String> tokens, CliCommandRun run);
    }

    public static int run(
            String[] args,
            PrintStream out,
            PrintStream err,
            Map<String, String> environment,
            Properties properties,
            Command command) {
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
        boolean jsonOutput = false;
        try {
            GlobalArgs.Parsed global = GlobalArgs.parse(args);
            jsonOutput = global.json();
            CliLayout layout = CliLayout.resolve(
                    global.dataDirectory(), global.configDirectory(), global.databasePath(), environment, properties);
            return command.run(global.remaining(), new CliCommandRun(jsonOutput, layout, out));
        } catch (CliFailure failure) {
            CliErrorLine.write(err, jsonOutput, failure.exitCode(), failure.getMessage());
            return failure.exitCode().code();
        } catch (IllegalArgumentException failure) {
            CliErrorLine.write(err, jsonOutput, CliExitCode.USAGE, CliErrorLine.failureText(failure));
            return CliExitCode.USAGE.code();
        } catch (KnowledgeStoreException | IllegalStateException failure) {
            CliErrorLine.write(err, jsonOutput, CliExitCode.STATE_ERROR, CliErrorLine.failureText(failure));
            return CliExitCode.STATE_ERROR.code();
        } catch (RuntimeException failure) {
            CliErrorLine.write(err, jsonOutput, CliExitCode.INTERNAL_ERROR, CliErrorLine.failureText(failure));
            return CliExitCode.INTERNAL_ERROR.code();
        }
    }

    /** Answers an invocation whose command path is not declared, the way every unknown command is answered. */
    public static int refuseUndeclared(String[] args, PrintStream err, String message) {
        CliErrorLine.write(err, Arrays.asList(args).contains("--json"), CliExitCode.USAGE, message);
        return CliExitCode.USAGE.code();
    }

    public boolean json() {
        return json;
    }

    CliLayout layout() {
        return layout;
    }

    public Path databasePath() {
        return layout.databasePath();
    }

    /** {@code value} in canonical JSON, or {@code human} as text. */
    public void print(Object value, String human) {
        out.println(json ? JSON.toJson(value) : human);
    }

    public void printJson(Object value) {
        out.println(JSON.toJson(value));
    }

    public void printLine(String line) {
        out.println(line);
    }

    /** {@code text} as it is, with no line separator added. */
    public void printText(String text) {
        out.print(text);
    }

    /** A page in canonical JSON, or its snapshot line and one line per item as text. */
    public <T> void printPage(String snapshotId, int totalMatches, boolean hasMore, List<T> items, Object page,
                              Function<T, String> human) {
        if (json) {
            out.println(JSON.toJson(page));
            return;
        }
        out.println(KEY_SNAPSHOT_ID + snapshotId + " total=" + totalMatches + " hasMore=" + hasMore);
        items.forEach(item -> out.println(human.apply(item)));
    }
}
