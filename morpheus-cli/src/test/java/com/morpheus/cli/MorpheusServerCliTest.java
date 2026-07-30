package com.morpheus.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusServerCliTest {
    private static final Pattern TOKEN = Pattern.compile("\\\"token\\\":\\\"([^\\\"]+)\\\"");
    private static final Pattern PATH = Pattern.compile("\\\"path\\\":\\\"([^\\\"]+\\.db)\\\"");

    @TempDir
    Path temp;

    @Test
    void identityProvisioningPersistsOnlyTokenHash() throws Exception {
        Result created = run("--json", "server", "identity", "create",
                "--principal", "alice", "--role", "ADMIN");
        assertEquals(CliExitCode.SUCCESS.code(), created.exitCode(), created.err());
        Matcher tokenMatcher = TOKEN.matcher(created.out());
        assertTrue(tokenMatcher.find(), created.out());
        String token = tokenMatcher.group(1);
        Path auth = temp.resolve("config/remote-auth.txt");
        String persisted = Files.readString(auth);
        assertFalse(persisted.contains(token));
        assertTrue(persisted.matches("(?s).*alice\\|ADMIN\\|[0-9a-f]{64}.*"));
        assertTrue(created.out().contains("NOT_PERSISTED_PRINTED_ONCE"));
    }

    @Test
    void backupVerifyAndConfirmedOfflineRestoreAreAvailableLocally() throws Exception {
        Result backup = run("--json", "server", "backup", "create");
        assertEquals(CliExitCode.SUCCESS.code(), backup.exitCode(), backup.err());
        assertTrue(backup.out().contains("\"integrityOk\":true"), backup.out());
        Matcher pathMatcher = PATH.matcher(backup.out());
        assertTrue(pathMatcher.find(), backup.out());
        Path backupPath = Path.of(pathMatcher.group(1).replace("\\\\", "\\"));
        assertTrue(Files.isRegularFile(backupPath));

        Result verified = run("--json", "server", "backup", "verify", "--file", backupPath.toString());
        assertEquals(CliExitCode.SUCCESS.code(), verified.exitCode(), verified.err());
        assertTrue(verified.out().contains("\"schemaVersion\":15"), verified.out());

        Result unconfirmed = run("--json", "server", "restore", "--file", backupPath.toString());
        assertEquals(CliExitCode.USAGE.code(), unconfirmed.exitCode(), unconfirmed.err());
        assertTrue(unconfirmed.err().contains("--confirm"));

        Result restored = run("--json", "server", "restore", "--file", backupPath.toString(), "--confirm");
        assertEquals(CliExitCode.SUCCESS.code(), restored.exitCode(), restored.err());
        assertTrue(restored.out().contains("\"integrityOk\":true"), restored.out());
    }

    private Result run(String... rawArgs) {
        List<String> args = new ArrayList<>();
        args.add("--data-dir");
        args.add(temp.resolve("data").toString());
        args.add("--config-dir");
        args.add(temp.resolve("config").toString());
        args.add("--db");
        args.add(temp.resolve("data/morpheus.db").toString());
        args.addAll(List.of(rawArgs));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int exit;
        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.setProperty("user.home", temp.resolve("home").toString());
            properties.setProperty("os.name", System.getProperty("os.name", "Linux"));
            exit = MorpheusMain.run(args.toArray(String[]::new), out, err, Map.of(), properties);
        }
        return new Result(exit, output.toString(StandardCharsets.UTF_8), errors.toString(StandardCharsets.UTF_8));
    }

    private record Result(int exitCode, String out, String err) {
    }
}
