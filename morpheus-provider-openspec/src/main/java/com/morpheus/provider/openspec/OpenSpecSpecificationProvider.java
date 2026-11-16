package com.morpheus.provider.openspec;

import com.morpheus.application.provider.SpecificationProvider;
import com.morpheus.application.files.SafeWorkspaceFileResolver;
import com.morpheus.application.read.ProviderIngestionBudget;
import com.morpheus.application.read.ProviderIngestionLimitException;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import com.morpheus.domain.provider.ProviderCapability;
import com.morpheus.domain.provider.ProviderCapabilitySet;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.provider.ProviderProbeResult;
import com.morpheus.domain.provider.ProviderProbeStatus;
import com.morpheus.domain.source.SourceLocator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only M1 provider probe for OpenSpec projects using the {@code spec-driven} schema, or a project schema whose
 * definition declares that it derives from it (ADR-0169).
 */
public final class OpenSpecSpecificationProvider implements SpecificationProvider {
    public static final ProviderId ID = new ProviderId("openspec");
    public static final String PROVIDER_VERSION = "m1-v1";
    public static final String SUPPORTED_SCHEMA = "spec-driven";
    /** The directories of a workspace this provider reads, relative to the workspace root. */
    public static final List<Path> SOURCE_DIRECTORIES = List.of(Path.of("openspec"));

    private static final Pattern SCHEMA_PATTERN = scalarDeclaration("schema");
    private static final Pattern DERIVES_PATTERN = scalarDeclaration("derives");
    /**
     * The longest schema name a refusal repeats. The text of a refused read lists every unread category before this
     * message, and a boundary relays it whole or not at all under its length bound.
     */
    static final int MAX_NAMED_SCHEMA_LENGTH = 32;

    /** A schema name is one path segment: it names the directory of its definition under {@code openspec/schemas}. */
    private static final Pattern SCHEMA_NAME = Pattern.compile("[A-Za-z0-9]([A-Za-z0-9._-]*[A-Za-z0-9_-])?");
    private static final SourceLocator CONFIG_LOCATOR = SourceLocator.file("openspec/config.yaml");

    @Override
    public ProviderId id() {
        return ID;
    }

    @Override
    public String version() {
        return PROVIDER_VERSION;
    }

    @Override
    public boolean remote() {
        return false;
    }

    @Override
    public ProviderProbeResult probe(Path workspaceRoot) {
        return probe(workspaceRoot, null);
    }

    ProviderProbeResult probe(Path workspaceRoot, ProviderIngestionBudget.Session budget) {
        Path root = workspaceRoot.toAbsolutePath().normalize();
        Path openspecRoot = root.resolve("openspec");
        Path config = openspecRoot.resolve("config.yaml");

        if (!Files.isDirectory(openspecRoot) || !Files.exists(config)) {
            return result(
                    ProviderProbeStatus.UNSUPPORTED,
                    Optional.empty(),
                    Optional.empty(),
                    ProviderCapabilitySet.of(),
                    List.of());
        }

        if (!Files.isRegularFile(config) || !Files.isReadable(config)) {
            return invalid(config, "OpenSpec config.yaml is not a readable regular file.");
        }

        final Optional<String> schema;
        try {
            schema = declaredValue(SCHEMA_PATTERN, readDocument(root, Path.of("openspec/config.yaml"), budget));
        } catch (ProviderIngestionLimitException limit) {
            throw limit;
        } catch (IOException | IllegalArgumentException exception) {
            return invalid(config, "OpenSpec config.yaml could not be read.");
        }

        if (schema.isEmpty()) {
            return invalid(config, "OpenSpec config.yaml does not declare a schema.");
        }

        String declaredSchema = schema.orElseThrow();
        if (!SUPPORTED_SCHEMA.equals(declaredSchema)) {
            Optional<ProviderProbeResult> refusal = refuseUnlessDerivedFromSupportedSchema(root, declaredSchema, budget);
            if (refusal.isPresent()) {
                return refusal.orElseThrow();
            }
        }

        return result(
                ProviderProbeStatus.SUPPORTED,
                schema,
                Optional.of(CONFIG_LOCATOR),
                detectCapabilities(openspecRoot),
                List.of());
    }

    /** Reads a workspace document through the session's budget, or under the default document bound without one. */
    private static String readDocument(Path root, Path relativePath, ProviderIngestionBudget.Session budget)
            throws IOException {
        return budget == null
                ? SafeWorkspaceFileResolver.rootedAt(root).readUtf8(
                        relativePath, Math.toIntExact(ProviderIngestionBudget.DEFAULT.maxDocumentBytes()))
                : budget.readDocument(relativePath);
    }

    /**
     * A project schema other than {@link #SUPPORTED_SCHEMA} is read as that schema only when its definition,
     * {@code openspec/schemas/<schema>/schema.yaml}, declares {@code derives: spec-driven}; one level, one parent.
     * Empty when the declaration holds, otherwise the named refusal.
     */
    private Optional<ProviderProbeResult> refuseUnlessDerivedFromSupportedSchema(
            Path root, String schema, ProviderIngestionBudget.Session budget) {
        if (!isSchemaName(schema)) {
            return Optional.of(unsupportedSchema(schema, SchemaRefusal.INVALID_NAME, Optional.empty()));
        }
        String relativeDefinition = definitionOf(schema);
        Path definition = Path.of(relativeDefinition);
        if (!Files.exists(root.resolve(definition), LinkOption.NOFOLLOW_LINKS)) {
            return Optional.of(unsupportedSchema(schema, SchemaRefusal.DEFINITION_MISSING, Optional.empty()));
        }

        final Optional<String> parent;
        try {
            parent = declaredValue(DERIVES_PATTERN, readDocument(root, definition, budget));
        } catch (ProviderIngestionLimitException limit) {
            throw limit;
        } catch (IOException | IllegalArgumentException exception) {
            return Optional.of(invalidDefinition(schema, relativeDefinition));
        }

        if (parent.isEmpty()) {
            return Optional.of(unsupportedSchema(schema, SchemaRefusal.NO_DECLARED_PARENT, Optional.empty()));
        }
        if (!SUPPORTED_SCHEMA.equals(parent.orElseThrow())) {
            return Optional.of(unsupportedSchema(schema, SchemaRefusal.UNSUPPORTED_PARENT, parent));
        }
        return Optional.empty();
    }

    private static boolean isSchemaName(String schema) {
        return SCHEMA_NAME.matcher(schema).matches() && !schema.contains("..");
    }

    private static String definitionOf(String schema) {
        return "openspec/schemas/" + schema + "/schema.yaml";
    }

    /** Why a project schema is refused: the code its diagnostic carries as {@code reason}. */
    private enum SchemaRefusal {
        INVALID_NAME("invalid-schema-name"),
        DEFINITION_MISSING("schema-definition-missing"),
        NO_DECLARED_PARENT("no-declared-parent"),
        UNSUPPORTED_PARENT("unsupported-parent");

        private final String reason;

        SchemaRefusal(String reason) {
            this.reason = reason;
        }
    }

    /**
     * What the refusal tells the operator: the definition file the probe expects and the declaration that makes a
     * project schema readable. The declared name is repeated only when it is a schema name of at most
     * {@link #MAX_NAMED_SCHEMA_LENGTH} characters, and a declared parent never is: both are text of the workspace, the
     * message is relayed to callers outside the machine, and every declared value is in the details.
     */
    private static String refusalMessage(String schema, SchemaRefusal refusal) {
        boolean named = isSchemaName(schema) && schema.length() <= MAX_NAMED_SCHEMA_LENGTH;
        String subject = named ? "OpenSpec schema '" + schema + "'" : "The declared OpenSpec schema";
        String definition = definitionOf(named ? schema : "<schema>");
        String declaration = "'derives: " + SUPPORTED_SCHEMA + "'";
        String remedy = switch (refusal) {
            case INVALID_NAME -> "its name is not a schema name; " + definition + " must exist and declare " + declaration;
            case DEFINITION_MISSING -> definition + " must exist and declare " + declaration;
            case NO_DECLARED_PARENT -> definition + " must declare " + declaration;
            case UNSUPPORTED_PARENT -> definition + " declares another parent; only " + declaration + " is read";
        };
        return subject + " is not supported: " + remedy + ".";
    }

    private ProviderProbeResult unsupportedSchema(String schema, SchemaRefusal refusal, Optional<String> declaredParent) {
        Map<String, String> details = new TreeMap<>(Map.of(
                "provider", ID.value(),
                "schema", schema,
                "supportedSchema", SUPPORTED_SCHEMA,
                "reason", refusal.reason));
        declaredParent.ifPresent(parent -> details.put("declaredParent", parent));
        Diagnostic diagnostic = Diagnostic.error(
                DiagnosticCode.UNSUPPORTED_PROVIDER_SCHEMA,
                refusalMessage(schema, refusal),
                details);
        return result(
                ProviderProbeStatus.UNSUPPORTED,
                Optional.of(schema),
                Optional.of(CONFIG_LOCATOR),
                ProviderCapabilitySet.of(),
                List.of(diagnostic));
    }

    private ProviderProbeResult invalidDefinition(String schema, String relativeDefinition) {
        Diagnostic diagnostic = new Diagnostic(
                DiagnosticCode.INVALID_SOURCE,
                DiagnosticSeverity.ERROR,
                "OpenSpec schema definition is not a readable regular file.",
                Map.of("provider", ID.value(), "schema", schema),
                Optional.of(relativeDefinition));
        return result(
                ProviderProbeStatus.INVALID,
                Optional.of(schema),
                Optional.of(SourceLocator.file(relativeDefinition)),
                ProviderCapabilitySet.of(),
                List.of(diagnostic));
    }

    private ProviderCapabilitySet detectCapabilities(Path openspecRoot) {
        Set<ProviderCapability> capabilities = EnumSet.of(ProviderCapability.DISCOVER_PROJECT);

        if (Files.isDirectory(openspecRoot.resolve("specs"))) {
            capabilities.add(ProviderCapability.READ_CURRENT_SPECIFICATIONS);
            capabilities.add(ProviderCapability.READ_REQUIREMENTS);
            capabilities.add(ProviderCapability.READ_SCENARIOS);
        }

        Path changesRoot = openspecRoot.resolve("changes");
        if (Files.isDirectory(changesRoot)) {
            capabilities.add(ProviderCapability.READ_CHANGES);
            capabilities.add(ProviderCapability.READ_DESIGN_DECISIONS);
            capabilities.add(ProviderCapability.READ_IMPLEMENTATION_TASKS);
        }

        if (Files.isDirectory(changesRoot.resolve("archive"))) {
            capabilities.add(ProviderCapability.READ_HISTORY);
            capabilities.add(ProviderCapability.READ_ARCHIVES);
        }

        return ProviderCapabilitySet.copyOf(capabilities);
    }

    /** A top-level {@code key: value} line whose value is a plain, double- or single-quoted YAML scalar. */
    private static Pattern scalarDeclaration(String key) {
        return Pattern.compile(
                "^" + key + ":\\s*(?:"
                + "\"([^\"#\\s]+)\"|'([^'#\\s]+)'|([^#\\s\"']+))(?:\\s|#|$)");
    }

    private static Optional<String> declaredValue(Pattern declaration, String text) {
        return text.lines()
                .map(declaration::matcher)
                .filter(Matcher::find)
                .map(OpenSpecSpecificationProvider::scalarValue)
                .findFirst();
    }

    /** The declared value, unquoted when written as a double- or single-quoted YAML scalar. */
    private static String scalarValue(Matcher matcher) {
        String quoted = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        return quoted != null ? quoted : matcher.group(3);
    }

    private ProviderProbeResult invalid(Path source, String message) {
        Diagnostic diagnostic = new Diagnostic(
                DiagnosticCode.INVALID_SOURCE,
                DiagnosticSeverity.ERROR,
                message,
                Map.of("provider", ID.value()),
                Optional.of(source.toString()));
        return result(
                ProviderProbeStatus.INVALID,
                Optional.empty(),
                Optional.of(CONFIG_LOCATOR),
                ProviderCapabilitySet.of(),
                List.of(diagnostic));
    }

    private ProviderProbeResult result(
            ProviderProbeStatus status,
            Optional<String> schema,
            Optional<SourceLocator> sourceLocator,
            ProviderCapabilitySet capabilities,
            List<Diagnostic> diagnostics) {
        return new ProviderProbeResult(
                ID,
                PROVIDER_VERSION,
                status,
                schema,
                Optional.empty(),
                sourceLocator,
                capabilities,
                false,
                diagnostics);
    }
}
