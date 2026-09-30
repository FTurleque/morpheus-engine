package com.morpheus.architecture;

import com.morpheus.application.ingestion.BoundedDiagnostics;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sync answer's diagnostics are a copy of code facts in {@code docs/openapi/morpheus-v1.yaml}: the bound, the shape
 * of the list and of one item, and the detail keys the remote projection relays. A copy is tolerated only while this
 * class holds it (PRV-2). The enum values of {@code DiagnosticCode} and {@code DiagnosticSeverity} are held by
 * {@link PublishedEnumsMatchJavaEnumsTest}; the keys of {@code SyncResult} are held against a real answer by
 * {@code MorpheusApiProjectSyncIntegrationTest}.
 *
 * <p>It also holds the one choice the HTTP adapter makes: a remote WRITE caller reaches the sync route, so no class of
 * {@code com.morpheus.api} may relay the local projection, and the sync service relays the remote one. These are
 * bytecode rules (ADR-0103: the intention is "no call", which a text search would only approximate).</p>
 *
 * <p>What it does not cover: that the remote projection is applied to the value the route actually serializes rather
 * than computed and dropped — the integration test proves it, with a title the location predicate refuses; and
 * adapters outside {@code com.morpheus.api}: the CLI relays the local projection by design.</p>
 */
class SyncDiagnosticsContractTest {
    private final JavaClasses classes = new ClassFileImporter().importPackages("com.morpheus");

    @Test
    void noHttpAdapterClassRelaysTheLocalProjection() {
        noClasses()
                .that().resideInAPackage("com.morpheus.api..")
                .should().callMethod(BoundedDiagnostics.class, "local", List.class)
                .because("POST /projects/{id}/sync is reachable by a remote WRITE caller; HTTP relays the allowlisted "
                        + "projection, never the diagnostics as produced")
                .check(classes);
    }

    @Test
    void theHttpSyncServiceRelaysTheRemoteProjection() {
        classes()
                .that().haveFullyQualifiedName("com.morpheus.api.MorpheusProjectSyncApiService")
                .should().callMethod(BoundedDiagnostics.class, "remote", List.class)
                .because("the sync answer names what was skipped, through the remote-safe projection")
                .check(classes);
    }

    @Test
    void thePublishedBoundIsTheBoundTheCodeApplies() throws IOException {
        List<String> schema = schema("BoundedDiagnostics");
        assertTrue(schema.stream().anyMatch(line -> line.trim().equals("maxItems: " + BoundedDiagnostics.MAX_ITEMS)),
                "BoundedDiagnostics.items must publish maxItems: " + BoundedDiagnostics.MAX_ITEMS + "\n" + schema);

        List<Diagnostic> overflowing = new ArrayList<>();
        for (int index = 0; index <= BoundedDiagnostics.MAX_ITEMS; index++) {
            overflowing.add(Diagnostic.warning(DiagnosticCode.PARTIAL_INGESTION, "skipped", Map.of()));
        }
        String reason = BoundedDiagnostics.remote(overflowing).truncationReason().orElseThrow();
        String published = reason.replace(Integer.toString(BoundedDiagnostics.MAX_ITEMS), "<bound>");
        assertTrue(String.join("\n", schema).contains(published),
                "the published truncation reason must be the one the code writes: " + reason);

        String document = read();
        assertTrue(document.contains("diagnostics relays at most " + BoundedDiagnostics.MAX_ITEMS + " of them"),
                "the syncProject response must state the bound the code applies");
    }

    @Test
    void onePublishedItemHasExactlyTheComponentsOfADiagnostic() throws IOException {
        assertEquals(components(Diagnostic.class), required("Diagnostic"));
    }

    @Test
    void thePublishedListHasExactlyTheComponentsOfBoundedDiagnostics() throws IOException {
        assertEquals(components(BoundedDiagnostics.class), required("BoundedDiagnostics"));
    }

    private static List<String> components(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    private static List<String> required(String name) throws IOException {
        String required = schema(name).stream()
                .filter(line -> line.trim().startsWith("required: ["))
                .findFirst()
                .orElseThrow(() -> new AssertionError(name + " publishes no required list"));
        return Arrays.stream(required.substring(required.indexOf('[') + 1, required.indexOf(']')).split(","))
                .map(String::trim)
                .toList();
    }

    @Test
    void thePublishedDetailKeysAreTheKeysTheRemoteProjectionRelays() throws IOException {
        String details = String.join(" ", schema("Diagnostic"));
        int open = details.indexOf("Only allowlisted keys (");
        assertTrue(open >= 0, "Diagnostic.details must list its allowlisted keys");
        String list = details.substring(open + "Only allowlisted keys (".length(), details.indexOf(')', open));
        Set<String> published = new TreeSet<>();
        for (String key : list.split(",")) {
            published.add(key.trim());
        }
        assertEquals(new TreeSet<>(BoundedDiagnostics.REMOTE_DETAIL_KEYS), published);
    }

    private static List<String> schema(String name) throws IOException {
        List<String> lines = read().lines().toList();
        String header = "    " + name + ":";
        int start = lines.indexOf(header);
        assertTrue(start >= 0, "docs/openapi/morpheus-v1.yaml has no schema " + name);
        List<String> block = new ArrayList<>();
        for (int index = start + 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (!line.isBlank() && !line.startsWith("      ")) {
                break;
            }
            block.add(line);
        }
        return block;
    }

    private static String read() throws IOException {
        return Files.readString(repoRoot().resolve("docs/openapi/morpheus-v1.yaml"));
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.exists(current.resolve("contracts/public-surfaces.tsv"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("repository root (contracts/public-surfaces.tsv) not found");
        }
        return current;
    }
}
