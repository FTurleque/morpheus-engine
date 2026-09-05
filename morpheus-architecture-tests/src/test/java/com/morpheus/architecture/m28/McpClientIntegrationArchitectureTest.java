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

    @Test
    void installerExposesUncheckedClientTasksAndConservativeUninstall() throws IOException {
        Path root = repoRoot();
        String installer = Files.readString(root.resolve("distribution/windows/MORPHEUS.iss"));

        assertTrue(installer.contains("Name: \"mcp_copilot_jetbrains\""));
        assertTrue(installer.contains("Name: \"mcp_copilot_cli\""));
        assertTrue(installer.contains("Name: \"mcp_claude_code\""));
        assertTrue(installer.contains("Name: \"mcp_claude_desktop\""));
        assertTrue(installer.contains("Name: \"mcp_codex\""));
        assertTrue(installer.contains("Connecter le MCP natif MORPHEUS à :"));
        assertTrue(installer.contains("Flags: unchecked"));
        assertTrue(installer.contains("configure-mcp-clients-setup.ps1"));
        assertTrue(installer.contains("configure-mcp-clients.ps1"));
        assertTrue(installer.contains("-Action Uninstall"));
        assertTrue(installer.contains("NativeMcpClientSelected"));
        assertTrue(installer.contains("ConfigureNativeMcpClients"));
        assertFalse(installer.contains("mcp_docker"));
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
        assertTrue(Files.isRegularFile(root.resolve("scripts/validate-m28.ps1")));
        assertTrue(Files.isRegularFile(root.resolve("scripts/validate-m28.sh")));
        assertTrue(Files.isRegularFile(root.resolve("docs/user/MCP_CLIENTS.md")));
        assertTrue(Files.isRegularFile(root.resolve("docs/roadmap/M28_EXECUTION.md")));
        assertTrue(Files.isRegularFile(root.resolve("docs/validation/VALIDATION_M28.md")));
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
