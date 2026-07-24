package com.morpheus.cli;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Official M9 launcher. CLI synchronization is conservatively executed as a full rebuild. */
public final class MorpheusMain {
    private MorpheusMain() {
    }

    public static void main(String[] args) {
        int exitCode = run(args, System.out, System.err, System.getenv(), System.getProperties());
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
        return new MorpheusCli().run(normalizeForExecution(args), out, err, environment, properties);
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
}