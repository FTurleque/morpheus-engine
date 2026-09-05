package com.morpheus.architecture.m28;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpClientIntegrationArchitectureTest {

    /**
     * A dead second pipeline ({@code build-windows-installer.ps1}, WiX/jpackage against the retired M14
     * app-image layout) once coexisted with the official Inno Setup pipeline and could silently produce a
     * different MORPHEUS.exe with different behavior. This is not a "the file happens not to exist today"
     * check: it forbids the mechanism (a second {@code --type exe}/WiX/candle/light invocation anywhere under
     * {@code distribution/}, or more than one file invoking ISCC) from reappearing under any filename.
     */
    @Test
    void exactlyOneWindowsInstallerPipelineExists() throws IOException {
        Path root = repoRoot();
        Path distribution = root.resolve("distribution");
        assertFalse(Files.exists(distribution.resolve("build-windows-installer.ps1")),
                "the retired WiX/jpackage pipeline must not come back under its old name");

        List<Path> scripts;
        try (var files = Files.walk(distribution)) {
            scripts = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".ps1")
                            || path.getFileName().toString().endsWith(".sh"))
                    .toList();
        }

        int isccInvocationSites = 0;
        for (Path script : scripts) {
            String content = Files.readString(script);
            assertFalse(content.contains("--type exe"),
                    script + " must not invoke jpackage's own EXE/WiX packaging");
            assertFalse(content.toLowerCase(java.util.Locale.ROOT).contains("candle.exe")
                            || content.toLowerCase(java.util.Locale.ROOT).contains("light.exe")
                            || content.contains("wix.exe"),
                    script + " must not reference WiX tooling");
            if (content.contains("ISCC")) {
                isccInvocationSites++;
                assertTrue(script.getFileName().toString().equals("build-installer.ps1")
                                || script.getFileName().toString().equals("ensure-inno-setup.ps1"),
                        "unexpected ISCC reference outside the official Inno pipeline: " + script);
            }
        }
        assertTrue(isccInvocationSites >= 1, "the official Inno Setup pipeline must still invoke ISCC somewhere");
    }

    @Test
    void integrationManagerIsNativeOptInConservativeAndStateDriven() throws IOException {
        Path root = repoRoot();
        String manager = Files.readString(root.resolve("integration/configure-mcp-clients.ps1"));
        String setupWrapper = Files.readString(root.resolve("integration/configure-mcp-clients-setup.ps1"));

        assertTrue(manager.contains("[switch] $CopilotJetBrains"));
        assertTrue(manager.contains("[switch] $CopilotCli"));
        assertTrue(manager.contains("[switch] $ClaudeCode"));
        assertTrue(manager.contains("[switch] $ClaudeDesktop"));
        assertTrue(manager.contains("[switch] $Codex"));
        assertTrue(manager.contains("Join-Path $InstallRoot 'morpheus.exe'"));
        assertTrue(manager.contains("args = @('mcp', '--stdio')"));
        assertTrue(manager.contains("MORPHEUS_DATA_DIR"));
        assertTrue(manager.contains("MORPHEUS_CONFIG_DIR"));
        assertTrue(manager.contains("mcp-client-integrations.json"));
        assertTrue(manager.contains("backups\\mcp-clients"));
        assertTrue(manager.contains("existing-unmanaged-morpheus-entry"));
        assertTrue(manager.contains("managed-entry-modified"));
        assertTrue(manager.contains("Action -eq 'Install'"));
        assertTrue(manager.contains("Uninstall is state-driven"));
        assertFalse(manager.contains("docker"));

        assertTrue(setupWrapper.contains("MORPHEUS setup MCP client selection SUCCESS"));
        assertTrue(setupWrapper.contains("One or more selected MORPHEUS MCP client integrations were not configured"));
    }

    /**
     * The five MCP client checkboxes are no longer static Inno {@code [Tasks]}: they live on a custom wizard
     * page driven by a real preflight ({@code -Action Detect}), so the wizard and the manager can never
     * disagree about a client's state. This pins that the custom page exists, that it is actually populated
     * from the Detect INI report (not a guess), and that the conservative Install/Uninstall contract still
     * holds underneath it.
     */
    @Test
    void installerExposesDetectionDrivenClientPageAndConservativeUninstall() throws IOException {
        Path root = repoRoot();
        String installer = Files.readString(root.resolve("distribution/windows/MORPHEUS.iss"));

        assertTrue(installer.contains("'Clients IA'"));
        assertTrue(installer.contains("TNewCheckBox"));
        assertTrue(installer.contains("-Action Detect"));
        assertTrue(installer.contains("GetIniString"));
        assertTrue(installer.contains("procedure RunDetect"));
        assertTrue(installer.contains("procedure RefreshClientsPage"));
        assertTrue(installer.contains("AlreadyManaged"));
        assertTrue(installer.contains("NeedsRepair"));
        assertTrue(installer.contains("Detection indisponible") || installer.contains("Détection indisponible"));
        assertTrue(installer.contains("SetupTypePage"));
        assertTrue(installer.contains("Standard (recommandé)"));
        assertTrue(installer.contains("AdvancedRootsPage"));
        assertTrue(installer.contains("procedure RefreshSummaryPage"));
        assertTrue(installer.contains("configure-mcp-clients-setup.ps1"));
        assertTrue(installer.contains("configure-mcp-clients.ps1"));
        assertTrue(installer.contains("-Action Uninstall"));
        assertTrue(installer.contains("NativeMcpClientSelected"));
        assertTrue(installer.contains("ConfigureNativeMcpClients"));
        assertFalse(installer.contains("Name: \"mcp_copilot_jetbrains\""));
        assertFalse(installer.contains("mcp_docker"));
    }

    /**
     * Upgrading MORPHEUS must never leave a half-old/half-new install: PrepareToInstall runs the
     * transactional engine (stage the new payload, verify it, activate with a journal, roll back on
     * failure) instead of letting Inno copy {#SourceDir}\* directly into {app}. This pins that the engine is
     * actually wired in, not just present on disk unused.
     */
    @Test
    void installerActivatesThePayloadThroughTheTransactionalUpgradeEngine() throws IOException {
        Path root = repoRoot();
        String installer = Files.readString(root.resolve("distribution/windows/MORPHEUS.iss"));
        String engine = Files.readString(root.resolve("distribution/windows/update-installation.ps1"));
        String builder = Files.readString(root.resolve("distribution/build-installer.ps1"));

        assertTrue(installer.contains("function PrepareToInstall"));
        assertTrue(installer.contains("update-installation.ps1"));
        assertTrue(installer.contains("morpheus-payload.zip"));
        assertFalse(installer.contains("Source: \"{#SourceDir}\\*\""),
                "the app-image must be activated by the transactional engine, not copied directly by [Files]");

        assertTrue(engine.contains("$PayloadZip"));
        assertTrue(engine.contains(".install-staging"));
        assertTrue(engine.contains(".install-rollback"));
        assertTrue(engine.contains(".install-journal.json"));
        assertTrue(engine.contains("Resume-InterruptedTransaction"));
        assertTrue(engine.contains(".morpheus-install.json"));
        assertTrue(engine.contains("ReparsePoint"));

        assertTrue(builder.contains("morpheus-payload.zip"));

        // A silent/unattended install never renders the custom "Clients IA" page, so nothing ever clicks its
        // checkboxes -- confirmed empirically that Inno still walks CurPageChanged through it internally, so
        // detection itself runs either way, but ApplyCommandLineClientSelection must run unconditionally
        // (not folded into the "detection already ran" guard) or a silent install can never select a client
        // at all. /MORPHEUSMCPCLIENTS lets an unattended deployment select a client the same way a human
        // would -- but only among rows detection left Enabled, so a Conflict/NotDetected client is never
        // selected this way either.
        assertTrue(installer.contains("DetectionHasRun"));
        assertTrue(installer.contains("ApplyCommandLineClientSelection"));
        assertTrue(installer.contains("MORPHEUSMCPCLIENTS"));
        assertTrue(installer.contains("WizardSilent()"));
        // /NOICONS only takes effect with AllowNoIcons=yes; without it a silent/CI install of the real
        // Setup.exe leaves a real Start Menu shortcut on the host running the test.
        assertTrue(installer.contains("AllowNoIcons=yes"));
        assertTrue(builder.contains("Compress-Archive"));
        assertTrue(builder.contains("/DPayloadZip="));
        assertTrue(builder.contains("/DUpdateInstallationScript="));
    }

    @Test
    void portableDistributionsContainTheIntegrationLayer() throws IOException {
        Path root = repoRoot();
        String windows = Files.readString(root.resolve("distribution/build-portable.ps1"));
        String linux = Files.readString(root.resolve("distribution/build-portable.sh"));

        assertTrue(windows.contains("$integrationSource = Join-Path $repo 'integration'"));
        assertTrue(windows.contains("Packaged MCP client integration manager: PASS"));
        assertTrue(linux.contains("INTEGRATION_SOURCE=\"$REPO/integration\""));
        assertTrue(linux.contains("Packaged MCP client integration guidance: PASS"));
    }

    @Test
    void windowsValidationDispatcherDoesNotDependOnPowerShellBeingInPath() throws IOException {
        String wrapper = Files.readString(repoRoot().resolve("scripts/validate.cmd"));

        assertTrue(wrapper.contains("%SystemRoot%\\System32\\WindowsPowerShell\\v1.0\\powershell.exe"));
        assertTrue(wrapper.contains("%SystemRoot%\\Sysnative\\WindowsPowerShell\\v1.0\\powershell.exe"));
        assertTrue(wrapper.contains("where pwsh.exe"));
        assertTrue(wrapper.contains("\"%POWERSHELL_EXE%\" -NoLogo -NoProfile"));
        assertTrue(wrapper.contains("scripts\\validate.ps1"));
        assertFalse(wrapper.contains("\npowershell.exe -NoLogo"));
    }

    @Test
    void validationAndUserDocumentationArePartOfTheContract() {
        Path root = repoRoot();
        assertTrue(Files.isRegularFile(root.resolve("scripts/validate.cmd")));
        assertTrue(Files.isRegularFile(root.resolve("scripts/validate.ps1")));
        assertTrue(Files.isRegularFile(root.resolve("scripts/README.md")));
        assertTrue(Files.isRegularFile(root.resolve("scripts/verify-m28-mcp-client-integration.ps1")));
        assertTrue(Files.isRegularFile(root.resolve("scripts/verify-windows-setup-mcp-smoke.ps1")));
        assertTrue(Files.isRegularFile(root.resolve("scripts/validate-m28.ps1")));
        assertTrue(Files.isRegularFile(root.resolve("scripts/validate-m28.sh")));
        assertTrue(Files.isRegularFile(root.resolve("docs/user/MCP_CLIENTS.md")));
        assertTrue(Files.isRegularFile(root.resolve("docs/roadmap/M28_EXECUTION.md")));
        assertTrue(Files.isRegularFile(root.resolve("docs/validation/VALIDATION_M28.md")));
    }

    /**
     * The real Setup.exe was proven to never select an MCP client during a production run:
     * [Environment]::GetFolderPath and Inno's own {localappdata}/{userappdata}/{group} constants resolve
     * through the Windows Known Folder API and ignore LOCALAPPDATA/APPDATA process overrides. Rather than
     * accept "the real wizard-to-manager path is untested" as a residual risk, a smoke-only build
     * (#ifdef SmokeMode, ISCC /DSmokeMode=1) compiles in a narrow, explicitly-named override mechanism that a
     * production build never contains at all -- not merely disabled at runtime.
     */
    @Test
    void smokeModeLetsTheRealWizardSelectASandboxedClientWithoutTouchingProduction() throws IOException {
        Path root = repoRoot();
        String installer = Files.readString(root.resolve("distribution/windows/MORPHEUS.iss"));
        String builder = Files.readString(root.resolve("distribution/build-installer.ps1"));
        String setupWrapper = Files.readString(root.resolve("integration/configure-mcp-clients-setup.ps1"));
        String smokeTest = Files.readString(root.resolve("scripts/verify-windows-setup-mcp-smoke.ps1"));

        assertTrue(installer.contains("#ifdef SmokeMode"));
        assertTrue(installer.contains("function BuildSmokeOverrideParameters"));
        assertTrue(installer.contains("function UninstallParameters"));
        assertTrue(installer.contains("{code:UninstallParameters}"));
        assertTrue(installer.contains("MORPHEUS_SMOKE_MODE"));
        assertTrue(installer.contains("MORPHEUS_SMOKE_DATA_ROOT"));
        assertTrue(installer.contains("MORPHEUS_SMOKE_CONFIG_ROOT"));
        assertTrue(installer.contains("MORPHEUS_SMOKE_STATE_PATH"));
        assertTrue(installer.contains("MORPHEUS_SMOKE_LOG_PATH"));
        assertTrue(installer.contains("MORPHEUS_SMOKE_BACKUP_ROOT"));
        assertTrue(installer.contains("MORPHEUS_SMOKE_CLAUDE_DESKTOP_CONFIG_PATH"));
        assertTrue(installer.contains("MORPHEUS_SMOKE_COPILOT_JETBRAINS_CONFIG_PATH"));
        // A smoke build's HKCU uninstall key must never be able to collide with -- or be mistaken for --
        // production's, so a crash mid-test cannot leave a fake install registered as the real product.
        assertTrue(installer.contains("6BF23F0E-6C9B-4B5A-9E51-8B6D1F0C7E42"));
        assertTrue(installer.contains("4D0DC052-2FD6-49F5-88F4-E32C9B1EB67A"),
                "production's own AppId must be untouched by the smoke mechanism");
        assertTrue(installer.contains("MORPHEUS Setup Smoke"));
        assertTrue(installer.contains("setup-smoke"));

        assertTrue(builder.contains("SmokeMode"));
        assertTrue(builder.contains("/DSmokeMode=1"));

        assertTrue(setupWrapper.contains("StatePath"));
        assertTrue(setupWrapper.contains("ClaudeDesktopConfigPath"));
        assertTrue(setupWrapper.contains("CopilotJetBrainsConfigPath"));

        assertTrue(smokeTest.contains("MORPHEUSMCPCLIENTS="),
                "the smoke test must select the client through the real ApplyCommandLineClientSelection path, not bypass it");
        assertTrue(smokeTest.contains("'claude-desktop'"));
        assertTrue(smokeTest.contains("mcpServers.morpheus.command"));
        assertTrue(smokeTest.contains("MORPHEUS_DATA_DIR"));
        assertTrue(smokeTest.contains("MORPHEUS_CONFIG_DIR"));
        assertTrue(smokeTest.contains("foreign"));
        assertTrue(smokeTest.contains("Idempotent"));
        assertTrue(smokeTest.contains("manually modified") || smokeTest.contains("modified managed entry"));
        assertTrue(smokeTest.contains("RealClaudeDesktopConfig"),
                "the smoke test must assert the real machine's Claude Desktop configuration was never touched");
    }

    private Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isRegularFile(current.resolve("pom.xml")) && Files.isDirectory(current.resolve("distribution"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isRegularFile(parent.resolve("pom.xml")) && Files.isDirectory(parent.resolve("distribution"))) {
            return parent;
        }
        throw new IllegalStateException("MORPHEUS repository root not found from " + current);
    }
}
