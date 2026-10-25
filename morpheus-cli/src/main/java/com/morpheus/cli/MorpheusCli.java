package com.morpheus.cli;

import com.morpheus.application.operability.LocalOperationalRuntime;
import com.morpheus.application.product.ProductMetadata;
import com.morpheus.cli.command.CliCommandRun;
import com.morpheus.cli.command.CliExitCode;
import com.morpheus.cli.command.CliFailure;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;

/**
 * The default branch of the CLI dispatch: {@code help}, and {@code version} for an invocation that reaches it
 * directly. Its own entry point serves every command {@link MorpheusMain#run} serves.
 */
public final class MorpheusCli {
    MorpheusCli() {
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8);
        int exit = MorpheusMain.run(LocalOperationalRuntime.process(), args, out, err, System.getenv(),
                System.getProperties());
        if (exit != 0) {
            System.exit(exit);
        }
    }

    int run(
            String[] args,
            PrintStream out,
            PrintStream err,
            Map<String, String> environment,
            Properties properties) {
        return CliCommandRun.run(args, out, err, environment, properties, (tokens, run) -> {
            if (tokens.isEmpty()) {
                printHelp(run);
                return CliExitCode.SUCCESS.code();
            }
            return dispatch(tokens.getFirst(), run);
        });
    }

    private int dispatch(String command, CliCommandRun run) {
        return switch (command) {
            case "help", "--help", "-h" -> {
                printHelp(run);
                yield CliExitCode.SUCCESS.code();
            }
            case "version", "--version" -> {
                run.print(new VersionView(version()), "MORPHEUS " + version());
                yield CliExitCode.SUCCESS.code();
            }
            default -> throw CliFailure.usage("unknown command: " + command);
        };
    }

    private String version() {
        return ProductMetadata.version();
    }

    private void printHelp(CliCommandRun run) {
        run.printText("""
                MORPHEUS — Specification & Intent Intelligence Engine

                Usage:
                  morpheus [--json] [--data-dir PATH] [--config-dir PATH] [--db PATH] <command>

                Commands:
                  help
                  version
                  paths
                  projects list
                  projects add --workspace PATH
                  sync --project ID [--revision REV] [--force]
                  sync-status --project ID [--max-age-minutes N]
                  requirements find --project ID [--query TEXT] [--offset N] [--limit N]
                  changes list --project ID [--offset N] [--limit N]
                  changes get --project ID --change ID
                  constraints list --project ID --change ID [--offset N] [--limit N]
                  decisions list --project ID --change ID [--offset N] [--limit N]
                  tasks list --project ID --change ID [--offset N] [--limit N]
                  trace-requirement --project ID --requirement ID [--depth N]
                  change-context --project ID --change ID [--depth N]
                  analyze-change --project ID --change ID [--depth N]
                  quality --project ID

                Environment overrides:
                  MORPHEUS_DATA_DIR
                  MORPHEUS_CONFIG_DIR
                  MORPHEUS_DB

                Exit codes:
                  0 success
                  2 usage / invalid arguments
                  3 requested project/entity/snapshot not found
                  4 persisted state or synchronization error
                  5 I/O error
                 10 unexpected internal error
                """);
    }

    private record VersionView(String version) {}
}
