package com.morpheus.cli;

import com.morpheus.api.AllowedWorkspaceRoots;
import com.morpheus.api.MorpheusHttpServer;
import com.morpheus.api.MorpheusRemoteHttpServer;
import com.morpheus.integration.minos.MinosIntegrationRuntime;
import com.morpheus.integration.nexus.NexusIntegrationRuntime;
import com.morpheus.mcp.MorpheusMcpServer;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Official native MORPHEUS launcher for CLI, MCP STDIO and local/remote HTTP API modes. */
public final class MorpheusMain {
    private MorpheusMain() {
    }

    public static void main(String[] args) {
        int exitCode;
        if (RemoteApiLaunchOptions.isRemoteApiCommand(args)) {
            exitCode = runRemoteApi(args, System.err, System.getenv(), System.getProperties());
        } else if (ApiLaunchOptions.isApiCommand(args)) {
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
        if (MorpheusProviderPluginCli.handles(args)) {
            return new MorpheusProviderPluginCli().run(args, out, err);
        }
        if (MorpheusProductCli.handles(args)) {
            return new MorpheusProductCli().run(args, out, err);
        }
        if (MorpheusServerCli.handles(args)) {
            return new MorpheusServerCli().run(args, out, err, environment, properties);
        }
        MinosIntegrationRuntime minos = MinosIntegrationRuntime.resolve(environment, properties);
        NexusIntegrationRuntime nexus = NexusIntegrationRuntime.resolve(environment, properties);
        int exitCode;
        if (MorpheusPolicyCli.handles(args)) {
            exitCode = new MorpheusPolicyCli().run(args, out, err, environment, properties);
        } else if (MorpheusQueryCli.handles(args)) {
            exitCode = new MorpheusQueryCli().run(args, out, err, environment, properties);
        } else if (MorpheusPortfolioCli.handles(args)) {
            exitCode = new MorpheusPortfolioCli().run(args, out, err, environment, properties);
        } else if (MorpheusCompositionCli.handles(args)) {
            exitCode = new MorpheusCompositionCli().run(args, out, err, environment, properties);
        } else if (MorpheusControlledLifecycleCli.handles(args)) {
            exitCode = new MorpheusControlledLifecycleCli().run(args, out, err, environment, properties);
        } else if (MorpheusConstraintSemanticsCli.handles(args)) {
            exitCode = new MorpheusConstraintSemanticsCli().run(args, out, err, environment, properties);
        } else if (MorpheusAcceptanceCriteriaCli.handles(args)) {
            exitCode = new MorpheusAcceptanceCriteriaCli().run(args, out, err, environment, properties);
        } else if (MorpheusJarvisOrchestrationCli.handles(args)) {
            exitCode = new MorpheusJarvisOrchestrationCli().run(args, out, err, environment, properties);
        } else if (MorpheusAugmentedContextCli.handles(args)) {
            exitCode = new MorpheusAugmentedContextCli(nexus, nexus)
                    .run(args, out, err, environment, properties);
        } else if (MorpheusExternalIntegrationCli.handles(args)) {
            exitCode = new MorpheusExternalIntegrationCli(minos.resolverRegistry(), minos)
                    .run(args, out, err, environment, properties);
        } else {
            exitCode = new MorpheusCli().run(normalizeForExecution(args), out, err, environment, properties);
        }
        if (exitCode == CliExitCode.SUCCESS.code() && isHelpRequest(args)) {
            out.println();
            out.println("MCP:");
            out.println("  morpheus [--data-dir PATH] [--config-dir PATH] [--db PATH] mcp --stdio");
            out.println("  STDIO mode reserves stdout for the MCP protocol; --json is not valid in MCP mode.");
            out.println();
            out.println("API local:");
            out.println("  morpheus [--data-dir PATH] [--config-dir PATH] [--db PATH] api [--host HOST] [--port PORT]");
            out.println("  Local mode is loopback-only. Defaults: host=127.0.0.1 port=8765; API base path=/api/v1.");
            out.println();
            out.println("Team / remote server (M26, opt-in):");
            out.println("  morpheus [layout] api --remote --host HOST --port PORT --tls-keystore FILE --workspace-root PATH [--workspace-root PATH ...] [--auth-file FILE] [--max-concurrent N] [--provider-plugin-dir PATH]");
            out.println("  Remote mode is HTTPS-only and requires MORPHEUS_SERVER_TLS_PASSWORD plus at least one server-owned workspace root.");
            out.println("  morpheus [layout] server identity create --principal NAME --role READ|WRITE|ADMIN [--auth-file FILE]");
            out.println("  morpheus [layout] server backup create [--output-dir PATH]");
            out.println("  morpheus [layout] server backup verify --file PATH");
            out.println("  morpheus [layout] server restore --file PATH --confirm");
            out.println("  Restore is offline-only; stop the remote server first. Tokens are printed once and only SHA-256 hashes are persisted.");
            out.println();
            out.println("Policy packs / governance automation (M25):");
            out.println("  morpheus [--json] policy pack create --name NAME --rules RULES --actor NAME --reason TEXT");
            out.println("  morpheus [--json] policy pack list|get|versions [--id ID]");
            out.println("  morpheus [--json] policy pack update --id ID --expected-revision N --name NAME --rules RULES --actor NAME --reason TEXT");
            out.println("  morpheus [--json] policy activate --id ID --version ID (--project ID | --portfolio ID) --expected-revision N --actor NAME --reason TEXT");
            out.println("  morpheus [--json] policy deactivate --id ID (--project ID | --portfolio ID) --expected-revision N --actor NAME --reason TEXT");
            out.println("  morpheus [--json] policy override put --id ID --rule ID --mode DISABLE|FORCE_WARN|FORCE_BLOCK (--project ID | --portfolio ID) --expected-revision N --actor NAME --reason TEXT");
            out.println("  morpheus [--json] policy override list (--project ID | --portfolio ID)");
            out.println("  morpheus [--json] policy evaluate (--project ID | --portfolio ID) [--id ID]");
            out.println("  morpheus [--json] policy dry-run --id ID --version ID (--project ID | --portfolio ID)");
            out.println("  morpheus [--json] policy audit --id ID");
            out.println("  Rule grammar: id-or-new|description|KIND|SEVERITY|fields ; multiple rules separated by ';;'.");
            out.println("  Policy evaluation and dry-run are read-only; lifecycle mutations remain a distinct controlled operation.");
            out.println();
            out.println("Query DSL / saved views / reporting (M24):");
            out.println("  morpheus [--json] query execute (--project ID | --portfolio ID) --entity TYPE [--filter DSL] [--sort field:asc,...] [--fields a,b] [--offset N] [--limit N]");
            out.println("  morpheus [--json] views create --name NAME (--project ID | --portfolio ID) --entity TYPE [query options]");
            out.println("  morpheus [--json] views list (--project ID | --portfolio ID)");
            out.println("  morpheus [--json] views get|versions|execute --id ID");
            out.println("  morpheus [--json] views update --id ID --expected-revision N --name NAME --entity TYPE [query options]");
            out.println("  morpheus [--json] views archive --id ID --expected-revision N");
            out.println("  morpheus export query --format json|csv|markdown (--project ID | --portfolio ID) --entity TYPE [query options]");
            out.println("  morpheus export view --format json|csv|markdown --id ID");
            out.println("  Filter DSL examples: title contains \"security\" ; and(title contains login,providerId in [openspec,markdown])");
            out.println();
            out.println("Portfolio intelligence (M23):");
            out.println("  morpheus [--json] portfolio create --name NAME");
            out.println("  morpheus [--json] portfolio add-project --portfolio ID --project ID --name NAME [--workspace PATH] [--repository SCHEME:VALUE] [--providers a,b]");
            out.println("  morpheus [--json] portfolio overview --portfolio ID");
            out.println("  morpheus [--json] portfolio references --portfolio ID [--project ID] [--offset N] [--limit N]");
            out.println("  morpheus [--json] portfolio traverse --portfolio ID --start-project ID --start-type TYPE --start-id ID [--direction BOTH] [--depth N] [--nodes N] [--links N]");
            out.println("  Project identity remains distinct from workspace, repository and provider observations.");
            out.println();
            out.println("Provider plugins (M22, explicit only):");
            out.println("  morpheus [--json] provider-plugins discover --directory PATH");
            out.println("  morpheus [--json] provider-plugins probe --directory PATH --plugin ID --workspace PATH");
            out.println("  Discovery reads JAR metadata only; probe performs explicit compatible-plugin activation.");
            out.println();
            out.println("Product integrity (M21):");
            out.println("  morpheus [--json] version");
            out.println("  morpheus [--json] product-info");
            out.println("  morpheus [--json] update-check --manifest URI_OR_PATH");
            out.println("  Update discovery is explicit and read-only: no startup network call, download, or automatic install.");
            out.println();
            out.println("Multi-provider composition (M18):");
            out.println("  morpheus [--json] composition sync --project ID [--revision REV]");
            out.println("  morpheus [--json] composition status --project ID");
            out.println("  morpheus [--json] composition conflicts --project ID");
            out.println("  Composition preserves all provider observations and exposes precedence/conflicts explicitly.");
            out.println();
            out.println("Controlled lifecycle mutations (write, opt-in):");
            out.println("  morpheus [--json] lifecycle apply --project ID --change ID --expected-revision N --to STATE --idempotency-key KEY --actor NAME --confirm [--abandonment-reason REASON]");
            out.println("  Evaluation remains read-only; WRITE_CHANGE capability, confirmation and CAS are mandatory for mutation.");
            out.println();
            out.println("Acceptance criteria / verification:");
            out.println("  morpheus [--json] acceptance-criteria list --project ID [--change ID | --requirement ID] [--offset N] [--limit N]");
            out.println("  Criteria are explicit snapshot facts; scenarios and tests are never converted into criteria implicitly.");
            out.println();
            out.println("Constraint semantics / policy:");
            out.println("  morpheus [--json] constraints evaluate --project ID --change ID --target STATE [--offset N] [--limit N]");
            out.println("  Constraint text and severity never imply blocking; UNKNOWN remains distinct from BLOCKED.");
            out.println();
            out.println("MINOS / external references:");
            out.println("  morpheus [--json] minos-status");
            out.println("  morpheus [--json] external-references list --project ID --owner ID");
            out.println("  morpheus [--json] external-references resolve --project ID --reference ID");
            out.println("  MINOS is optional; configure MORPHEUS_MINOS_JAR to enable live resolution.");
            out.println();
            out.println("NEXUS / augmented context:");
            out.println("  morpheus [--json] nexus-status");
            out.println("  morpheus [--json] augmented-context requirement --project ID --requirement ID --nexus-project ID_OR_NAME [--budget N] [--source TYPE] [--constraint k=v] [--explain]");
            out.println("  morpheus [--json] augmented-context change --project ID --change ID --nexus-project ID_OR_NAME [--budget N] [--source TYPE] [--constraint k=v] [--explain]");
            out.println("  NEXUS is optional; configure MORPHEUS_NEXUS_JAR to enable live technical context.");
            out.println();
            out.println("JARVIS / orchestration contract (read-only):");
            out.println("  morpheus [--json] change-orchestration state --project ID --change ID [--lifecycle STATE] [--abandonment-reason REASON]");
            out.println("  morpheus [--json] change-orchestration transition-check --project ID --change ID --from STATE --to STATE [--from-abandonment-reason REASON] [--abandonment-reason REASON] [--allow-backward] [--allow-completed-reopen]");
            out.println("  MORPHEUS exposes lifecycle facts and decisions; JARVIS remains responsible for orchestration.");
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
            MinosIntegrationRuntime minos = MinosIntegrationRuntime.resolve(environment, properties);
            NexusIntegrationRuntime nexus = NexusIntegrationRuntime.resolve(environment, properties);
            return MorpheusMcpServer.run(
                    options.layout().databasePath(),
                    minos.resolverRegistry(),
                    nexus,
                    new CliProjectWriteCapabilityResolver(options.layout().databasePath()));
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
            MinosIntegrationRuntime minos = MinosIntegrationRuntime.resolve(environment, properties);
            NexusIntegrationRuntime nexus = NexusIntegrationRuntime.resolve(environment, properties);
            try (MorpheusHttpServer server = MorpheusHttpServer.start(
                    options.layout().databasePath(),
                    options.host(),
                    options.port(),
                    minos.resolverRegistry(),
                    minos,
                    nexus,
                    new CliProjectWriteCapabilityResolver(options.layout().databasePath()))) {
                err.println("MORPHEUS API listening on " + server.baseUri());
                return waitUntilInterrupted();
            }
        } catch (IllegalArgumentException failure) {
            err.println("MORPHEUS API usage error: " + safeMessage(failure));
            return CliExitCode.USAGE.code();
        } catch (RuntimeException failure) {
            err.println("MORPHEUS API startup error: " + safeMessage(failure));
            return CliExitCode.INTERNAL_ERROR.code();
        }
    }

    static int runRemoteApi(
            String[] args,
            PrintStream err,
            Map<String, String> environment,
            Properties properties) {
        try {
            RemoteApiLaunchOptions options = RemoteApiLaunchOptions.parse(args, environment, properties);
            MinosIntegrationRuntime minos = MinosIntegrationRuntime.resolve(environment, properties);
            NexusIntegrationRuntime nexus = NexusIntegrationRuntime.resolve(environment, properties);
            try (MorpheusRemoteHttpServer server = MorpheusRemoteHttpServer.start(
                    options.layout().databasePath(),
                    options.layout().backupsDirectory(),
                    options.providerPluginDirectory(),
                    AllowedWorkspaceRoots.of(options.allowedWorkspaceRoots()),
                    options.host(),
                    options.port(),
                    options.authFile(),
                    options.tlsKeyStore(),
                    options.tlsPasswordChars(),
                    options.maxConcurrentRequests(),
                    minos.resolverRegistry(),
                    minos,
                    nexus,
                    new CliProjectWriteCapabilityResolver(options.layout().databasePath()))) {
                err.println("MORPHEUS remote HTTPS API listening on " + server.baseUri());
                return waitUntilInterrupted();
            }
        } catch (IllegalArgumentException failure) {
            err.println("MORPHEUS remote API usage error: " + safeMessage(failure));
            return CliExitCode.USAGE.code();
        } catch (RuntimeException failure) {
            err.println("MORPHEUS remote API startup error: " + safeMessage(failure));
            return CliExitCode.INTERNAL_ERROR.code();
        }
    }

    private static int waitUntilInterrupted() {
        try {
            Thread.currentThread().join();
            return CliExitCode.SUCCESS.code();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return CliExitCode.SUCCESS.code();
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
