package com.morpheus.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morpheus.application.composition.CompositionEntityType;
import com.morpheus.application.composition.CompositionResolution;
import com.morpheus.application.context.TechnicalContextOptions;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationResultState;
import com.morpheus.application.orchestration.ChangeTransitionEvaluationState;
import com.morpheus.application.policy.PolicyConfiguration;
import com.morpheus.application.policy.PolicyRule;
import com.morpheus.application.portfolio.PortfolioTraversalDirection;
import com.morpheus.application.query.export.QueryExportFormat;
import com.morpheus.application.reasoning.ReasoningContracts;
import com.morpheus.domain.acceptance.VerificationStatus;
import com.morpheus.domain.change.lifecycle.ChangeAbandonmentReason;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.constraint.ConstraintApplicability;
import com.morpheus.domain.constraint.ConstraintBlockingMode;
import com.morpheus.domain.constraint.ConstraintEvaluationState;
import com.morpheus.domain.constraint.ConstraintSatisfaction;
import com.morpheus.domain.constraint.ConstraintSeverity;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A published contract names every value the code can emit, and only those (API-5).
 *
 * <p>An OpenAPI {@code enum} that mirrors a Java enum is a hand-maintained copy: nothing links the two, so the
 * copy is correct only until the enum grows. {@code CompositionConflict.entityType} listed four values while
 * {@code CompositionEntityType} published ten, and the schema next to it had been corrected in the same commit --
 * a validating client or a generated typed client rejects the response the server legitimately sends. The copy is
 * tolerated only because this class holds it: {@link #TABLE} declares, per schema property, the Java enum it mirrors,
 * and the sets must be equal <em>in both directions</em> (a value only Java knows is a response the contract
 * forbids; a value only the YAML knows is a request the code refuses).</p>
 *
 * <p>Three halves keep the guard from asserting more than it checks:</p>
 * <ol>
 *   <li>every table row resolves to a non-empty YAML enum and equals its Java set;</li>
 *   <li>every {@code enum:} key -- flow or block style -- written in the six {@code docs/openapi/*.yaml} files is either a table row or a
 *   declared {@link #EXEMPT} one, so a new enum is refused until someone classifies it, and the table cannot
 *   quietly be empty or stale;</li>
 *   <li>the locator itself is proved on a synthetic document: a value removed from the YAML, or a schema path that
 *   resolves nothing, fails with a message that says so.</li>
 * </ol>
 *
 * <p>What it does not cover: the {@link #EXEMPT} enums, which have no Java enum to compare with ({@code ScopeKind}
 * is derived from sealed {@code PolicyScope} records, {@code Error.code} from string literals in {@code ApiFailure});
 * enumerations spelled {@code const}, {@code oneOf} or in prose; and an enum-typed property that is written without
 * an {@code enum:} list. A block-style {@code enum:} is counted, so it must be classified, but a table row on it
 * fails to resolve until the locator learns the form. The read is textual by design (ADR-0103): no YAML parser dependency is added, and the
 * proposition -- these two lists of names are equal -- is about the text of the contract.</p>
 */
class PublishedEnumsMatchJavaEnumsTest {

    private record Locator(String file, String schema, String property) {
        String path() {
            return file + "#" + schema + (property == null ? "" : "." + property);
        }
    }

    private record Row(Locator at, Set<String> javaValues) {
    }

    private record Exemption(Locator at, String reason) {
    }

    private static final String V1 = "morpheus-v1.yaml";
    private static final String M23 = "morpheus-v1-portfolio-m23.yaml";
    private static final String M24 = "morpheus-v1-query-m24.yaml";
    private static final String M25 = "morpheus-v1-policy-m25.yaml";
    private static final String M26 = "morpheus-v1-remote-m26.yaml";
    private static final String M27 = "morpheus-v1-reasoning-m27.yaml";
    private static final List<String> FILES = List.of(V1, M23, M24, M25, M26, M27);

    private static final List<Row> TABLE = List.of(
            row(V1, "CompositionConflict", "entityType", CompositionEntityType.class),
            row(V1, "CompositionConflict", "resolution", CompositionResolution.class),
            row(V1, "VerificationStatus", null, VerificationStatus.class),
            row(V1, "ConstraintApplicability", null, ConstraintApplicability.class),
            row(V1, "ConstraintSeverity", null, ConstraintSeverity.class),
            row(V1, "ConstraintSatisfaction", null, ConstraintSatisfaction.class),
            row(V1, "ConstraintBlockingMode", null, ConstraintBlockingMode.class),
            row(V1, "ConstraintEvaluationState", null, ConstraintEvaluationState.class),
            row(V1, "ChangeTransitionEvaluationState", null, ChangeTransitionEvaluationState.class),
            row(V1, "LifecycleState", null, ChangeLifecycleState.class),
            row(V1, "AbandonmentReason", null, ChangeAbandonmentReason.class),
            row(V1, "LifecycleMutationResultState", null, ChangeLifecycleMutationResultState.class),
            row(V1, "DiagnosticCode", null, DiagnosticCode.class),
            row(V1, "DiagnosticSeverity", null, DiagnosticSeverity.class),
            new Row(new Locator(V1, "AugmentedContextRequest", "requestedSources"), TechnicalContextOptions.ALLOWED_SOURCES),
            row(M23, "TraversalRequest", "direction", PortfolioTraversalDirection.class),
            row(M24, "ExportFormat", null, QueryExportFormat.class),
            row(M25, "PolicyRule", "kind", PolicyRule.Kind.class),
            row(M25, "PolicyRule", "severity", PolicyRule.Severity.class),
            row(M25, "PolicyRule", "qualityMetric", PolicyRule.QualityMetric.class),
            row(M25, "PolicyRule", "comparison", PolicyRule.Comparison.class),
            row(M25, "OverrideRequest", "mode", PolicyConfiguration.OverrideMode.class),
            row(M27, "Evidence", "kind", ReasoningContracts.EvidenceKind.class),
            row(M27, "Confidence", "band", ReasoningContracts.ConfidenceBand.class),
            row(M27, "Claim", "kind", ReasoningContracts.ClaimKind.class),
            row(M27, "AdapterExecution", "status", ReasoningContracts.AdapterStatus.class));

    private static final List<Exemption> EXEMPT = List.of(
            new Exemption(new Locator(V1, "Error", "code"), "string literals in ApiFailure, no Java enum"),
            new Exemption(new Locator(M24, "ScopeKind", null), "derived from the sealed PolicyScope records, no Java enum"),
            new Exemption(new Locator(M25, "ScopeKind", null), "derived from the sealed PolicyScope records, no Java enum"));

    private static final Pattern ENUM_KEY = Pattern.compile("\\benum:");
    private static final Pattern ENUM_LIST = Pattern.compile("\\benum:\\s*\\[([^\\]]*)\\]");

    @Test
    void everyPublishedEnumEqualsItsJavaEnumInBothDirections() throws IOException {
        assertFalse(TABLE.isEmpty(), "the table is empty: the guard would pass without comparing anything");
        List<String> failures = new ArrayList<>();
        for (Row row : TABLE) {
            Set<String> published = locate(read(row.at().file()), row.at().schema(), row.at().property(), row.at().path());
            Set<String> missingFromContract = difference(row.javaValues(), published);
            Set<String> unknownToCode = difference(published, row.javaValues());
            if (!missingFromContract.isEmpty() || !unknownToCode.isEmpty()) {
                failures.add(row.at().path() + ": published by the code but absent from the contract " + missingFromContract
                        + "; in the contract but unknown to the code " + unknownToCode);
            }
        }
        assertTrue(failures.isEmpty(), "published enum drifted from its Java enum:\n" + String.join("\n", failures));
    }

    @Test
    void everyEnumWrittenInTheSixContractsIsEitherGuardedOrDeclaredExempt() throws IOException {
        List<String> failures = new ArrayList<>();
        try (Stream<Path> contracts = Files.list(repoRoot().resolve("docs/openapi"))) {
            List<String> onDisk = contracts.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".yaml")).sorted().toList();
            assertEquals(FILES.stream().sorted().toList(), onDisk,
                    "docs/openapi holds a contract this guard does not read: add it to FILES");
        }
        for (String file : FILES) {
            int written = countEnumLists(read(file));
            long classified = TABLE.stream().filter(row -> row.at().file().equals(file)).count()
                    + EXEMPT.stream().filter(exemption -> exemption.at().file().equals(file)).count();
            if (written != classified) {
                failures.add(file + " writes " + written + " enum list(s) but the table and the exemptions classify "
                        + classified + ": add the new enum to TABLE, or to EXEMPT with the reason it has no Java enum");
            }
        }
        for (Exemption exemption : EXEMPT) {
            Locator at = exemption.at();
            assertFalse(locate(read(at.file()), at.schema(), at.property(), at.path()).isEmpty(),
                    at.path() + " is declared exempt but no longer exists");
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void theLocatorRefusesAnEmptyResolutionAndSeesARemovedValue() {
        String document = """
                components:
                  schemas:
                    Colour:
                      type: string
                      enum: [RED, GREEN]
                    Paint:
                      properties:
                        tint: {type: string, enum: [RED, GREEN, BLUE]}
                        finish:
                          type: [string, 'null']
                          enum: [MATT, null]
                """;
        assertEquals(Set.of("RED", "GREEN"), locate(document, "Colour", null, "synthetic#Colour"));
        assertEquals(Set.of("RED", "GREEN", "BLUE"), locate(document, "Paint", "tint", "synthetic#Paint.tint"));
        assertEquals(Set.of("MATT"), locate(document, "Paint", "finish", "synthetic#Paint.finish"));

        IllegalStateException unknownSchema = assertThrows(IllegalStateException.class,
                () -> locate(document, "Shade", null, "synthetic#Shade"));
        assertTrue(unknownSchema.getMessage().contains("resolves no schema"), unknownSchema.getMessage());
        IllegalStateException unknownProperty = assertThrows(IllegalStateException.class,
                () -> locate(document, "Paint", "gloss", "synthetic#Paint.gloss"));
        assertTrue(unknownProperty.getMessage().contains("resolves no property"), unknownProperty.getMessage());
        IllegalStateException noEnum = assertThrows(IllegalStateException.class,
                () -> locate(document, "Paint", null, "synthetic#Paint"));
        assertTrue(noEnum.getMessage().contains("resolves no enum list"), noEnum.getMessage());

        Set<String> java = Set.of("RED", "GREEN", "BLUE");
        Set<String> published = locate(document.replace("[RED, GREEN, BLUE]", "[RED, GREEN]"), "Paint", "tint",
                "synthetic#Paint.tint");
        assertEquals(Set.of("BLUE"), difference(java, published), "a value dropped from the YAML must be seen");
    }

    private static Row row(String file, String schema, String property, Class<? extends Enum<?>> enumType) {
        Set<String> names = new TreeSet<>();
        Arrays.stream(enumType.getEnumConstants()).forEach(constant -> names.add(constant.name()));
        return new Row(new Locator(file, schema, property), names);
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new TreeSet<>(left);
        result.removeAll(right);
        return result;
    }

    private static int countEnumLists(String document) {
        Matcher matcher = ENUM_KEY.matcher(document);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * The values of the one flow-style enum list under {@code schema} (and {@code property}); a nullable enum's
     * {@code null} entry is not a value and is dropped. It fails, naming {@code path}, rather than return an empty
     * set: an empty resolution would make every comparison against it vacuous.
     */
    private static Set<String> locate(String document, String schema, String property, String path) {
        List<String> lines = document.lines().toList();
        int schemasAt = lines.indexOf("  schemas:");
        if (schemasAt < 0) {
            throw new IllegalStateException(path + " resolves no schema: the document has no components.schemas");
        }
        List<String> block = blockUnder(lines, schemasAt + 1, "    " + schema + ":", 4);
        if (block == null) {
            throw new IllegalStateException(path + " resolves no schema: " + schema);
        }
        int enumIndent = 6;
        if (property != null) {
            block = blockUnder(block, 0, "        " + property + ":", 8);
            if (block == null) {
                throw new IllegalStateException(path + " resolves no property: " + property);
            }
            enumIndent = -1;
        }
        List<String> found = new ArrayList<>();
        for (String line : block) {
            Matcher matcher = ENUM_LIST.matcher(line);
            if (matcher.find() && (enumIndent < 0 || indentOf(line) == enumIndent)) {
                found.add(matcher.group(1));
            }
        }
        if (found.size() != 1) {
            throw new IllegalStateException(path + " resolves no enum list (or several): found " + found.size());
        }
        Set<String> values = new TreeSet<>();
        for (String token : found.get(0).split(",")) {
            String value = token.trim();
            if (!value.isEmpty() && !value.equals("null")) {
                values.add(value);
            }
        }
        return values;
    }

    /**
     * The block opened by {@code header}, alone on its line or followed by an inline value, ending at the next line
     * indented {@code indent} or less: the indentation is what keeps a same-named property of a neighbouring schema
     * out of the block.
     */
    private static List<String> blockUnder(List<String> lines, int from, String header, int indent) {
        for (int index = from; index < lines.size(); index++) {
            String candidate = lines.get(index).stripTrailing();
            if (!candidate.equals(header) && !candidate.startsWith(header + " ")) {
                continue;
            }
            List<String> block = new ArrayList<>();
            block.add(lines.get(index));
            for (int next = index + 1; next < lines.size(); next++) {
                String line = lines.get(next);
                if (!line.isBlank() && indentOf(line) <= indent) {
                    break;
                }
                block.add(line);
            }
            return block;
        }
        return null;
    }

    private static int indentOf(String line) {
        int indent = 0;
        while (indent < line.length() && line.charAt(indent) == ' ') {
            indent++;
        }
        return indent;
    }

    private static String read(String file) throws IOException {
        return Files.readString(repoRoot().resolve("docs/openapi").resolve(file));
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
