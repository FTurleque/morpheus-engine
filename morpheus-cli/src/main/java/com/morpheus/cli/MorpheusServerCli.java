package com.morpheus.cli;

import com.morpheus.api.MorpheusRemoteIdentityFile;
import com.morpheus.api.MorpheusRemoteRole;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.store.sqlite.SqliteServerMaintenance;

import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/** M26 local administrative CLI for remote identities and SQLite backup/restore. */
final class MorpheusServerCli {
    private static final String IDENTITY_RELOAD_POLICY = "LIVE_RELOAD_ON_AUTHENTICATION";
    private static final String OPT_CONFIG_DIR = "--config-dir";
    private static final String OPT_CONFIRM = "confirm";
    private final CanonicalJsonSerializer serializer = new CanonicalJsonSerializer();
    private final SqliteServerMaintenance maintenance = new SqliteServerMaintenance();

    static boolean handles(String[] args) {
        for (int index = 0; index < args.length; index++) {
            String token = args[index];
            if (token.equals("--json")) continue;
            if (isLayoutOption(token)) {
                if (!token.contains("=")) index++;
                continue;
            }
            return token.equals("server");
        }
        return false;
    }

    int run(
            String[] args,
            PrintStream out,
            PrintStream err,
            Map<String, String> environment,
            Properties properties) {
        try {
            Parsed parsed = parse(args, environment, properties);
            List<String> command = parsed.command();
            if (command.size() >= 3 && command.get(1).equals("identity")) {
                return switch (command.get(2)) {
                    case "create" -> identityCreate(parsed, out);
                    case "list" -> identityList(parsed, out);
                    case "revoke" -> identityRevoke(parsed, out);
                    case "rotate" -> identityRotate(parsed, out);
                    case "role" -> identityRole(parsed, out);
                    default -> throw new IllegalArgumentException(
                            "server identity command must be create, list, revoke, rotate, or role");
                };
            }
            if (command.size() >= 3 && command.get(1).equals("backup") && command.get(2).equals("create")) {
                return backupCreate(parsed, out);
            }
            if (command.size() >= 3 && command.get(1).equals("backup") && command.get(2).equals("verify")) {
                return backupVerify(parsed, out);
            }
            if (command.size() >= 2 && command.get(1).equals("restore")) {
                return restore(parsed, out);
            }
            throw new IllegalArgumentException(
                    "server command must be identity create|list|revoke|rotate|role, backup create, backup verify, or restore");
        } catch (IllegalArgumentException failure) {
            err.println("MORPHEUS server usage error: " + safeMessage(failure));
            return CliExitCode.USAGE.code();
        } catch (RuntimeException failure) {
            err.println("MORPHEUS server error: " + safeMessage(failure));
            return CliExitCode.INTERNAL_ERROR.code();
        }
    }

    private int identityCreate(Parsed parsed, PrintStream out) {
        Map<String, String> options = options(
                parsed.command(), 3, Set.of("principal", "role", "auth-file", "expires-at"));
        String principal = required(options, "principal");
        MorpheusRemoteRole role = role(required(options, "role"));
        Path authFile = authFile(parsed, options);
        MorpheusRemoteIdentityFile.GeneratedCredential credential;
        if (options.containsKey("expires-at")) {
            Optional<Instant> expiry = expiry(options.get("expires-at"));
            credential = expiry.isPresent()
                    ? MorpheusRemoteIdentityFile.create(authFile, principal, role, expiry.orElseThrow())
                    : MorpheusRemoteIdentityFile.create(authFile, principal, role);
        } else {
            credential = MorpheusRemoteIdentityFile.create(authFile, principal, role);
        }
        print(parsed.json(), out, credentialView(credential, authFile));
        return CliExitCode.SUCCESS.code();
    }

    private int identityList(Parsed parsed, PrintStream out) {
        Map<String, String> options = options(parsed.command(), 3, Set.of("auth-file"));
        Path authFile = authFile(parsed, options);
        Instant now = Instant.now();
        List<Map<String, Object>> identities = MorpheusRemoteIdentityFile.load(authFile).stream()
                .map(identity -> {
                    Map<String, Object> view = new LinkedHashMap<>();
                    view.put("principal", identity.principal());
                    view.put("role", identity.role().name());
                    view.put("expiresAt", identity.expiresAt().map(Instant::toString).orElse("NEVER"));
                    view.put("expired", identity.isExpiredAt(now));
                    return Map.copyOf(view);
                })
                .toList();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("authFile", authFile.toAbsolutePath().normalize().toString());
        view.put("identities", identities);
        view.put("tokenMaterialExposed", false);
        view.put("reloadPolicy", IDENTITY_RELOAD_POLICY);
        print(parsed.json(), out, view);
        return CliExitCode.SUCCESS.code();
    }

    private int identityRevoke(Parsed parsed, PrintStream out) {
        Map<String, String> options = options(parsed.command(), 3, Set.of("principal", "auth-file"));
        String principal = required(options, "principal");
        Path authFile = authFile(parsed, options);
        MorpheusRemoteIdentityFile.revoke(authFile, principal);
        print(parsed.json(), out, mutationView("REVOKED", principal, authFile));
        return CliExitCode.SUCCESS.code();
    }

    private int identityRotate(Parsed parsed, PrintStream out) {
        Map<String, String> options = options(parsed.command(), 3, Set.of("principal", "auth-file", "expires-at"));
        String principal = required(options, "principal");
        Path authFile = authFile(parsed, options);
        MorpheusRemoteIdentityFile.GeneratedCredential credential = options.containsKey("expires-at")
                ? MorpheusRemoteIdentityFile.rotate(authFile, principal, expiry(options.get("expires-at")))
                : MorpheusRemoteIdentityFile.rotate(authFile, principal);
        Map<String, Object> view = credentialView(credential, authFile);
        view.put("mutation", "ROTATED");
        view.put("oldToken", "INVALID_IMMEDIATELY");
        print(parsed.json(), out, view);
        return CliExitCode.SUCCESS.code();
    }

    private int identityRole(Parsed parsed, PrintStream out) {
        Map<String, String> options = options(parsed.command(), 3, Set.of("principal", "role", "auth-file"));
        String principal = required(options, "principal");
        MorpheusRemoteRole role = role(required(options, "role"));
        Path authFile = authFile(parsed, options);
        MorpheusRemoteIdentityFile.changeRole(authFile, principal, role);
        Map<String, Object> view = mutationView("ROLE_CHANGED", principal, authFile);
        view.put("role", role.name());
        print(parsed.json(), out, view);
        return CliExitCode.SUCCESS.code();
    }

    private int backupCreate(Parsed parsed, PrintStream out) {
        Map<String, String> options = options(parsed.command(), 3, Set.of("output-dir"));
        Path output = Optional.ofNullable(options.get("output-dir"))
                .map(Path::of)
                .orElse(parsed.layout().backupsDirectory());
        SqliteServerMaintenance.BackupVerification backup =
                maintenance.createBackup(parsed.layout().databasePath(), output);
        print(parsed.json(), out, backupView(backup));
        return CliExitCode.SUCCESS.code();
    }

    private int backupVerify(Parsed parsed, PrintStream out) {
        Map<String, String> options = options(parsed.command(), 3, Set.of("file"));
        SqliteServerMaintenance.BackupVerification backup = maintenance.verify(Path.of(required(options, "file")));
        print(parsed.json(), out, backupView(backup));
        return CliExitCode.SUCCESS.code();
    }

    private int restore(Parsed parsed, PrintStream out) {
        Map<String, String> options = options(parsed.command(), 2, Set.of("file", OPT_CONFIRM));
        if (!"true".equals(options.get(OPT_CONFIRM))) {
            throw new IllegalArgumentException("server restore requires explicit --confirm");
        }
        SqliteServerMaintenance.BackupVerification restored = maintenance.restoreOffline(
                Path.of(required(options, "file")), parsed.layout().databasePath(), true);
        print(parsed.json(), out, backupView(restored));
        return CliExitCode.SUCCESS.code();
    }

    private Map<String, Object> credentialView(
            MorpheusRemoteIdentityFile.GeneratedCredential credential,
            Path authFile) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("principal", credential.principal());
        view.put("role", credential.role().name());
        view.put("token", credential.token());
        view.put("expiresAt", credential.expiresAt().map(Instant::toString).orElse("NEVER"));
        view.put("authFile", authFile.toAbsolutePath().normalize().toString());
        view.put("tokenPersistence", "NOT_PERSISTED_PRINTED_ONCE");
        view.put("reloadPolicy", IDENTITY_RELOAD_POLICY);
        return view;
    }

    private Map<String, Object> mutationView(String mutation, String principal, Path authFile) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("mutation", mutation);
        view.put("principal", principal);
        view.put("authFile", authFile.toAbsolutePath().normalize().toString());
        view.put("reloadPolicy", IDENTITY_RELOAD_POLICY);
        return view;
    }

    private Path authFile(Parsed parsed, Map<String, String> options) {
        return Optional.ofNullable(options.get("auth-file"))
                .map(Path::of)
                .orElse(parsed.layout().configDirectory().resolve("remote-auth.txt"));
    }

    private MorpheusRemoteRole role(String value) {
        try {
            return MorpheusRemoteRole.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("--role must be READ, WRITE, or ADMIN", failure);
        }
    }

    private Optional<Instant> expiry(String value) {
        String normalized = required(Map.of("expires-at", value), "expires-at");
        if (normalized.equalsIgnoreCase("never")) return Optional.empty();
        try {
            return Optional.of(Instant.parse(normalized));
        } catch (DateTimeParseException failure) {
            throw new IllegalArgumentException("--expires-at must be an ISO-8601 instant or 'never'", failure);
        }
    }

    private Map<String, Object> backupView(SqliteServerMaintenance.BackupVerification backup) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("path", backup.path().toString());
        view.put("bytes", backup.bytes());
        view.put("sha256", backup.sha256());
        view.put("schemaVersion", backup.schemaVersion());
        view.put("integrityOk", backup.integrityOk());
        return view;
    }

    private Parsed parse(String[] args, Map<String, String> environment, Properties properties) {
        Optional<Path> data = Optional.empty();
        Optional<Path> config = Optional.empty();
        Optional<Path> database = Optional.empty();
        boolean json = false;
        List<String> command = new ArrayList<>();
        for (int index = 0; index < args.length; index++) {
            String token = args[index];
            if (token.equals("--json")) {
                json = true;
                continue;
            }
            if (token.equals("--data-dir") || token.equals(OPT_CONFIG_DIR) || token.equals("--db")) {
                if (index + 1 >= args.length) throw new IllegalArgumentException(token + " requires a value");
                Path value = Path.of(args[++index]);
                if (token.equals("--data-dir")) data = Optional.of(value);
                if (token.equals(OPT_CONFIG_DIR)) config = Optional.of(value);
                if (token.equals("--db")) database = Optional.of(value);
                continue;
            }
            if (token.startsWith("--data-dir=") || token.startsWith("--config-dir=") || token.startsWith("--db=")) {
                int separator = token.indexOf('=');
                Path value = Path.of(token.substring(separator + 1));
                String option = token.substring(0, separator);
                if (option.equals("--data-dir")) data = Optional.of(value);
                if (option.equals(OPT_CONFIG_DIR)) config = Optional.of(value);
                if (option.equals("--db")) database = Optional.of(value);
                continue;
            }
            command.add(token);
        }
        if (command.isEmpty() || !command.getFirst().equals("server")) {
            throw new IllegalArgumentException("server command is required");
        }
        return new Parsed(CliLayout.resolve(data, config, database, environment, properties), json, List.copyOf(command));
    }

    private Map<String, String> options(List<String> command, int start, Set<String> allowed) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = start; index < command.size(); index++) {
            String token = command.get(index);
            if (!token.startsWith("--")) throw new IllegalArgumentException("unexpected server argument: " + token);
            String name = token.substring(2);
            if (!allowed.contains(name)) throw new IllegalArgumentException("unknown server option: --" + name);
            if (name.equals(OPT_CONFIRM)) {
                if (result.put(name, "true") != null) throw new IllegalArgumentException("duplicate --confirm");
                continue;
            }
            if (index + 1 >= command.size()) throw new IllegalArgumentException(token + " requires a value");
            String value = command.get(++index);
            if (value.startsWith("--")) throw new IllegalArgumentException(token + " requires a value");
            if (result.put(name, value) != null) throw new IllegalArgumentException("duplicate " + token);
        }
        return result;
    }

    private void print(boolean json, PrintStream out, Object value) {
        if (json) {
            out.println(serializer.toJson(value));
        } else if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> out.println(key + "=" + item));
        } else {
            out.println(serializer.toJson(value));
        }
    }

    private static String required(Map<String, String> options, String name) {
        String value = options.get(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("--" + name + " is required");
        return value.trim();
    }

    private static boolean isLayoutOption(String token) {
        return token.equals("--data-dir") || token.equals(OPT_CONFIG_DIR) || token.equals("--db")
                || token.startsWith("--data-dir=") || token.startsWith("--config-dir=") || token.startsWith("--db=");
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private record Parsed(CliLayout layout, boolean json, List<String> command) {
    }
}
