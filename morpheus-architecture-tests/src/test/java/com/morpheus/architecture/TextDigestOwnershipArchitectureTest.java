package com.morpheus.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SHA-256 of a text, and the stable key of a title, are computed in one place each (AUD-QUA-08). The digest of an
 * excerpt is what a provenance fingerprint is made of; a second computation is a place where an encoding or a
 * normalisation can change for some fingerprints and not for others.
 *
 * <p>Intent of the first rule: <em>no class outside the named list asks the platform for a message digest.</em> It is
 * a rule on the compiled call to {@code MessageDigest.getInstance}, so a copy under any method name is seen, and
 * {@code MessageDigest.isEqual} is not concerned. The algorithm name is a string argument the bytecode rule cannot
 * read, and {@code morpheus-provider-testkit} is outside the imported classpath, so a textual twin scans every
 * {@code src/main/java} of the repository for the literal call.</p>
 *
 * <p>Intent of the second rule: <em>the key, line and content helpers are declared only by their owner.</em> A private
 * method from {@code String} to {@code String} depends on nothing and has no signature a members rule could single
 * out; its name is the only handle, so the rule is textual and matches declarations, not calls.</p>
 */
class TextDigestOwnershipArchitectureTest {
    private static final String TEXT_DIGEST = "com.morpheus.application.foundation.TextDigest";
    private static final String OPENSPEC_SOURCE_TEXT = "com.morpheus.provider.openspec.OpenSpecSourceText";

    /**
     * The classes that obtain a message digest from the platform, each with why it does not use {@code TextDigest}.
     * The list only shrinks.
     */
    private static final Map<String, String> DIGEST_SITES = new TreeMap<>(Map.of(
            TEXT_DIGEST,
            "the owner of the SHA-256 of a text",
            "com.morpheus.application.security.ExternalJarIntegrity",
            "hashes a JAR file by streaming it, without reading it whole",
            "com.morpheus.application.sync.SourceFingerprint",
            "hashes a source while it reads it, for its size and its bytes in one pass",
            "com.morpheus.store.sqlite.runtime.SqliteServerMaintenance",
            "hashes a backup file by streaming it, under the backup's own error handling",
            "com.morpheus.api.remote.RemoteIdentityCredentialService",
            "hashes a presented token to bytes for a constant-time comparison; the token material stays in this "
                    + "class (RemoteIdentityDecompositionContractTest)"));

    private static final String DIGEST_CALL = "getInstance(\"SHA-256\")";

    /** A declaration, not a call: modifiers, a return type, then the name. Applied line by line. */
    private static final Pattern HELPER_DECLARATION = Pattern.compile(
            "^\\s*(?:(?:public|protected|private|static|final)\\s+)*(?!return\\b|throw\\b|new\\b|else\\b)"
                    + "[A-Za-z_][\\w.]*(?:<[^()]*>)?(?:\\[])*\\s+"
                    + "(slug|stableKey|readAllLines|joinContent|sha256(?=\\s*\\(\\s*String\\b))\\s*\\(");

    /** Which file may declare which helper; a helper absent from this map may be declared nowhere. */
    private static final Map<String, String> HELPER_OWNERS = Map.of(
            "sha256", "morpheus-application/src/main/java/com/morpheus/application/foundation/TextDigest.java",
            "readAllLines", "morpheus-provider-openspec/src/main/java/com/morpheus/provider/openspec/OpenSpecSourceText.java",
            "joinContent", "morpheus-provider-openspec/src/main/java/com/morpheus/provider/openspec/OpenSpecSourceText.java");

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus");

    @Test
    void onlyTheNamedClassesAskThePlatformForAMessageDigest() {
        noClasses()
                .that(DescribedPredicate.not(named(DIGEST_SITES.keySet())))
                .should().callMethodWhere(asksForAMessageDigest())
                .because("the SHA-256 of a text is TextDigest.sha256; a class that obtains a digest itself is named "
                        + "in TextDigestOwnershipArchitectureTest with its reason, and that list only shrinks")
                .check(classes);
    }

    @Test
    void everyNamedClassStillAsksThePlatformForAMessageDigest() {
        Set<String> asking = new TreeSet<>();
        for (JavaClass origin : classes) {
            boolean asks = origin.getMethodCallsFromSelf().stream().anyMatch(asksForAMessageDigest());
            if (asks) asking.add(topLevelName(origin));
        }
        assertEquals(DIGEST_SITES.keySet(), asking,
                "the named list is exactly the classes that obtain a digest: remove one that no longer does");
    }

    @Test
    void noProductSourceOutsideTheNamedListCallsTheDigestByItsAlgorithmName() throws IOException {
        Set<String> allowed = new TreeSet<>();
        DIGEST_SITES.keySet().forEach(name -> allowed.add(name.replace('.', '/') + ".java"));
        List<String> offenders = new ArrayList<>();
        List<String> otherWordings = new ArrayList<>();
        for (Path source : productSources()) {
            String text = Files.readString(source);
            String relative = relative(source);
            if (text.contains(DIGEST_CALL) && allowed.stream().noneMatch(relative::endsWith)) {
                offenders.add(relative);
            }
            if (text.contains("\"SHA-256 is unavailable\"")) otherWordings.add(relative);
        }
        assertEquals(List.of(), offenders,
                "these sources obtain a SHA-256 themselves; use TextDigest.sha256, or name the class with its reason");
        assertEquals(List.of(), otherWordings,
                "a platform without SHA-256 is reported as '" + "SHA-256 must be available" + "', in every module");
    }

    @Test
    void theKeyLineAndContentHelpersAreDeclaredOnlyByTheirOwner() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path source : productSources()) {
            String relative = relative(source);
            List<String> lines = Files.readAllLines(source);
            for (int index = 0; index < lines.size(); index++) {
                Matcher declaration = HELPER_DECLARATION.matcher(lines.get(index));
                if (declaration.find() && !relative.equals(HELPER_OWNERS.get(declaration.group(1)))) {
                    offenders.add(relative + ":" + (index + 1) + " declares " + declaration.group(1));
                }
            }
        }
        assertEquals(List.of(), offenders,
                "a stable key comes from StableKey.fromTitle, a digest from TextDigest.sha256, and the OpenSpec "
                        + "reading and joining from OpenSpecSourceText");
    }

    @Test
    void theDeclarationPatternIsNotAFamilyPrefix() {
        assertTrue(HELPER_DECLARATION.matcher("    private String slug(String value) {").find());
        assertTrue(HELPER_DECLARATION.matcher("    private static String sha256(String value) {").find());
        assertTrue(HELPER_DECLARATION.matcher("    private List<String> readAllLines(").find());
        assertFalse(HELPER_DECLARATION.matcher("    public static String sha256(Path jar) {").find(),
                "ExternalJarIntegrity.sha256(Path) is a file digest, not a copy of the text digest");
        assertFalse(HELPER_DECLARATION.matcher("    static String sha256Hex(String token) {").find());
        assertFalse(HELPER_DECLARATION.matcher("        return slug(value);").find());
        assertFalse(HELPER_DECLARATION.matcher("        String key = slug(title);").find());
        assertFalse(HELPER_DECLARATION.matcher("    private String slugify(String value) {").find());
    }

    @Test
    void theRulesSeeTheOwnersTheyProtect() {
        assertTrue(classes.contain(TEXT_DIGEST), "TextDigest was not imported: the digest rule would pass on nothing");
        assertTrue(classes.contain(OPENSPEC_SOURCE_TEXT),
                "OpenSpecSourceText was not imported: the helper rule names an owner that does not exist");
    }

    private static DescribedPredicate<JavaCall<?>> asksForAMessageDigest() {
        return DescribedPredicate.describe("MessageDigest.getInstance",
                call -> call.getTargetOwner().isEquivalentTo(MessageDigest.class)
                        && call.getName().equals("getInstance"));
    }

    private static DescribedPredicate<JavaClass> named(Set<String> names) {
        return DescribedPredicate.describe("named as a digest site", javaClass -> names.contains(topLevelName(javaClass)));
    }

    private static String topLevelName(JavaClass javaClass) {
        String name = javaClass.getName();
        int nested = name.indexOf('$');
        return nested < 0 ? name : name.substring(0, nested);
    }

    private static List<Path> productSources() throws IOException {
        Path root = RepositoryRoot.locate();
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> modules = Files.list(root)) {
            for (Path module : modules.filter(Files::isDirectory).sorted().toList()) {
                Path main = module.resolve("src/main/java");
                if (!Files.isDirectory(main)) continue;
                try (Stream<Path> files = Files.walk(main)) {
                    files.filter(file -> file.toString().endsWith(".java")).sorted().forEach(sources::add);
                }
            }
        }
        assertFalse(sources.isEmpty(), "no product source was found: the textual rules would pass on nothing");
        return sources;
    }

    private static String relative(Path source) {
        return RepositoryRoot.locate().relativize(source).toString().replace('\\', '/');
    }

}
