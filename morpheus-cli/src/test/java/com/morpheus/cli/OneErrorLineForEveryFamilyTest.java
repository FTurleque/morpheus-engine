package com.morpheus.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morpheus.api.remote.MorpheusRemoteIdentityFile;
import com.morpheus.api.remote.MorpheusRemoteRole;
import com.morpheus.application.operability.OperationalLogMode;
import com.morpheus.application.operability.OperationalRuntime;
import com.morpheus.cli.command.CliExitCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The command families that once wrote a prefix of their own, and the server launchers, answer a refusal with the one
 * error line of the CLI: {@code MORPHEUS error [<code>]: <message>} in text, its JSON object under {@code --json}, and
 * the exit code each family answered before.
 */
class OneErrorLineForEveryFamilyTest {
    private static final String UNKNOWN_OPTION = "unknown option: --bogus";
    private static final String KEYSTORE_PASSWORD = "changeit";
    private static final Duration LAUNCH_TIMEOUT = Duration.ofSeconds(60);

    @TempDir
    Path temp;

    @Test
    void aRefusalUnderTheJsonOutputIsOneJsonObjectWithTheSameExitCode() {
        Map<String, List<String>> families = new LinkedHashMap<>();
        families.put("update-check", List.of("--json", "update-check", "--bogus"));
        families.put("reason analyze", List.of("--json", "reason", "analyze", "--bogus"));
        families.put("provider-plugins discover", List.of("--json", "provider-plugins", "discover", "--bogus"));
        families.put("server identity list", List.of("--json", "server", "identity", "list", "--bogus"));
        families.put("acceptance-criteria list", List.of("--json", "acceptance-criteria", "list", "--bogus"));
        families.put("policy pack list", List.of("--json", "policy", "pack", "list", "--bogus"));

        families.forEach((family, words) -> {
            Result result = run(words.toArray(String[]::new));
            assertEquals(CliExitCode.USAGE.code(), result.exitCode(), family + ": " + result.err());
            List<String> lines = result.err().lines().toList();
            assertEquals(1, lines.size(), family + " writes one line: " + result.err());
            String line = lines.get(0);
            assertTrue(line.startsWith("{") && line.endsWith("}"), family + " writes a JSON object: " + line);
            assertTrue(line.contains("\"exitCode\":2"), family + ": " + line);
            assertTrue(line.contains("\"code\":\"USAGE\""), family + ": " + line);
            assertTrue(line.contains("\"message\":\"" + UNKNOWN_OPTION + "\""), family + ": " + line);
        });
    }

    @Test
    void aRefusalWithoutJsonIsTheOneTextLineForEveryFamily() {
        for (List<String> words : List.of(
                List.of("update-check", "--bogus"),
                List.of("reason", "analyze", "--bogus"),
                List.of("provider-plugins", "discover", "--bogus"),
                List.of("server", "identity", "list", "--bogus"))) {
            Result result = run(words.toArray(String[]::new));
            assertEquals(CliExitCode.USAGE.code(), result.exitCode(), words + ": " + result.err());
            assertEquals("MORPHEUS error [2]: " + UNKNOWN_OPTION, result.err(), words.toString());
        }
    }

    @Test
    void aRefusedLaunchOfEveryServerIsTheOneErrorLineWithTheUsageCode() {
        Map<String, Function<PrintStream, Integer>> launchers = new LinkedHashMap<>();
        launchers.put("mcp", err -> MorpheusMain.runMcp(
                runtime(), new String[] {"mcp", "--stdio", "--bogus"}, err, Map.of(), properties()));
        launchers.put("api", err -> MorpheusMain.runApi(
                runtime(), new String[] {"api", "--bogus"}, err, Map.of(), properties()));
        launchers.put("api --remote", err -> MorpheusMain.runRemoteApi(
                runtime(), new String[] {"api", "--remote", "--bogus"}, err, Map.of(), properties()));

        launchers.forEach((launcher, launcherCall) -> {
            Launch launch = launch(launcherCall);
            assertEquals(CliExitCode.USAGE.code(), launch.exitCode(), launcher + ": " + launch.err());
            assertEquals(1, launch.err().lines().count(), launcher + " writes one line: " + launch.err());
            assertTrue(launch.err().startsWith("MORPHEUS error [2]: ") && launch.err().contains("--bogus"),
                    launcher + " refuses through the one error line: " + launch.err());
        });
    }

    @Test
    void aLocalApiThatCannotBindItsPortIsTheOneErrorLineWithTheInternalCode() throws Exception {
        try (ServerSocket occupied = occupiedLoopbackPort()) {
            String[] args = {"api", "--host", "127.0.0.1", "--port", Integer.toString(occupied.getLocalPort()),
                    "--data-dir", temp.resolve("local-data").toString()};

            Launch launch = assertTimeoutPreemptively(LAUNCH_TIMEOUT,
                    () -> launch(err -> MorpheusMain.runApi(runtime(), args, err, Map.of(), properties())));

            assertStartupFailure(launch, "MORPHEUS error [10]: API startup failed: ");
        }
    }

    @Test
    void aRemoteApiThatCannotBindItsPortIsTheOneErrorLineWithTheInternalCode() throws Exception {
        Path auth = temp.resolve("remote-auth.txt");
        MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN);
        Path keyStore = createKeyStore();
        Path workspace = Files.createDirectory(temp.resolve("workspace"));
        try (ServerSocket occupied = occupiedLoopbackPort()) {
            String[] args = {"api", "--remote", "--host", "127.0.0.1",
                    "--port", Integer.toString(occupied.getLocalPort()),
                    "--data-dir", temp.resolve("remote-data").toString(),
                    "--auth-file", auth.toString(),
                    "--tls-keystore", keyStore.toString(),
                    "--workspace-root", workspace.toString()};
            Map<String, String> environment = Map.of("MORPHEUS_SERVER_TLS_PASSWORD", KEYSTORE_PASSWORD);

            Launch launch = assertTimeoutPreemptively(LAUNCH_TIMEOUT,
                    () -> launch(err -> MorpheusMain.runRemoteApi(runtime(), args, err, environment, properties())));

            assertStartupFailure(launch, "MORPHEUS error [10]: remote API startup failed: ");
        }
    }

    private static void assertStartupFailure(Launch launch, String expectedPrefix) {
        assertEquals(CliExitCode.INTERNAL_ERROR.code(), launch.exitCode(), launch.err());
        assertEquals(1, launch.err().lines().count(), "one line: " + launch.err());
        assertTrue(launch.err().startsWith(expectedPrefix), launch.err());
    }

    private static ServerSocket occupiedLoopbackPort() throws IOException {
        ServerSocket occupied = new ServerSocket();
        occupied.setReuseAddress(false);
        occupied.bind(new InetSocketAddress("127.0.0.1", 0));
        return occupied;
    }

    private static Launch launch(Function<PrintStream, Integer> launcher) {
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        int exitCode;
        try (PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8)) {
            exitCode = launcher.apply(err);
        }
        return new Launch(exitCode, errBytes.toString(StandardCharsets.UTF_8).strip());
    }

    private Path createKeyStore() throws IOException, InterruptedException {
        Path keyStore = temp.resolve("server.p12");
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        Path keytool = Path.of(System.getProperty("java.home")).resolve("bin")
                .resolve(windows ? "keytool.exe" : "keytool");
        Process process = new ProcessBuilder(
                keytool.toString(),
                "-genkeypair",
                "-alias", "morpheus-cli-startup-test",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "2",
                "-storetype", "PKCS12",
                "-keystore", keyStore.toString(),
                "-storepass", KEYSTORE_PASSWORD,
                "-keypass", KEYSTORE_PASSWORD,
                "-dname", "CN=localhost",
                "-ext", "SAN=DNS:localhost,IP:127.0.0.1",
                "-noprompt")
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        return keyStore;
    }

    private record Launch(int exitCode, String err) {
    }

    private static OperationalRuntime runtime() {
        return OperationalRuntime.of(OperationalLogMode.OFF);
    }

    private Properties properties() {
        Properties properties = new Properties();
        properties.setProperty("os.name", System.getProperty("os.name", "Windows"));
        properties.setProperty("user.home", temp.resolve("home").toString());
        return properties;
    }

    private Result run(String... args) {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8)) {
            int exitCode = MorpheusMain.run(runtime(), args, out, err, Map.of(), properties());
            return new Result(exitCode, errBytes.toString(StandardCharsets.UTF_8).strip());
        }
    }

    private record Result(int exitCode, String err) {
    }
}
