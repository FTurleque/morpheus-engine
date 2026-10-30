package com.morpheus.architecture.m20;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morpheus.architecture.CoherenceCheck;
import com.morpheus.architecture.RepositoryRoot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * How the Windows distribution is signed, and where the signing identity may be reached from.
 *
 * <p>Every rule here reads text: a workflow, an Inno Setup definition and PowerShell scripts are not on the
 * classpath, so the text is the only hold on them. The intent of each rule is one sentence in its message.</p>
 */
@CoherenceCheck
class WindowsReleaseSigningContractTest {
    private static final String DECLARATION = "distribution/windows/authenticode-signing.properties";
    private static final String ENTRY_POINT = "distribution/windows/sign-windows-binary.ps1";
    private static final String VERIFICATION = "scripts/lib/MorpheusAuthenticode.ps1";

    /** The names that reach the release signing identity: its protected environment, its variables, its state. */
    private static final List<String> SIGNING_IDENTITY_NAMES =
            List.of("windows-release-signing", "MORPHEUS_WINDOWS_SIGNING_", "-SigningState release");

    /** The named refusals of the verification, the same for the qualification and the rehearsal. */
    private static final List<String> REFUSALS = List.of(
            "AUTHENTICODE_SIGNATURE_MISSING", "AUTHENTICODE_SIGNATURE_INVALID", "AUTHENTICODE_SIGNATURE_NOT_TRUSTED",
            "AUTHENTICODE_SIGNER_UNEXPECTED", "AUTHENTICODE_TIMESTAMP_MISSING", "AUTHENTICODE_PUBLISHER_NOT_DECLARED");

    private static final Pattern RUN_KEY = Pattern.compile("^(\\s*)(?:- )?run:\\s*(.*)$");
    private static final Pattern POM_VERSION =
            Pattern.compile("<artifactId>morpheus-engine</artifactId>\\s*<version>([^<]+)</version>");

    @Test
    void noRunBlockOfAnyWorkflowExpandsASecret() throws IOException {
        Map<String, List<String>> offending = new TreeMap<>();
        List<Path> workflows = workflows();
        for (Path workflow : workflows) {
            List<String> blocks = runBlocks(Files.readString(workflow));
            for (String block : blocks) {
                if (block.contains("secrets.")) {
                    offending.computeIfAbsent(workflow.getFileName().toString(), name -> new ArrayList<>()).add(block);
                }
            }
        }
        assertTrue(workflows.size() >= 4, "too few workflows read: the rule would pass on nothing");
        assertTrue(offending.isEmpty(), () -> "a secret expanded inside a run: block reaches the command line and the "
                + "log of the step; map it under the step's env: instead: " + offending);
    }

    @Test
    void onlyTheReleaseWorkflowNamesTheReleaseSigningIdentity() throws IOException {
        for (Path workflow : workflows()) {
            String name = workflow.getFileName().toString();
            if (name.equals("release.yml")) {
                continue;
            }
            String text = executableText(workflow);
            for (String identity : SIGNING_IDENTITY_NAMES) {
                assertFalse(text.contains(identity), () -> name + " names '" + identity + "': the release signing "
                        + "identity is reachable from the release workflow on a tag only, never from a pull request "
                        + "or a schedule");
            }
        }
        String nightly = executableText(RepositoryRoot.locate().resolve(".github/workflows/nightly.yml"));
        assertTrue(nightly.contains("validate-m20.ps1 -Version $version -TestSigning"),
                "the Windows release rehearsal exercises the signing path with the run's own test identity");
    }

    @Test
    void theReleaseWorkflowCannotBeWiredToSignWhileTheDeclarationIsIncomplete() throws IOException {
        String release = executableText(RepositoryRoot.locate().resolve(".github/workflows/release.yml"));
        boolean wired = SIGNING_IDENTITY_NAMES.stream().anyMatch(release::contains);
        if (wired) {
            Map<String, String> declared = declaration();
            for (String key : List.of("publisherSubject", "signingBaseline", "timestampAuthority")) {
                assertFalse(declared.get(key).isEmpty(), () -> "release.yml reaches the release signing identity "
                        + "while " + key + " is empty in " + DECLARATION + ": the cutover declares the publisher, the "
                        + "baseline and the timestamp authority in the same change");
            }
        }
    }

    @Test
    void theSigningDeclarationIsCompleteAndItsBaselineIsNotAheadOfTheProduct() throws IOException {
        Map<String, String> declared = declaration();
        assertEquals(List.of("publisherSubject", "signingBaseline", "timestampAuthority"),
                List.copyOf(declared.keySet()), "the declaration states exactly its three fields, once each");
        String baseline = declared.get("signingBaseline");
        if (!baseline.isEmpty()) {
            Matcher version = POM_VERSION.matcher(read("pom.xml"));
            assertTrue(version.find(), "the root POM version was not found");
            assertTrue(compareVersions(baseline, version.group(1)) <= 0, () -> "the signing baseline " + baseline
                    + " is ahead of the product version " + version.group(1) + " the root POM declares");
        }
    }

    @Test
    void theQualificationAndTheRehearsalReadTheDeclarationAndHoldNoPublisherOfTheirOwn() throws IOException {
        String publisher = declaration().get("publisherSubject");
        for (String script : List.of("scripts/verify-release-provenance.ps1", "scripts/verify-release-provenance.sh",
                "scripts/validate-m20.ps1", "distribution/build-release.ps1", VERIFICATION)) {
            String text = read(script);
            assertFalse(text.contains("FTurleque"),
                    () -> script + " holds a MORPHEUS publisher name: the declaration is its only source");
            if (!publisher.isEmpty()) {
                assertFalse(text.contains(publisher),
                        () -> script + " holds the declared publisher: it must read it from " + DECLARATION);
            }
        }
        assertTrue(read(VERIFICATION).contains("authenticode-signing.properties"),
                "the shared verification reads the one declaration");
        for (String reader : List.of("scripts/verify-release-provenance.ps1", "scripts/validate-m20.ps1",
                "distribution/build-release.ps1")) {
            assertTrue(read(reader).contains("Read-MorpheusSigningDeclaration"),
                    () -> reader + " must read the declaration instead of assuming a publisher or a baseline");
        }
        assertTrue(read("scripts/verify-release-provenance.sh").contains("authenticode-signing.properties"),
                "the shell variant reads the same declaration as the Windows variant");
    }

    @Test
    void theVerificationNamesEveryRefusalAndRefusesASignedBinaryWithoutADeclaredPublisher() throws IOException {
        String verification = read(VERIFICATION);
        for (String refusal : REFUSALS) {
            assertTrue(verification.contains("'" + refusal + "'"),
                    () -> VERIFICATION + " must answer " + refusal + " by name");
        }
        assertTrue(verification.contains("[string]$signature.SignatureType -eq 'Authenticode'"),
                "a catalog signature does not travel with a published binary: only an embedded one counts");
        assertTrue(verification.contains("$signer -cne $ExpectedSubject"),
                "the signer is matched on the exact declared subject, not on validity alone");
        assertFalse(verification.contains("Set-AuthenticodeSignature"), "the verification never signs");
    }

    @Test
    void theInstallerDefinitionSignsTheInstallerAndTheUninstallerOnlyInASigningBuild() throws IOException {
        String installer = read("distribution/windows/MORPHEUS.iss");
        int open = installer.indexOf("#ifdef SignBuild");
        assertTrue(open >= 0, "the signing directive is compiled in for a signing build only");
        int close = installer.indexOf("#endif", open);
        String branch = installer.substring(open, close);
        assertTrue(branch.contains("SignTool=morpheus"), "the compiler signs through the tool build-installer defines");
        assertTrue(branch.contains("SignedUninstaller=yes"), "the uninstaller the installer deploys is signed too");
        assertEquals(1, occurrences(installer, "SignTool="), "one signing directive, inside the signing branch");
    }

    @Test
    void theToolHandedToTheCompilerCarriesNoCredential() throws IOException {
        String builder = read("distribution/build-installer.ps1");
        List<String> definitions = new ArrayList<>();
        for (String line : builder.split("\\R")) {
            if (line.contains("/Smorpheus=")) {
                definitions.add(line);
            }
        }
        assertEquals(1, definitions.size(), "build-installer.ps1 defines the signing tool once");
        String definition = definitions.get(0).toLowerCase(Locale.ROOT);
        for (String forbidden : List.of("$env:", "secret", "password", "pin", "token", "credential", "key")) {
            assertFalse(definition.contains(forbidden), () -> "the signing tool handed to the compiler contains '"
                    + forbidden + "': its command is logged in full, so it holds a script path and a file only");
        }
        assertTrue(definition.contains("sign-windows-binary.ps1") || definitions.get(0).contains("$signingEntryPoint"),
                "the compiler signs through the one signing entry point");
        assertTrue(definitions.get(0).contains("-Path $f"), "the entry point receives the compiler's file and nothing else");
        assertTrue(builder.contains("WINDOWS_SIGNING_REFUSED: the smoke installer is never signed with the release identity"),
                "the smoke installer cannot be signed with the release identity");
    }

    @Test
    void theSigningEntryPointTakesAFilePathAndNothingElse() throws IOException {
        String entryPoint = read(ENTRY_POINT);
        int parameterBlock = entryPoint.indexOf("param(");
        String parameters = entryPoint.substring(parameterBlock,
                entryPoint.indexOf(")", entryPoint.indexOf("$Path", parameterBlock)));
        Matcher declared = Pattern.compile("\\]\\s*\\$[A-Za-z]+").matcher(parameters);
        int count = 0;
        while (declared.find()) {
            count++;
        }
        assertEquals(1, count, "the entry point declares one parameter, the file to sign: " + parameters);
        assertTrue(entryPoint.contains("WINDOWS_SIGNING_FAILED") && entryPoint.contains("WINDOWS_SIGNING_TIMESTAMP_FAILED"),
                "the entry point fails by name");
        assertTrue(entryPoint.contains("switch -CaseSensitive ($state)") && entryPoint.contains("default {"),
                "an unknown signing state is refused, never treated as one of the known ones");
        assertFalse(entryPoint.contains("Write-Host $env:") || entryPoint.contains("Write-Host \"$env:"),
                "the entry point prints nothing it read from the environment");
    }

    @Test
    void theEntryPointIsTheOnlyCallerOfASigningTool() throws IOException {
        Path root = RepositoryRoot.locate();
        List<Path> files = new ArrayList<>();
        for (String directory : List.of("distribution", "scripts", ".github/workflows", "integration")) {
            try (Stream<Path> walk = Files.walk(root.resolve(directory))) {
                walk.filter(Files::isRegularFile)
                        .filter(path -> path.toString().matches(".*\\.(ps1|sh|yml|py|cmd|iss)$"))
                        .filter(path -> !path.toString().replace('\\', '/').contains("/tests/"))
                        .forEach(files::add);
            }
        }
        assertTrue(files.size() > 20, "too few scripts read: the rule would pass on nothing");
        for (Path file : files) {
            String relative = root.relativize(file).toString().replace('\\', '/');
            if (relative.equals(ENTRY_POINT)) {
                continue;
            }
            String text = executableText(file).toLowerCase(Locale.ROOT);
            for (String tool : List.of("set-authenticodesignature", "signtool.exe", "signtool sign", "osslsigncode sign",
                    "azuresigntool")) {
                assertFalse(text.contains(tool), () -> relative + " calls a signing tool (" + tool + "); "
                        + ENTRY_POINT + " is the only caller, so the credential has one place to arrive");
            }
        }
    }

    @Test
    void theLauncherIsSignedBeforeAnythingIsDerivedFromTheImage() throws IOException {
        String portable = read("distribution/build-portable.ps1");
        int jpackage = portable.indexOf("& $jpackage --type app-image");
        int signing = portable.indexOf("& $signingEntryPoint -Path $executable");
        int archive = portable.indexOf("Compress-PortableArchiveWithRetry -SourceDirectory");
        assertTrue(jpackage > 0 && signing > jpackage, "the image executables are signed after jpackage makes them");
        assertTrue(archive > signing, "the image executables are signed before the portable archive is made from them");
        assertTrue(portable.contains("WINDOWS_SIGNING_FAILED: the archive $archive carries an unsigned executable"),
                "the packaging reads the signature back from the archive it made");

        String installer = read("distribution/build-installer.ps1");
        int portableBuild = installer.indexOf("build-portable.ps1') -Version $Version -OutputDirectory $OutputDirectory -SigningState $SigningState");
        int payload = installer.indexOf("Compress-Archive -Path (Join-Path $appImage '*') -DestinationPath $payloadZip");
        assertTrue(portableBuild > 0 && payload > portableBuild,
                "the installer payload is made from the image the portable build signed");
        assertTrue(installer.contains("WINDOWS_SIGNING_FAILED: the installer payload $payloadZip carries an unsigned executable"),
                "the installer build reads the signature back from the payload it embeds");

        String release = read("distribution/build-release.ps1");
        int required = release.indexOf("WINDOWS_SIGNING_REQUIRED");
        assertTrue(required > 0 && required < release.indexOf("build-portable.ps1"),
                "a release at or above the baseline refuses an unsigned build before packaging");
        assertTrue(release.contains("-SkipPortable -SigningState $SigningState"),
                "the installer is built in the signing state of the release build");
        assertTrue(release.contains("Get-MorpheusSignatureStatement -Path $setup"),
                "the manifest states the signature read from the installer, never an intention");
    }

    private static Map<String, String> declaration() throws IOException {
        Map<String, String> declared = new java.util.LinkedHashMap<>();
        for (String line : read(DECLARATION).split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int separator = trimmed.indexOf('=');
            assertTrue(separator > 0, () -> DECLARATION + " holds a line that is not key=value: " + trimmed);
            String key = trimmed.substring(0, separator).strip();
            assertFalse(declared.containsKey(key), () -> DECLARATION + " states " + key + " twice");
            declared.put(key, trimmed.substring(separator + 1).strip());
        }
        return declared;
    }

    private static int compareVersions(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.replace("-SNAPSHOT", "").split("\\.");
        for (int index = 0; index < Math.max(leftParts.length, rightParts.length); index++) {
            int x = index < leftParts.length ? Integer.parseInt(leftParts[index]) : 0;
            int y = index < rightParts.length ? Integer.parseInt(rightParts[index]) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    /** Every {@code run:} block of a workflow: its inline value, or the lines indented under it. */
    private static List<String> runBlocks(String workflow) {
        List<String> blocks = new ArrayList<>();
        String[] lines = workflow.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            Matcher run = RUN_KEY.matcher(lines[index]);
            if (!run.matches()) {
                continue;
            }
            int indentation = run.group(1).length() + (lines[index].stripLeading().startsWith("- ") ? 2 : 0);
            StringBuilder block = new StringBuilder(run.group(2)).append('\n');
            for (int next = index + 1; next < lines.length; next++) {
                String line = lines[next];
                if (!line.isBlank() && line.length() - line.stripLeading().length() <= indentation) {
                    break;
                }
                block.append(line).append('\n');
            }
            blocks.add(block.toString());
        }
        return blocks;
    }

    private static List<Path> workflows() throws IOException {
        try (Stream<Path> files = Files.list(RepositoryRoot.locate().resolve(".github/workflows"))) {
            return files.filter(path -> path.getFileName().toString().endsWith(".yml")).sorted().toList();
        }
    }

    private static String executableText(Path file) throws IOException {
        StringBuilder text = new StringBuilder();
        for (String line : Files.readString(file).split("\\R", -1)) {
            if (line.stripLeading().startsWith("#")) {
                continue;
            }
            text.append(line.replaceFirst("\\s#.*$", "")).append('\n');
        }
        return text.toString();
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        for (int index = text.indexOf(needle); index >= 0; index = text.indexOf(needle, index + needle.length())) {
            count++;
        }
        return count;
    }

    private static String read(String relative) throws IOException {
        return Files.readString(RepositoryRoot.locate().resolve(relative));
    }
}
