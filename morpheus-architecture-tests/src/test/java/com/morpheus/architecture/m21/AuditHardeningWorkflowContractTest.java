package com.morpheus.architecture.m21;

import com.morpheus.architecture.ProductionSources;
import com.morpheus.architecture.RepositoryRoot;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditHardeningWorkflowContractTest {
    private static final String MCP_TRANSPORT_PACKAGE = "com.morpheus.transport.mcp";
    private static final String INBOUND_FAIL_CLOSED_SIGNAL = MCP_TRANSPORT_PACKAGE + ".MessageTooLargeException";
    private static final Pattern CACHE_AGE = Pattern.compile(
            "DEPENDENCY_CHECK_MAX_CACHE_AGE_HOURS:\\s*'([0-9]+)'");
    private static final Pattern RELEASE_TAG_TRIGGER = Pattern.compile(
            "(?m)^\\s*tags:\\s*\\R\\s*-\\s*'v\\*'\\s*$");

    @Test
    void dependencyCheckTrustedRefreshCannotAgePastPullRequestCacheWindow() throws IOException {
        String workflow = Files.readString(RepositoryRoot.locate().resolve(".github/workflows/security.yml"));
        Matcher cacheAge = CACHE_AGE.matcher(workflow);

        assertTrue(cacheAge.find(), "Dependency-Check cache-age policy must be explicit");
        int maximumAgeHours = Integer.parseInt(cacheAge.group(1));
        assertTrue(maximumAgeHours > 24,
                "Daily trusted refresh must complete before the pull-request cache can become stale");
        assertTrue(workflow.contains("- cron: '17 4 * * *'"),
                "Trusted Dependency-Check refresh must run daily");
        assertFalse(workflow.contains("- cron: '17 4 * * 1'"),
                "Weekly refresh would leave a deterministic stale-cache merge window");
        assertTrue(workflow.contains("DEPENDENCY_CHECK_MAX_CACHE_AGE_HOURS * 60 * 60"),
                "Freshness enforcement must use the declared cache-age policy");
    }

    @Test
    void releaseWorkflowPublishesAttestedNonOverwritableAssetsFromMain() throws IOException {
        String workflow = Files.readString(RepositoryRoot.locate().resolve(".github/workflows/release.yml"));

        assertTrue(RELEASE_TAG_TRIGGER.matcher(workflow).find(),
                "Release workflow must trigger on version tags on every supported platform");
        assertTrue(workflow.contains("contents: write"));
        assertTrue(workflow.contains("id-token: write"));
        assertTrue(workflow.contains("attestations: write"));
        assertTrue(workflow.contains("git merge-base --is-ancestor HEAD origin/main"));
        assertTrue(workflow.contains("distribution/build-release.sh"));
        assertTrue(workflow.contains("distribution/build-release.ps1"));
        assertTrue(workflow.contains(
                "actions/attest@1e69f48acb82d1966a394da916b4c1698aa569d6"),
                "Release provenance action must remain SHA-pinned");
        assertTrue(workflow.contains(
                "actions/download-artifact@9000827ccba6bdab643e8b6fd33ac0654aef8333"),
                "Cross-job release asset download must remain SHA-pinned");
        assertTrue(workflow.contains("MORPHEUS-${version}-windows-x64-setup.exe"));
        assertTrue(workflow.contains("morpheus-${version}-linux-x64.tar.gz"));
        assertTrue(workflow.contains("gh release view \"${GITHUB_REF_NAME}\""));
        assertTrue(workflow.contains("refusing to overwrite published assets"));
        assertFalse(workflow.contains("--clobber"),
                "Published release assets must never be silently replaced");
    }

    @Test
    void pullRequestCoverageGateIncludesChangedBranches() throws IOException {
        Path root = RepositoryRoot.locate();
        String workflow = Files.readString(root.resolve(".github/workflows/ci.yml"));
        String checker = Files.readString(root.resolve("scripts/check-diff-coverage.py"));

        assertTrue(workflow.contains("--minimum 0.80 --minimum-branch 0.70"),
                "CI must gate both changed lines and branches");
        assertTrue(checker.contains("--minimum-branch"));
        assertTrue(checker.contains("covered_changed_branches"));
        assertTrue(checker.contains("changed_branches"));
        assertTrue(checker.contains("Changed-branch coverage"));
        assertTrue(workflow.contains("git diff --unified=0 --no-color origin/main...HEAD --"),
                "a pull request is compared with main, the only long-lived branch");
        assertTrue(workflow.contains("echo \"::error::Unsupported pull-request base branch\""),
                "a pull request against any other base is refused by name rather than compared with nothing");
    }

    @Test
    void sonarAnalysisNeverRunsOnPullRequestsAndStaysOutOfTheRequiredVerifyJob() throws IOException {
        String workflow = Files.readString(RepositoryRoot.locate().resolve(".github/workflows/ci.yml"));

        assertFalse(requiredVerifyJob(workflow).contains("SONAR_TOKEN"),
                "the required exact-head job must never carry SONAR_TOKEN in its environment");
        assertFalse(requiredVerifyJob(workflow).contains("continue-on-error"),
                "the exact-head verify job must never be made advisory");

        String sonarJob = namedJob(workflow, "sonar");
        assertTrue(sonarJob.contains("Run SonarQube Cloud CI analysis with JaCoCo"));
        assertTrue(sonarJob.contains("SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}"),
                "Sonar analysis must authenticate through the repository secret");
        assertTrue(sonarJob.contains("if: github.event_name == 'push' && github.ref_name == 'main'"),
                "Sonar must run only for trusted pushes to main, never for pull_request events -- "
                        + "an internal branch's pull_request build must never see SONAR_TOKEN");
        assertFalse(sonarJob.contains("github.event_name == 'pull_request'")
                        || sonarJob.contains("github.event.pull_request"),
                "the Sonar job must not gate on pull_request in any condition or expression");
        assertFalse(workflow.contains("pull_request_target"),
                "CI must not expose repository secrets to pull-request code through pull_request_target");
        assertTrue(sonarJob.contains("*/target/site/jacoco/jacoco.xml"),
                "Sonar analysis must import the XML reports produced by its own reactor build");
        assertTrue(sonarJob.contains("-Dsonar.coverage.jacoco.xmlReportPaths=\"$reports\""));
        assertTrue(sonarJob.contains("-Dsonar.projectKey=FTurleque_morpheus-engine"));
        assertTrue(sonarJob.contains("-Dsonar.organization=fturleque"));
        assertTrue(sonarJob.contains("-Dsonar.host.url=https://sonarcloud.io"));
        assertTrue(sonarJob.contains("-Dsonar.qualitygate.wait=true"),
                "The Sonar job must fail when SonarQube Cloud rejects the analysis");
        assertTrue(sonarJob.contains(
                "org.sonarsource.scanner.maven:sonar-maven-plugin:5.5.0.6356:sonar"),
                "The SonarScanner for Maven must be version-pinned");
        assertTrue(sonarJob.indexOf("clean verify")
                        < sonarJob.indexOf("Run SonarQube Cloud CI analysis with JaCoCo"),
                "Coverage-producing build must complete before Sonar analysis");

        java.util.List<String> requiredChecks = java.util.List.of(
                "exact-head (ubuntu-latest)", "exact-head (windows-latest)",
                "dependency-check (ubuntu-latest)", "java (ubuntu-latest)");
        assertFalse(requiredChecks.contains("SonarQube Cloud Analysis"),
                "Sonar must stay outside the required-checks set so a SonarCloud-side outage on a trusted "
                        + "push cannot block a merge whose own required checks already passed");
    }

    /**
     * Every merge into {@code main} gets a static-analysis verdict, and the secret lives in one job only.
     *
     * <p>{@code main} is the only long-lived branch, so the {@code sonar} job of {@code ci.yml}, which runs on
     * every push to {@code main}, analyses every merge. No second workflow carries {@code SONAR_TOKEN}: a nightly
     * analysis would repeat that verdict, and every workflow that holds the token is one more place where a
     * dispatch from an unmerged branch could bring its own job definition into scope of it.</p>
     */
    @Test
    void everyMergeIsAnalysedByTheMainBranchJobAndNoOtherWorkflowCarriesTheToken() throws IOException {
        Path root = RepositoryRoot.locate();
        String nightly = Files.readString(root.resolve(".github/workflows/nightly.yml"));

        // A trigger key, not the words: the workflow explains in prose why it has no pull_request trigger, and a
        // substring search would find that explanation and fail on it.
        assertFalse(Pattern.compile("(?m)^\\s*pull_request(?:_target)?:\\s*$").matcher(nightly).find(),
                "the nightly workflow must not be reachable from a pull request in any form");
        assertTrue(nightly.contains("schedule:") && nightly.contains("- cron: "),
                "the nightly lanes must run on a bounded cadence, not only when somebody remembers to ask");
        assertFalse(nightly.contains("SONAR_TOKEN"),
                "the merge analysis of ci.yml already covers main: the nightly workflow must not carry the token");
        assertFalse(nightly.contains("sonar-maven-plugin"),
                "the nightly workflow must not run a second SonarQube Cloud analysis of main");

        String sonarJob = namedJob(Files.readString(root.resolve(".github/workflows/ci.yml")), "sonar");
        assertTrue(sonarJob.contains("if: github.event_name == 'push' && github.ref_name == 'main'"),
                "every push to main, that is every merge, must be analysed");
        assertTrue(sonarJob.contains("bash ./scripts/classify-sonar-quality-gate.sh"),
                "an indeterminate SonarCloud verdict must be classified, so an external outage cannot "
                        + "masquerade as a MORPHEUS regression");
    }

    /**
     * Isolates the required exact-head job from the advisory/trusted-only lanes beside it.
     *
     * <p>This guard used to scan the whole file for {@code continue-on-error}, which was exact while ci.yml held
     * a single job and became wrong the moment an advisory lane was added next to it. What must never be
     * advisory -- and must never carry a durable secret -- is the job that gates merges; a lane that observes an
     * unqualified platform must be advisory, and a lane that only ever runs after merge must stay out of the
     * required job entirely, or a SonarCloud-side hiccup on a trusted push (as opposed to a real regression)
     * would block the same required check it has no business gating.</p>
     */
    private String requiredVerifyJob(String workflow) {
        return namedJob(workflow, "verify");
    }

    private String namedJob(String workflow, String jobName) {
        Matcher job = Pattern.compile("(?m)^  " + Pattern.quote(jobName) + ":\\s*$").matcher(workflow);
        assertTrue(job.find(), "ci.yml must keep a job named " + jobName);
        int start = job.end();
        Matcher nextJob = Pattern.compile("(?m)^  [a-z][a-z0-9-]*:\\s*$").matcher(workflow);
        int end = nextJob.find(start) ? nextJob.start() : workflow.length();
        return workflow.substring(start, end);
    }

    @Test
    void mcpTransportDoesNotLogRawPeerControlledThrowables() throws IOException {
        Path root = RepositoryRoot.locate();
        String client = Files.readString(root.resolve(
                "morpheus-mcp-transport/src/main/java/com/morpheus/transport/mcp/BoundedStdioClientTransport.java"));
        String server = Files.readString(root.resolve(
                "morpheus-mcp-transport/src/main/java/com/morpheus/transport/mcp/BoundedStdioServerTransportProvider.java"));
        String redactor = Files.readString(root.resolve(
                "morpheus-mcp-transport/src/main/java/com/morpheus/transport/mcp/McpDiagnosticRedactor.java"));

        assertTrue(client.contains("McpDiagnosticRedactor.describe(failure)"));
        assertTrue(server.contains("McpDiagnosticRedactor.describe(failure)"));
        assertFalse(client.contains("failure.getMessage()"));
        assertFalse(server.contains("failure.getMessage()"));
        assertFalse(client.contains("\"MCP inbound processing failed\", failure"));
        assertFalse(client.contains("\"MCP outbound processing failed\", failure"));
        assertTrue(redactor.contains("JSON_OR_NAMED_SECRET"));
    }

    /**
     * ADR-0106: an outbound frame past the bound is a MORPHEUS defect, not a hostile peer. The outbound encoding
     * path must answer it with a named error and leave the fail-closed decision to its caller, which only takes it
     * when even that error cannot fit. Text, not ArchUnit: the intent is that this mechanism is not rewired, and no
     * bytecode rule says which private method calls which.
     */
    @Test
    void mcpServerTransportAnswersAnOversizedResponseInsteadOfFailingClosed() throws IOException {
        String server = Files.readString(RepositoryRoot.locate().resolve(
                "morpheus-mcp-transport/src/main/java/com/morpheus/transport/mcp/BoundedStdioServerTransportProvider.java"));

        assertTrue(server.contains("\"MCP_RESPONSE_TOO_LARGE\""));
        int encodeStart = server.indexOf("private OutboundFrame encode(");
        int encodeEnd = server.indexOf("private void requestStop()", encodeStart);
        assertTrue(encodeStart >= 0 && encodeEnd > encodeStart,
                "the outbound encoding path must stay between encode(...) and requestStop()");
        String outboundEncoding = server.substring(encodeStart, encodeEnd);
        assertFalse(outboundEncoding.contains("failClosed("),
                "the outbound encoding path must not fail the session closed itself");
        assertTrue(outboundEncoding.contains("responseTooLarge("));
    }

    /**
     * ADR-0106: the principle holds in both transports. A client has no pending peer request
     * to answer, so its only outbound treatment is refusal to the local sender, and {@code sendMessage} must catch that
     * refusal before the {@code IOException} branch that fails the session closed. Text, for the same reason as the
     * server rule above.
     */
    @Test
    void mcpClientTransportRefusesAnOversizedOutboundFrameInsteadOfFailingClosed() throws IOException {
        String client = Files.readString(RepositoryRoot.locate().resolve(
                "morpheus-mcp-transport/src/main/java/com/morpheus/transport/mcp/BoundedStdioClientTransport.java"));

        int encodeStart = client.indexOf("private OutboundFrame encode(");
        int encodeEnd = client.indexOf("private void failClosed(", encodeStart);
        assertTrue(encodeStart >= 0 && encodeEnd > encodeStart,
                "the outbound encoding path must stay between encode(...) and failClosed(...)");
        String outboundEncoding = client.substring(encodeStart, encodeEnd);
        assertFalse(outboundEncoding.contains("failClosed("),
                "the outbound encoding path must not fail the session closed itself");
        assertFalse(outboundEncoding.contains("MessageTooLargeException"),
                "outbound, the inbound fail-closed signal must not be raised");
        assertTrue(outboundEncoding.contains("new OutboundMessageRefusedException("));

        int sendStart = client.indexOf("public Mono<Void> sendMessage(");
        int sendEnd = client.indexOf("public Mono<Void> closeGracefully()", sendStart);
        assertTrue(sendStart >= 0 && sendEnd > sendStart, "sendMessage must precede closeGracefully");
        String send = client.substring(sendStart, sendEnd);
        int refusal = send.indexOf("catch (OutboundMessageRefusedException");
        int ioFailure = send.indexOf("catch (IOException");
        assertTrue(refusal >= 0 && ioFailure > refusal,
                "sendMessage must hand an outbound refusal back to its sender before the fail-closed branch");
    }

    /**
     * ADR-0106, on the bytecode: the client's outbound encoding, {@code encode} and any lambda it declares, neither
     * raises nor names {@code MessageTooLargeException}, the inbound fail-closed signal. The textual ban above sees the
     * region of the source between {@code encode(} and {@code failClosed(}; this rule sees the method however the
     * signal reaches it, through a call, a {@code throws} clause, a class literal or an {@code instanceof}.
     * {@code MessageTooLargeException} declares no compile-time constant, so the rule has no blind spot.
     *
     * <p>Broken before acceptance, 06/11/2026: {@code if (encoded.length < 0) throw new MessageTooLargeException(0);}
     * written into {@code encode}; this method failed and named the constructor call in {@code encode}.</p>
     */
    @Test
    void mcpClientOutboundEncodingNeverRaisesTheInboundFailClosedSignal() {
        JavaClasses transport = new ClassFileImporter().importPackages(MCP_TRANSPORT_PACKAGE);
        noMethods()
                .that().areDeclaredInClassesThat()
                .haveFullyQualifiedName(MCP_TRANSPORT_PACKAGE + ".BoundedStdioClientTransport")
                .and(DescribedPredicate.describe("are encode or a lambda declared in it",
                        (JavaMethod method) -> method.getName().equals("encode")
                                || method.getName().startsWith("lambda$encode$")))
                .should(new ArchCondition<JavaMethod>("raise or name " + INBOUND_FAIL_CLOSED_SIGNAL) {
                    @Override
                    public void check(JavaMethod method, ConditionEvents events) {
                        boolean touches = Stream.of(
                                        method.getAccessesFromSelf().stream().map(access -> access.getTargetOwner()),
                                        method.getThrowsClause().getTypes().stream(),
                                        method.getReferencedClassObjects().stream()
                                                .map(reference -> reference.getRawType()),
                                        method.getInstanceofChecks().stream().map(check -> check.getRawType()))
                                .flatMap(types -> types)
                                .anyMatch(type -> type.getName().equals(INBOUND_FAIL_CLOSED_SIGNAL));
                        events.add(new SimpleConditionEvent(method, touches,
                                method.getFullName() + (touches ? " raises or names " : " leaves ")
                                        + INBOUND_FAIL_CLOSED_SIGNAL));
                    }
                })
                .because("an oversized outbound frame is MORPHEUS's own and is refused to its sender; failing the "
                        + "session closed is the answer to a hostile inbound peer only (ADR-0106)")
                .check(transport);
    }

    /**
     * The MCP transport module is shared by the MORPHEUS server and by the MINOS and NEXUS clients. Naming a tool of
     * the server catalog in it would leak the application into the transport; the guidance for an oversized response
     * is supplied by the layer that owns the catalog (ADR-0106).
     */
    @Test
    void mcpTransportNamesNoServerCatalogTool() throws IOException {
        Path root = RepositoryRoot.locate();
        String catalog = Files.readString(ProductionSources.mainSource("morpheus-mcp", "MorpheusMcpToolCatalog"));
        List<String> tools = Pattern.compile("tool\\(\"([a-z_]+)\"").matcher(catalog).results()
                .map(match -> match.group(1))
                .toList();
        assertTrue(tools.contains("find_requirements") && tools.contains("get_current_specification"),
                () -> "the catalog tool names must be readable from the catalog source: " + tools);

        Path transportSources = root.resolve("morpheus-mcp-transport/src/main/java");
        try (Stream<Path> files = Files.walk(transportSources)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (String tool : tools) {
                    assertFalse(source.contains("\"" + tool + "\""),
                            () -> transportSources.relativize(file) + " must not name the server tool " + tool);
                }
            }
        }
    }

    @Test
    void remoteDiscoveryCannotRegressToChecksumOnlyOrClaimVerifiedTrust() throws IOException {
        Path root = RepositoryRoot.locate();
        String manifest = Files.readString(root.resolve(
                "morpheus-application/src/main/java/com/morpheus/application/product/UpdateManifest.java"));
        String discovery = Files.readString(root.resolve(
                "morpheus-application/src/main/java/com/morpheus/application/product/UpdateDiscoveryService.java"));

        assertTrue(manifest.contains("remote update manifest must declare attestationUri"));
        assertTrue(manifest.contains("remote update artifactUri must use https"));
        assertTrue(manifest.contains("remote update attestationUri must use https"));
        assertTrue(manifest.contains("does not cryptographically verify the attestation"),
                "discovery validation must never be presented as publisher-identity verification");
        assertTrue(discovery.contains("optionalUri(properties, \"attestationUri\")"));
        assertTrue(discovery.contains("manifest.requireRemoteDiscoveryContract(manifestUri)"));
    }

    @Test
    void policyAdaptersCannotBypassBoundedQueryDefinitionCodec() throws IOException {
        Path root = RepositoryRoot.locate();
        String codec = Files.readString(root.resolve(
                "morpheus-application/src/main/java/com/morpheus/application/query/dsl/QueryDefinitionCodec.java"));
        String validator = Files.readString(root.resolve(
                "morpheus-application/src/main/java/com/morpheus/application/query/dsl/QueryValidator.java"));

        assertTrue(codec.contains("requireEncodedSize(encoded)"),
                "QueryDefinitionCodec must reject oversized encoded input before Base64 decoding");
        assertTrue(codec.contains("budget.enterNode(depth)"),
                "QueryDefinitionCodec must account for every AST node before recursive decoding");
        assertTrue(codec.contains("budget.enterPredicate()"),
                "QueryDefinitionCodec must enforce the global predicate budget during decoding");
        assertTrue(codec.contains("new QueryValidator().requireValid(query, encoded.length())"),
                "Decoded queries must pass the shared semantic and size validator before leaving the codec");
        assertTrue(validator.contains("counters.exhausted"),
                "QueryValidator must stop traversal after a structural budget is exceeded");

        assertUsesBoundedQueryCodec(ProductionSources.mainSource("morpheus-api", "MorpheusPolicyApiService"));
        assertUsesBoundedQueryCodec(ProductionSources.mainSource("morpheus-mcp", "MorpheusPolicyMcpTools"));
        assertUsesBoundedQueryCodec(ProductionSources.mainSource("morpheus-cli", "MorpheusPolicyCli"));
    }

    private void assertUsesBoundedQueryCodec(Path adapter) throws IOException {
        String source = Files.readString(adapter);
        assertTrue(source.contains("QueryDefinitionCodec"),
                adapter + " must depend on the shared bounded query codec");
        assertTrue(source.contains("queryCodec.decode("),
                adapter + " must decode persisted QUERY_ASSERTION payloads through QueryDefinitionCodec");
        assertFalse(source.contains("Base64.getUrlDecoder()"),
                adapter + " must not implement an unbounded parallel query decoder");
    }
}
