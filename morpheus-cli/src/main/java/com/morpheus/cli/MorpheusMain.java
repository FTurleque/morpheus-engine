package com.morpheus.cli;

import com.morpheus.api.MorpheusHttpServer;
import com.morpheus.mcp.MorpheusMcpServer;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Official native MORPHEUS launcher for CLI, MCP STDIO and the M11 headless HTTP API. */
public final class MorpheusMain {
    private MorpheusMain() {
    }

    public static void main(String[] args) {
        int exitCode;
        if (ApiLaunchOptions.isApiCommand(args)) {
            exitCode = runApi(args, System.err, System.getenv(), System.getProperties());
        } else if (McpLaunchOptions.isMcpCommand(args)) {
            exitCode = runMcp(args, System.err, System.getenv(), System.getProperties());
        } else {
            exitCode = run(args, System.out, System.err, System.getenv(), System.getProperties());
        }
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static int run(
            String[] args,
            PrintStream out,
            PrintStream err,
            Map<String, String> environment,
            Properties properties) {
        int exitCode = new MorpheusCli().run(normalizeForExecution(args), out, err, environment, properties);
        if (exitCode == CliExitCode.SUCCESS.code() && isHelpRequest(args)) {
            out.println();
            out.println("MCP:");
            out.println("  morpheus [--data-dir PATH] [--config-dir PATH] [--db PATH] mcp --stdio");
            out.println("  STDIO mode reserves stdout for the MCP protocol; --json is not valid in MCP mode.");
            out.println();
            out.println("API:");
            out.println("  morpheus [--data-dir PATH] [--config-dir PATH] [--db PATH] api [--host HOST] [--port PORT]");
            out.println("  Defaults: host=127.0.0.1 port=8765; API base path=/api/v1.");
        }
        return exitCode;
    }

    static int runMcp(
            String[] args,
            PrintStream err,
            Map<String, String> environment,
            Properties properties) {
        try {
            McpLaunchOptions options = McpLaunchOptions.parse(args, environment, properties);
            return MorpheusMcpServer.run(options.layout().databasePath());
        } catch (IllegalArgumentException failure) {
            err.println("MORPHEUS MCP usage error: " + safeMessage(failure));
            return CliExitCode.USAGE.code();
        } catch (RuntimeException failure) {
            err.println("MORPHEUS MCP startup error: " + safeMessage(failure));
            return CliExitCode.INTERNAL_ERROR.code();
        }
    }

    static int runApi(
            String[] args,
            PrintStream err,
            Map<String, String> environment,
            Properties properties) {
        try {
            ApiLaunchOptions options = ApiLaunchOptions.parse(args, environment, properties);
            try (MorpheusHttpServer server = MorpheusHttpServer.start(
                    options.layout().databasePath(), options.host(), options.port())) {
                err.println("MORPHEUS API listening on " + server.baseUri());
                try {
                    Thread.currentThread().join();
                    return CliExitCode.SUCCESS.code();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return CliExitCode.SUCCESS.code();
                }
            }
        } catch (IllegalArgumentException failure) {
            err.println("MORPHEUS API usage error: " + safeMessage(failure));
            return CliExitCode.USAGE.code();
        } catch (RuntimeException failure) {
            err.println("MORPHEUS API startup error: " + safeMessage(failure));
            return CliExitCode.INTERNAL_ERROR.code();
        }
    }

    static String[] normalizeForExecution(String[] args) {
        List<String> normalized = new ArrayList<>(Arrays.asList(args));
        if (isSyncCommand(args) && normalized.stream().noneMatch("--force"::equals)) {
            normalized.add("--force");
        }
        return normalized.toArray(String[]::new);
    }

    private static boolean isSyncCommand(String[] args) {
        for (int index = 0; index < args.length; index++) {
            String token = args[index];
            if (token.equals("--json")) {
                continue;
            }
            if (token.equals("--data-dir") || token.equals("--config-dir") || token.equals("--db")) {
                index++;
                continue;
            }
            return token.equals("sync");
        }
        return false;
    }

    private static boolean isHelpRequest(String[] args) {
        for (int index = 0; index < args.length; index++) {
            String token = args[index];
            if (token.equals("--json")) {
                continue;
            }
            if (token.equals("--data-dir") || token.equals("--config-dir") || token.equals("--db")) {
                index++;
                continue;
            }
            return token.equals("help") || token.equals("--help") || token.equals("-h");
        }
        return true;
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
