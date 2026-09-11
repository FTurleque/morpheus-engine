package com.morpheus.cli;

import com.morpheus.application.context.TechnicalContextBundle;
import com.morpheus.application.context.TechnicalContextItem;
import com.morpheus.application.context.TechnicalContextObservation;
import com.morpheus.application.context.TechnicalContextProvider;
import com.morpheus.application.context.TechnicalContextRequest;
import com.morpheus.application.identity.PersistentEntityIdentityResolver;
import com.morpheus.application.ingestion.ProjectSnapshotImportService;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.reference.ExternalIntegrationStatus;
import com.morpheus.application.reference.ExternalIntegrationStatusProvider;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.provider.synthetic.SyntheticSpecificationContentReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The augmented-context option parser is a user input boundary, and until 11/09/2026 no test reached it.
 *
 * <p>{@code ContextOptions} measured 0 of 49 lines and 0 of 32 branches on the aggregate report of 11/09/2026:
 * every refusal below was reachable by a user and by no test. The parser runs before the database is opened, so
 * a malformed invocation must be refused as a usage error without creating one.</p>
 */
class MorpheusAugmentedContextCliTest {
    @TempDir
    Path tempDirectory;

    @Test
    void everyMalformedOptionIsAUsageErrorNamingItAndNoDatabaseIsCreated() {
        Path data = tempDirectory.resolve("never-opened");
        String project = ProjectSpecificationId.generate().toString();
        List<String> valid = List.of("--requirement", "r", "--project", project, "--nexus-project", "nexus");

        Map<List<String>, String> refusals = new LinkedHashMap<>();
        refusals.put(List.of(), "augmented-context requires subject: requirement | change");
        refusals.put(List.of("requirement", "--unknown", "x"), "unknown option: --unknown");
        refusals.put(List.of("requirement", "--project"), "--project requires a value");
        refusals.put(List.of("requirement", "--project", "--requirement", "r"), "--project requires a value");
        refusals.put(List.of("requirement", "--source"), "--source requires a value");
        refusals.put(List.of("requirement", "--project", "a", "--project", "b"), "duplicate option: --project");
        refusals.put(List.of("requirement", "--constraint", "novalue"), "--constraint must use key=value");
        refusals.put(List.of("requirement", "--constraint", "=value"), "--constraint must use key=value");
        refusals.put(List.of("requirement", "--constraint", "key="), "--constraint must use key=value");
        refusals.put(List.of("requirement", "--constraint", "k=v", "--constraint", "k=w"), "duplicate constraint: k");
        refusals.put(List.of("decision", "--project", project), "unknown augmented-context subject: decision");
        refusals.put(List.of("requirement", "--project", project), "--requirement is required");
        refusals.put(List.of("requirement", "--requirement", "r", "--change", "c"),
                "--change is not valid for augmented-context requirement");
        refusals.put(List.of("change", "--project", project), "--change is required");
        refusals.put(List.of("change", "--change", "c", "--requirement", "r"),
                "--requirement is not valid for augmented-context change");
        refusals.put(List.of("requirement", "--requirement", "r", "--project", " "), "--project is required");
        refusals.put(List.of("requirement", "--requirement", "r", "--project", project), "--nexus-project is required");
        refusals.put(with("requirement", valid, "--budget", "ten"), "--budget must be an integer");
        refusals.put(with("requirement", valid, "--budget", "0"), "tokenBudget must be between 1 and 100000");
        refusals.put(with("requirement", valid, "--source", "nope"), "unsupported technical context sources: [NOPE]");

        for (Map.Entry<List<String>, String> refusal : refusals.entrySet()) {
            List<String> command = new ArrayList<>(List.of("augmented-context"));
            command.addAll(refusal.getKey());
            Invocation invocation = invokeMain(data, command.toArray(String[]::new));
            assertEquals(CliExitCode.USAGE.code(), invocation.exitCode(), () -> command + ": " + invocation.stderr());
            assertTrue(invocation.stderr().contains(refusal.getValue()),
                    () -> command + " must be refused with \"" + refusal.getValue() + "\": " + invocation.stderr());
        }
        assertFalse(Files.exists(databasePath(data)), "option parsing must fail before the database is opened");
    }

    @Test
    void globalOptionsMissingTheirValueAreUsageErrors() {
        Path data = tempDirectory.resolve("global-options");

        Invocation database = invokeMain(data, "augmented-context", "--db");
        assertEquals(CliExitCode.USAGE.code(), database.exitCode(), database.stderr());
        assertTrue(database.stderr().contains("--db requires a value"), database.stderr());

        Invocation config = invokeMain(data, "--config-dir", "--json", "nexus-status");
        assertEquals(CliExitCode.USAGE.code(), config.exitCode(), config.stderr());
        assertTrue(config.stderr().contains("--config-dir requires a value"), config.stderr());

        Invocation status = invokeMain(data, "nexus-status", "--project", "p");
        assertEquals(CliExitCode.USAGE.code(), status.exitCode(), status.stderr());
        assertTrue(status.stderr().contains("nexus-status does not accept command options"), status.stderr());
    }

    /** MorpheusMain only dispatches recognised commands here; the parser still refuses anything else it is handed. */
    @Test
    void aDirectInvocationWithoutACommandOrWithAForeignOneIsAUsageError() {
        MorpheusAugmentedContextCli cli = new MorpheusAugmentedContextCli(
                RecordingProvider.failing(new IllegalStateException("never built")),
                RecordingProvider.failing(new IllegalStateException("never built")));

        Invocation missing = invoke(cli, "--json");
        assertEquals(CliExitCode.USAGE.code(), missing.exitCode(), missing.stderr());
        assertTrue(missing.stderr().contains("NEXUS integration command is required"), missing.stderr());

        Invocation foreign = invoke(cli, "sync");
        assertEquals(CliExitCode.USAGE.code(), foreign.exitCode(), foreign.stderr());
        assertTrue(foreign.stderr().contains("unsupported augmented-context command: sync"), foreign.stderr());
    }

    @Test
    void nexusStatusPrintsOneKeyPerLineIncludingDetailsWhenJsonIsNotRequested() {
        RecordingProvider provider = RecordingProvider.available();
        Invocation invocation = invoke(new MorpheusAugmentedContextCli(provider, provider),
                "--data-dir", tempDirectory.resolve("status").toString(), "nexus-status");

        assertEquals(0, invocation.exitCode(), invocation.stderr());
        assertEquals("""
                system=NEXUS
                state=AVAILABLE
                configured=true
                message=recording provider
                transport=stdio
                """, invocation.stdout());
    }

    @Test
    void anUnknownProjectIsAStateErrorRatherThanAUsageError() {
        String project = ProjectSpecificationId.generate().toString();
        Invocation invocation = invokeMain(tempDirectory.resolve("empty"),
                "augmented-context", "requirement", "--project", project, "--requirement", "r",
                "--nexus-project", "nexus");

        assertEquals(CliExitCode.STATE_ERROR.code(), invocation.exitCode(), invocation.stderr());
        assertTrue(invocation.stderr().contains("project not found: " + project), invocation.stderr());
    }

    @Test
    void aRequirementContextIsBuiltFromTheActiveSnapshotWhileTheEngineIsDisabled() {
        Seed seed = seed(tempDirectory.resolve("requirement"));
        Invocation invocation = invokeMain(seed.data(),
                "augmented-context", "requirement", "--project", seed.projectId(),
                "--requirement", seed.requirementId(), "--nexus-project", "nexus");

        assertEquals(0, invocation.exitCode(), invocation.stderr());
        assertTrue(invocation.stdout().contains("subjectType=REQUIREMENT\nsubjectId=" + seed.requirementId() + "\n"),
                invocation.stdout());
        assertTrue(invocation.stdout().contains("nexusState=DISABLED\npersisted=false\n"), invocation.stdout());
        assertFalse(invocation.stdout().contains("nexusProject="),
                "a disabled engine returns no bundle, so no bundle line may be invented: " + invocation.stdout());
    }

    @Test
    void anIdentityThatIsNotARequirementOfTheActiveSnapshotIsAStateError() {
        Seed seed = seed(tempDirectory.resolve("not-a-requirement"));
        Invocation invocation = invokeMain(seed.data(),
                "augmented-context", "requirement", "--project", seed.projectId(),
                "--requirement", seed.changeId(), "--nexus-project", "nexus");

        assertEquals(CliExitCode.STATE_ERROR.code(), invocation.exitCode(), invocation.stderr());
        assertTrue(invocation.stderr().contains("requirement not found in ACTIVE snapshot"), invocation.stderr());
    }

    /** What the user typed is what the engine receives, normalized once: trimmed, sources upper-cased. */
    @Test
    void everyParsedOptionReachesTheEngineNormalizedAndTheBundleIsReported() {
        Seed seed = seed(tempDirectory.resolve("change"));
        RecordingProvider provider = RecordingProvider.available();
        MorpheusAugmentedContextCli cli = new MorpheusAugmentedContextCli(provider, provider);
        String[] command = {
                "--data-dir", seed.data().toString(), "augmented-context", "change",
                "--project", seed.projectId(), "--change", seed.changeId(),
                "--nexus-project", " nexus ", "--budget", " 512 ",
                "--source", " file ", "--source", "symbol",
                "--constraint", " language = java ", "--explain"};

        Invocation text = invoke(cli, command);
        assertEquals(0, text.exitCode(), text.stderr());
        var options = provider.requests.getFirst().options();
        assertEquals("nexus", options.externalProject());
        assertEquals(512, options.tokenBudget());
        assertEquals(Set.of("FILE", "SYMBOL"), options.requestedSources());
        assertEquals(Map.of("language", "java"), options.constraints());
        assertTrue(options.explain());
        assertTrue(text.stdout().contains("subjectType=CHANGE\nsubjectId=" + seed.changeId() + "\n"), text.stdout());
        assertTrue(text.stdout().contains(
                "nexusState=AVAILABLE\npersisted=false\nnexusProject=nexus-project\ntokenBudget=512\n"
                        + "estimatedTokens=128\nitems=1\n"), text.stdout());

        List<String> json = new ArrayList<>(List.of(command));
        json.addFirst("--json");
        Invocation canonical = invoke(cli, json.toArray(String[]::new));
        assertEquals(0, canonical.exitCode(), canonical.stderr());
        assertTrue(canonical.stdout().contains("\"projectName\":\"nexus-project\""), canonical.stdout());
        assertTrue(canonical.stdout().contains("\"persisted\":false"), canonical.stdout());
    }

    @Test
    void anEngineFailureWithoutAMessageIsReportedByItsTypeAsAStateError() {
        Seed seed = seed(tempDirectory.resolve("engine-failure"));
        RecordingProvider provider = RecordingProvider.failing(new UnsupportedOperationException());
        Invocation invocation = invoke(new MorpheusAugmentedContextCli(provider, provider),
                "--data-dir", seed.data().toString(), "augmented-context", "requirement",
                "--project", seed.projectId(), "--requirement", seed.requirementId(), "--nexus-project", "nexus");

        assertEquals(CliExitCode.STATE_ERROR.code(), invocation.exitCode(), invocation.stderr());
        assertTrue(invocation.stderr().contains("UnsupportedOperationException"), invocation.stderr());
    }

    private static List<String> with(String subject, List<String> valid, String... extra) {
        List<String> tokens = new ArrayList<>(List.of(subject));
        tokens.addAll(valid);
        tokens.addAll(List.of(extra));
        return tokens;
    }

    private Seed seed(Path data) {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        try (CliRuntime runtime = new CliRuntime(databasePath(data))) {
            var normalized = new SyntheticSpecificationContentReader()
                    .read(
                            ProviderReadRequest.all(fixture("synthetic-basic"), projectId),
                            new PersistentEntityIdentityResolver(runtime.identities))
                    .content()
                    .orElseThrow();
            new ProjectSnapshotImportService(
                    runtime.snapshots,
                    runtime.requirements,
                    runtime.content,
                    runtime.traceability)
                    .publishFull(normalized, Optional.of("augmented-context-cli-test"),
                            Instant.parse("2026-09-11T00:00:00Z"));
            return new Seed(
                    data,
                    projectId.toString(),
                    normalized.changes().getFirst().id().toString(),
                    normalized.requirements().getFirst().id().toString());
        }
    }

    private Path databasePath(Path data) {
        return CliLayout.resolve(Optional.of(data), Optional.empty(), Optional.empty(), Map.of(), properties())
                .databasePath();
    }

    private Invocation invokeMain(Path data, String... command) {
        String[] args = new String[command.length + 2];
        args[0] = "--data-dir";
        args[1] = data.toString();
        System.arraycopy(command, 0, args, 2, command.length);
        return capture((out, err) -> MorpheusMain.run(args, out, err, Map.of(), properties()));
    }

    private Invocation invoke(MorpheusAugmentedContextCli cli, String... args) {
        return capture((out, err) -> cli.run(args, out, err, Map.of(), properties()));
    }

    private Invocation capture(Command command) {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8)) {
            int exitCode = command.run(out, err);
            return new Invocation(
                    exitCode,
                    outBytes.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"),
                    errBytes.toString(StandardCharsets.UTF_8).replace("\r\n", "\n"));
        }
    }

    private Properties properties() {
        Properties properties = new Properties();
        properties.setProperty("os.name", System.getProperty("os.name", "Windows"));
        properties.setProperty("user.home", tempDirectory.resolve("home").toString());
        return properties;
    }

    private Path fixture(String name) {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve("experiments/m0/fixtures").resolve(name);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("M0 fixture not found: " + name);
    }

    private static final class RecordingProvider implements TechnicalContextProvider, ExternalIntegrationStatusProvider {
        private final RuntimeException failure;
        private final List<TechnicalContextRequest> requests = new ArrayList<>();

        private RecordingProvider(RuntimeException failure) {
            this.failure = failure;
        }

        static RecordingProvider available() {
            return new RecordingProvider(null);
        }

        static RecordingProvider failing(RuntimeException failure) {
            return new RecordingProvider(failure);
        }

        @Override
        public String system() {
            return "NEXUS";
        }

        @Override
        public ExternalIntegrationStatus status() {
            return new ExternalIntegrationStatus("NEXUS", "AVAILABLE", true, "recording provider",
                    Map.of("transport", "stdio"));
        }

        @Override
        public TechnicalContextObservation build(TechnicalContextRequest request) {
            requests.add(request);
            if (failure != null) {
                throw failure;
            }
            TechnicalContextItem item = new TechnicalContextItem("FILE", "src/Main.java", "Main", 1, 3,
                    "class Main {}", 0.9, Map.of("lexical", 0.9), List.of("matched intent"), 128, false);
            return TechnicalContextObservation.available(status(), new TechnicalContextBundle(
                    "nexus-id", "nexus-project", request.query(), request.options().explain(), 5,
                    request.options().tokenBudget(), 128, List.of(item), List.of(), Map.of()));
        }
    }

    @FunctionalInterface
    private interface Command {
        int run(PrintStream out, PrintStream err);
    }

    private record Seed(Path data, String projectId, String changeId, String requirementId) {
    }

    private record Invocation(int exitCode, String stdout, String stderr) {
    }
}
