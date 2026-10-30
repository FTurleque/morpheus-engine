package com.morpheus.architecture.m26;

import com.morpheus.application.files.SafeWorkspaceFileResolver;
import com.morpheus.architecture.ClassesOfASource;
import com.morpheus.architecture.ProductionSources;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The remote identity boundary stays decomposed, and each component stays unable to do the others' work.
 *
 * <p>One class used to own the file format, the token material, the audit and the filesystem custody at once. That
 * is a maintainability problem rather than a vulnerability, but the way out of it is only worth anything if the
 * split cannot silently collapse again: a parser that regains filesystem access, or a store that regains a digest,
 * puts every invariant back into a single blast radius. These assertions are what makes the separation executable
 * rather than a convention (issue #289).</p>
 */
class RemoteIdentityDecompositionContractTest {

    private static final String REMOTE_PACKAGE = "com.morpheus.api.remote";

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(REMOTE_PACKAGE);

    /**
     * Filesystem custody no in-memory component of the identity boundary may reach for.
     *
     * <p>None of these four types declares a compile-time constant a component could read without depending on it,
     * so the rule alone covers them. Broken before acceptance, 30/10/2026: a {@code Files.exists} call added to
     * {@code RemoteIdentityCodec} failed {@link #theCodecOwnsTheGrammarWithoutFilesystemOrTokenMaterial}.</p>
     */
    private static final DescribedPredicate<JavaClass> FILESYSTEM_CUSTODY = belongToAnyOf(
            Files.class, FileChannel.class, FileLock.class, SafeWorkspaceFileResolver.class);

    /**
     * Token material no component outside the credential service may reach for.
     *
     * <p>Broken before acceptance, 30/10/2026: a {@code MessageDigest} field added to {@code RemoteIdentityAudit}
     * failed {@link #theAuditOwnsEvidenceWithoutFilesystemOrAuthority}, and a call to
     * {@code RemoteIdentityCredentialService.sha256Bytes} added to {@code RemoteIdentityFileStore} failed
     * {@link #theFileStoreOwnsCustodyWithoutGrammarOrTokenMaterial}, and a {@code SecureRandom} field added to
     * {@code MorpheusRemoteIdentityFile} failed {@link #theFacadeDecidesPolicyAndDelegatesEveryMechanism}. The ban on
     * {@code MorpheusRemoteRole} in the store, an enum whose constants javac never inlines, failed on a read of
     * {@code MorpheusRemoteRole.ADMIN} added to {@code RemoteIdentityFileStore}.</p>
     */
    private static final DescribedPredicate<JavaClass> TOKEN_MATERIAL = belongToAnyOf(
            SecureRandom.class, MessageDigest.class);

    @Test
    void onlyTheFacadeIsPublicAndEveryComponentIsFinal() {
        assertTrue(Modifier.isPublic(load("MorpheusRemoteIdentityFile").getModifiers()),
                "the compatible public facade must stay public");
        for (String component : List.of(
                "RemoteIdentityCodec",
                "RemoteIdentityCredentialService",
                "RemoteIdentityAudit",
                "RemoteIdentityFileStore")) {
            Class<?> type = load(component);
            assertFalse(Modifier.isPublic(type.getModifiers()),
                    () -> component + " must stay package-private: it is a mechanism, not a published surface");
            assertTrue(Modifier.isFinal(type.getModifiers()),
                    () -> component + " must be final");
        }
    }

    @Test
    void theCodecOwnsTheGrammarWithoutFilesystemOrTokenMaterial() throws IOException {
        String codec = read("RemoteIdentityCodec.java");

        assertReachesNoFilesystemCustody("RemoteIdentityCodec");
        assertReachesNoTokenMaterial("RemoteIdentityCodec");
        assertTrue(codec.contains("static List<Identity> parse(List<String> lines)"));
        assertTrue(codec.contains("static List<String> format(List<Identity> identities)"));
        assertTrue(codec.contains("MAX_IDENTITIES = 256"));
        assertTrue(codec.contains("[0-9a-f]{64}"),
                "the stored verifier must stay a lowercase SHA-256 by grammar");
        assertTrue(codec.contains("fields.length != 3 && fields.length != 4"),
                "the three-field legacy entry must stay accepted input");
        assertTrue(codec.contains("blank remote identity expiry at line"),
                "a fourth field without a usable instant must never be read as 'no expiry'");
    }

    @Test
    void theCredentialServiceOwnsTokenMaterialWithoutFilesystem() throws IOException {
        String credentials = read("RemoteIdentityCredentialService.java");

        assertReachesNoFilesystemCustody("RemoteIdentityCredentialService");
        assertTrue(credentials.contains("TOKEN_BYTES = 32"));
        assertTrue(credentials.contains("new SecureRandom()"));
        assertTrue(credentials.contains("MessageDigest.isEqual(candidate, identity.tokenHash())"),
                "verifier comparison must stay constant-time");
        assertFalse(credentials.contains("Arrays.equals"),
                "a short-circuiting array comparison must never replace MessageDigest.isEqual");
        assertFalse(credentials.contains("token.equals("),
                "a presented token must never be compared as a string");
        assertTrue(credentials.contains("MAX_PRESENTED_TOKEN_CHARS = 1024"),
                "an unauthenticated caller must not choose how much input the digest consumes");
    }

    @Test
    void theAuditOwnsEvidenceWithoutFilesystemOrAuthority() throws IOException {
        String audit = read("RemoteIdentityAudit.java");

        assertReachesNoFilesystemCustody("RemoteIdentityAudit");
        assertReachesNoTokenMaterial("RemoteIdentityAudit");
        assertTrue(audit.contains("MAX_AUDIT_RECORDS = 512"));
        assertTrue(audit.contains("static RetainedAudit salvage(List<String> lines)"));
        assertTrue(audit.contains("static List<AuditRecord> parseStrict(List<String> lines)"));
        assertTrue(audit.contains("retained.size() - MAX_AUDIT_RECORDS"),
                "the window must be trimmed from the front so the newest evidence survives");
    }

    @Test
    void theFileStoreOwnsCustodyWithoutGrammarOrTokenMaterial() throws IOException {
        String store = read("RemoteIdentityFileStore.java");

        assertReachesNoTokenMaterial("RemoteIdentityFileStore");
        assertFalse(store.contains("split(\"\\\\|\""),
                "the store must not learn the identity grammar");
        noClasses()
                .that(ClassesOfASource.declaredIn("RemoteIdentityFileStore"))
                .should().dependOnClassesThat().haveFullyQualifiedName(REMOTE_PACKAGE + ".MorpheusRemoteRole")
                .because("the store keeps the identity file and must not learn what an identity means")
                .check(PRODUCTION);

        assertTrue(store.contains("MAX_FILE_BYTES = 256 * 1024"));
        assertTrue(store.contains("LinkOption.NOFOLLOW_LINKS"));
        assertTrue(store.contains("must not be a symbolic link"));
        assertTrue(store.contains("requireWriteProtectedDirectory(parent)"),
                "the ancestor chain must be revalidated on every security-sensitive read");
        assertTrue(store.contains("StandardCopyOption.ATOMIC_MOVE"));
        assertTrue(store.contains("try (FileLock ignored = channel.lock())"),
                "mutations must stay serialized across cooperating processes");
        assertTrue(store.contains("synchronized (MUTATION_LOCK)"),
                "mutations must stay serialized inside one JVM");
        assertTrue(store.contains("hardener.hardenFile(temp)"),
                "the replacement must never be briefly readable by anyone but its owner");
    }

    @Test
    void theFacadeDecidesPolicyAndDelegatesEveryMechanism() throws IOException {
        String facade = read("MorpheusRemoteIdentityFile.java");

        assertReachesNoFilesystemCustody("MorpheusRemoteIdentityFile");
        noClasses()
                .that(ClassesOfASource.declaredIn("MorpheusRemoteIdentityFile"))
                .should().dependOnClassesThat(TOKEN_MATERIAL)
                .because("the facade decides policy and delegates token material to the credential service")
                .check(PRODUCTION);

        assertTrue(facade.contains("cannot revoke the last active ADMIN identity"));
        assertTrue(facade.contains("cannot change the role of the last active ADMIN identity"));
        assertTrue(facade.contains("requireAdministratorOutliving(updated, expiry)"));
        assertTrue(facade.contains("public static LegacyMigration migrateLegacyExpiry("));

        for (Map.Entry<String, String> surface : Map.of(
                "public static List<Identity> load(Path authFile)", "RemoteIdentityCodec.parse(",
                "public static List<AuditRecord> audit(Path authFile)", "RemoteIdentityAudit.parseStrict(",
                "public static Optional<Identity> authenticate(", "RemoteIdentityCredentialService.authenticate(",
                "public static String sha256Hex(String token)", "RemoteIdentityCredentialService.sha256Hex(")
                .entrySet()) {
            assertTrue(facade.contains(surface.getKey()),
                    () -> "the public facade must keep " + surface.getKey());
            assertTrue(facade.contains(surface.getValue()),
                    () -> surface.getKey() + " must delegate to " + surface.getValue());
        }
    }

    /**
     * The facade keeps publishing the bounds callers already compile against, and keeps them equal to the values
     * the components actually enforce -- a facade constant that drifts from its component would document a limit
     * nothing applies.
     */
    @Test
    void publishedBoundsStayDelegatedToTheComponentThatEnforcesThem() throws IOException {
        String facade = read("MorpheusRemoteIdentityFile.java");

        assertTrue(facade.contains("MAX_FILE_BYTES = RemoteIdentityFileStore.MAX_FILE_BYTES"));
        assertTrue(facade.contains("MAX_IDENTITIES = RemoteIdentityCodec.MAX_IDENTITIES"));
        assertTrue(facade.contains("MAX_AUDIT_RECORDS = RemoteIdentityAudit.MAX_AUDIT_RECORDS"));
        assertTrue(facade.contains("TOKEN_BYTES = RemoteIdentityCredentialService.TOKEN_BYTES"));

        assertEquals(256 * 1024, com.morpheus.api.remote.MorpheusRemoteIdentityFile.MAX_FILE_BYTES);
        assertEquals(256, com.morpheus.api.remote.MorpheusRemoteIdentityFile.MAX_IDENTITIES);
        assertEquals(512, com.morpheus.api.remote.MorpheusRemoteIdentityFile.MAX_AUDIT_RECORDS);
        assertEquals(32, com.morpheus.api.remote.MorpheusRemoteIdentityFile.TOKEN_BYTES);
    }

    private static void assertReachesNoFilesystemCustody(String component) {
        noClasses()
                .that(ClassesOfASource.declaredIn(component))
                .should().dependOnClassesThat(FILESYSTEM_CUSTODY)
                .because(component + " is an in-memory component of the identity boundary: the file custody is "
                        + "RemoteIdentityFileStore's")
                .check(PRODUCTION);
    }

    private static void assertReachesNoTokenMaterial(String component) {
        noClasses()
                .that(ClassesOfASource.declaredIn(component))
                .should().dependOnClassesThat(TOKEN_MATERIAL)
                .orShould().callMethod(REMOTE_PACKAGE + ".RemoteIdentityCredentialService", "sha256Bytes",
                        String.class.getName())
                .because("token material belongs to RemoteIdentityCredentialService alone; " + component
                        + " holding a digest or a random source puts every invariant back into one blast radius")
                .check(PRODUCTION);
    }

    private static Class<?> load(String simpleName) {
        try {
            return Class.forName("com.morpheus.api.remote." + simpleName);
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("com.morpheus.api.remote." + simpleName + " must exist", missing);
        }
    }

    private static String read(String fileName) throws IOException {
        return ProductionSources.readMain("morpheus-api", fileName.substring(0, fileName.length() - ".java".length()));
    }
}
