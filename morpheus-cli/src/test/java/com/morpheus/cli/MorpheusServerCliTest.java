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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
        assertTrue(created.out().contains("LIVE_RELOAD_ON_AUTHENTICATION"));
        assertTrue(created.out().contains("\"expiresAt\":\"NEVER\""), created.out());
    }

    @Test
    void identityExpiryCanBeCreatedPreservedAndExplicitlyRemoved() throws Exception {
        String expiry = "2099-01-01T00:00:00Z";
        Result created = run("--json", "server", "identity", "create",
                "--principal", "expiring", "--role", "ADMIN", "--expires-at", expiry);
        assertEquals(CliExitCode.SUCCESS.code(), created.exitCode(), created.err());
        assertTrue(created.out().contains("\"expiresAt\":\"" + expiry + "\""), created.out());
        assertTrue(Files.readString(temp.resolve("config/remote-auth.txt")).contains("|" + expiry));

        Result listed = run("--json", "server", "identity", "list");
        assertEquals(CliExitCode.SUCCESS.code(), listed.exitCode(), listed.err());
        assertTrue(listed.out().contains("\"expired\":false"), listed.out());
        assertTrue(listed.out().contains("\"expiresAt\":\"" + expiry + "\""), listed.out());

        Result preserved = run("--json", "server", "identity", "rotate", "--principal", "expiring");
        assertEquals(CliExitCode.SUCCESS.code(), preserved.exitCode(), preserved.err());
        assertTrue(preserved.out().contains("\"expiresAt\":\"" + expiry + "\""), preserved.out());

        Result permanent = run("--json", "server", "identity", "rotate",
                "--principal", "expiring", "--expires-at", "never");
        assertEquals(CliExitCode.SUCCESS.code(), permanent.exitCode(), permanent.err());
        assertTrue(permanent.out().contains("\"expiresAt\":\"NEVER\""), permanent.out());
    }

    @Test
    void identityLifecycleCommandsNeverListCredentialMaterial() {
        Result firstAdmin = run("--json", "server", "identity", "create",
                "--principal", "admin-one", "--role", "ADMIN");
        Result secondAdmin = run("--json", "server", "identity", "create",
                "--principal", "admin-two", "--role", "ADMIN");
        Result reader = run("--json", "server", "identity", "create",
                "--principal", "reader", "--role", "READ");
        String originalReaderToken = token(reader);

        Result listed = run("--json", "server", "identity", "list");
        assertEquals(CliExitCode.SUCCESS.code(), listed.exitCode(), listed.err());
        assertTrue(listed.out().contains("\"principal\":\"reader\""), listed.out());
        assertFalse(listed.out().contains(token(firstAdmin)), listed.out());
        assertFalse(listed.out().contains(token(secondAdmin)), listed.out());
        assertFalse(listed.out().contains(originalReaderToken), listed.out());
        assertFalse(Pattern.compile("[0-9a-f]{64}").matcher(listed.out()).find(), listed.out());
        assertTrue(listed.out().contains("LIVE_RELOAD_ON_AUTHENTICATION"));

        Result rotated = run("--json", "server", "identity", "rotate", "--principal", "reader");
        assertEquals(CliExitCode.SUCCESS.code(), rotated.exitCode(), rotated.err());
        assertNotEquals(originalReaderToken, token(rotated));
        assertTrue(rotated.out().contains("LIVE_RELOAD_ON_AUTHENTICATION"));
        assertTrue(rotated.out().contains("INVALID_IMMEDIATELY"));
        assertFalse(rotated.out().contains("RESTART_REMOTE_SERVER_REQUIRED_AFTER_MUTATION"));

        Result changed = run("--json", "server", "identity", "role",
                "--principal", "reader", "--role", "WRITE");
        assertEquals(CliExitCode.SUCCESS.code(), changed.exitCode(), changed.err());
        Result revoked = run("--json", "server", "identity", "revoke", "--principal", "admin-one");
        assertEquals(CliExitCode.SUCCESS.code(), revoked.exitCode(), revoked.err());

        Result finalList = run("--json", "server", "identity", "list");
        assertTrue(finalList.out().contains("\"principal\":\"reader\",\"role\":\"WRITE\""), finalList.out());
        assertFalse(finalList.out().contains("admin-one"), finalList.out());
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
        assertTrue(verified.out().contains("\"schemaVersion\":17"), verified.out());

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

    private String token(Result result) {
        Matcher matcher = TOKEN.matcher(result.out());
        assertTrue(matcher.find(), result.out());
        return matcher.group(1);
    }

    private record Result(int exitCode, String out, String err) {
    }
}
