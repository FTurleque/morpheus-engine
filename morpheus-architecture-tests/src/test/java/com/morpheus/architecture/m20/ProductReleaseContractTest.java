package com.morpheus.architecture.m20;

import com.morpheus.cli.CliLayout;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductReleaseContractTest {

    @Test
    void productVersionIsFrozenAtOneDotZeroDotZero() throws IOException {
        String pom = Files.readString(repoRoot().resolve("pom.xml"));
        assertTrue(pom.contains("<version>1.0.0</version>"));
        assertFalse(pom.contains("<version>0.1.0-SNAPSHOT</version>"));
    }

    @Test
    void windowsProductionLayoutSeparatesProgramFromPersistentState() {
        Path root = repoRoot().resolve("target/m20-layout-test").toAbsolutePath().normalize();
        Properties windows = properties("Windows 10", root.resolve("home"));
        Path localAppData = root.resolve("LocalAppData");

        CliLayout layout = CliLayout.resolve(
                Optional.empty(), Optional.empty(), Optional.empty(),
                Map.of("LOCALAPPDATA", localAppData.toString()), windows);

        Path persistentRoot = localAppData.resolve("MORPHEUS").toAbsolutePath().normalize();
        assertEquals(persistentRoot.resolve("data"), layout.dataDirectory());
        assertEquals(persistentRoot.resolve("config"), layout.configDirectory());
        assertEquals(persistentRoot.resolve("logs"), layout.logsDirectory());
        assertEquals(persistentRoot.resolve("backups"), layout.backupsDirectory());
        assertEquals(persistentRoot.resolve("data/morpheus.db"), layout.databasePath());

        Path programRoot = localAppData.resolve("Programs/MORPHEUS").toAbsolutePath().normalize();
        assertFalse(layout.dataDirectory().startsWith(programRoot));
        assertFalse(layout.configDirectory().startsWith(programRoot));
    }

    @Test
    void linuxProductionLayoutUsesXdgDataConfigAndStateRoots() {
        Path root = repoRoot().resolve("target/m20-xdg-test").toAbsolutePath().normalize();
        Properties linux = properties("Linux", root.resolve("home"));
        Path data = root.resolve("data");
        Path config = root.resolve("config");
        Path state = root.resolve("state");

        CliLayout layout = CliLayout.resolve(
                Optional.empty(), Optional.empty(), Optional.empty(),
                Map.of(
                        "XDG_DATA_HOME", data.toString(),
                        "XDG_CONFIG_HOME", config.toString(),
                        "XDG_STATE_HOME", state.toString()),
                linux);

        assertEquals(data.resolve("morpheus").toAbsolutePath().normalize(), layout.dataDirectory());
        assertEquals(config.resolve("morpheus").toAbsolutePath().normalize(), layout.configDirectory());
        assertEquals(state.resolve("morpheus/logs").toAbsolutePath().normalize(), layout.logsDirectory());
        assertEquals(state.resolve("morpheus/backups").toAbsolutePath().normalize(), layout.backupsDirectory());
    }

    @Test
    void releaseScriptsEnforcePerUserInstallExactTagChecksumsAndVerifiedToolBootstrap() throws IOException {
        Path root = repoRoot();
        String installer = Files.readString(root.resolve("distribution/windows/MORPHEUS.iss"));
        String windowsRelease = Files.readString(root.resolve("distribution/build-release.ps1"));
        String linuxRelease = Files.readString(root.resolve("distribution/build-release.sh"));
        String installerBuilder = Files.readString(root.resolve("distribution/build-installer.ps1"));
        String bootstrap = Files.readString(root.resolve("distribution/ensure-inno-setup.ps1"));

        assertTrue(installer.contains("DefaultDirName={localappdata}\\Programs\\MORPHEUS"));
        assertTrue(installer.contains("PrivilegesRequired=lowest"));
        assertTrue(installer.contains("Name: \"addtopath\""));
        assertFalse(installer.contains("UninstallDelete"));

        assertTrue(windowsRelease.contains("Release build requires a clean Git workspace"));
        assertTrue(windowsRelease.contains("points to $tagSha, but HEAD is $head"));
        assertTrue(windowsRelease.contains("Get-FileHash"));
        assertTrue(windowsRelease.contains("uninstallPreservesPersistentState = $true"));

        assertTrue(linuxRelease.contains("Release build requires a clean Git workspace"));
        assertTrue(linuxRelease.contains("points to $TAG_SHA, but HEAD is $HEAD_SHA"));
        assertTrue(linuxRelease.contains("sha256sum -c"));
        assertTrue(linuxRelease.contains("userJdkRequired"));

        assertTrue(installerBuilder.contains("ensure-inno-setup.ps1"));
        assertTrue(bootstrap.contains("innosetup-7.0.2-x64.exe"));
        assertTrue(bootstrap.contains("releases/download/is-7_0_2"));
        assertTrue(bootstrap.contains("Get-AuthenticodeSignature"));
        assertTrue(bootstrap.contains("Pyrsys B\\.V\\."));
        assertTrue(bootstrap.contains("/PORTABLE=1"));
        assertTrue(bootstrap.contains("/CURRENTUSER"));
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

    private Properties properties(String osName, Path home) {
        Properties properties = new Properties();
        properties.setProperty("os.name", osName);
        properties.setProperty("user.home", home.toString());
        return properties;
    }
}
