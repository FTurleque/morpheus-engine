package com.morpheus.provider.openspec;

import com.morpheus.application.files.SafeWorkspaceFileResolver;
import com.morpheus.application.read.NormalizedProjectContent;
import com.morpheus.application.read.ProviderIngestionBudget;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderCapability;
import com.morpheus.domain.provider.ProviderCapabilitySet;
import com.morpheus.domain.provider.ProviderProbeResult;
import com.morpheus.domain.provider.ProviderProbeStatus;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.scenario.Scenario;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Measures, on a real OpenSpec workspace, what its evidence costs the ingestion budget: the qualification of the default
 * evidence bound rests on it (archived openspec change {@code 2026-11-16-qualify-the-evidence-budget-on-real-corpora}, task 1.1).
 *
 * <p>A measuring tool, not a test: it reads the workspaces given as arguments, read-only, and prints one Markdown table.
 * It calls the three readers directly with a probe result it builds itself, so a workspace whose declared schema the
 * probe does not accept is measured all the same, and with an evidence bound out of reach, so the total is the whole
 * read and not the value at which a refusal stopped it. Both counts are computed from the evidence the readers return,
 * so they do not depend on the rule the session applies:</p>
 * <ul>
 *   <li><b>each excerpt</b>: the bytes of every cited excerpt, its lines joined by one newline, as many times as it is
 *   cited;</li>
 *   <li><b>each line once</b>: the bytes of every line some excerpt cites, plus one for its line end, once per file.</li>
 * </ul>
 * <p>Per requirement of the current specifications, it reports the bytes the requirement and its scenarios cite as
 * excerpts, and their ratio to the text of the requirement block they come from.</p>
 */
final class OpenSpecEvidenceMeasurement {
    private OpenSpecEvidenceMeasurement() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length == 0) {
            throw new IllegalArgumentException("usage: OpenSpecEvidenceMeasurement <workspace root>...");
        }
        System.out.println("| corpus | specs (o) | évidence par extrait (o) | évidence une fois par ligne (o) "
                + "| dont changements actifs (o, nombre) | compté par la session (o) | exigences "
                + "| médiane par exigence (o) | max par exigence (o) | rapport médian au texte "
                + "| rapport max au texte | groupes en échec |");
        System.out.println("|---|---|---|---|---|---|---|---|---|---|---|---|");
        for (String argument : arguments) {
            System.out.println(measure(Path.of(argument).toAbsolutePath().normalize()).row());
        }
    }

    static Measurement measure(Path root) throws IOException {
        ProviderIngestionBudget defaults = ProviderIngestionBudget.DEFAULT;
        ProviderIngestionBudget.Session session = new ProviderIngestionBudget(
                defaults.maxDocumentBytes(),
                defaults.maxFiles(),
                defaults.maxAggregateBytes(),
                defaults.maxLines(),
                defaults.maxBlocks(),
                defaults.maxEntities(),
                Integer.MAX_VALUE)
                .open(SafeWorkspaceFileResolver.rootedAt(root));
        session.readDocument(Path.of("openspec/config.yaml"));
        ProviderProbeResult probe = new ProviderProbeResult(
                OpenSpecSpecificationProvider.ID,
                OpenSpecSpecificationProvider.PROVIDER_VERSION,
                ProviderProbeStatus.SUPPORTED,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                ProviderCapabilitySet.of(
                        ProviderCapability.DISCOVER_PROJECT,
                        ProviderCapability.READ_CURRENT_SPECIFICATIONS,
                        ProviderCapability.READ_REQUIREMENTS,
                        ProviderCapability.READ_SCENARIOS,
                        ProviderCapability.READ_CHANGES,
                        ProviderCapability.READ_DESIGN_DECISIONS),
                false,
                List.of());
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        List<Evidence> evidence = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        List<Requirement> requirements = new ArrayList<>();
        List<Scenario> scenarios = new ArrayList<>();
        try {
            NormalizedProjectContent current = new OpenSpecCurrentSpecificationReader()
                    .read(root, projectId, OpenSpecEvidenceMeasurement::identity, session, probe);
            evidence.addAll(current.evidence());
            requirements.addAll(current.requirements());
            scenarios.addAll(current.scenarios());
        } catch (RuntimeException failure) {
            failures.add("current: " + failure.getMessage());
        }
        List<Path> changeRoots = OpenSpecWorkspaceListing.changeRoots(root, session);
        try {
            evidence.addAll(new OpenSpecChangeMetadataReader().readContained(
                    root, projectId, OpenSpecEvidenceMeasurement::identity, session, probe, changeRoots)
                    .content().evidence());
        } catch (RuntimeException failure) {
            failures.add("changes: " + failure.getMessage());
        }
        try {
            evidence.addAll(new OpenSpecRequirementDeltaReader().read(
                    root, OpenSpecEvidenceMeasurement::identity, session, probe, changeRoots).evidence());
        } catch (RuntimeException failure) {
            failures.add("deltas: " + failure.getMessage());
        }

        Map<String, List<String>> files = new TreeMap<>();
        Map<String, BitSet> cited = new TreeMap<>();
        Map<EvidenceId, Long> excerptBytes = new TreeMap<>();
        long eachExcerpt = 0;
        for (Evidence item : evidence) {
            String source = item.source().value();
            List<String> lines = files.computeIfAbsent(source, ignored -> lines(root.resolve(source)));
            int start = item.range().orElseThrow().startLine();
            int end = item.range().orElseThrow().endLine();
            long bytes = utf8Bytes(String.join("\n", lines.subList(start - 1, end)));
            excerptBytes.put(item.id(), bytes);
            eachExcerpt += bytes;
            cited.computeIfAbsent(source, ignored -> new BitSet()).set(start - 1, end);
        }
        long eachLineOnce = 0;
        long eachLineOnceInChanges = 0;
        for (Map.Entry<String, BitSet> entry : cited.entrySet()) {
            List<String> lines = files.get(entry.getKey());
            BitSet lineSet = entry.getValue();
            for (int index = lineSet.nextSetBit(0); index >= 0; index = lineSet.nextSetBit(index + 1)) {
                long bytes = utf8Bytes(lines.get(index)) + 1;
                eachLineOnce += bytes;
                if (entry.getKey().startsWith("openspec/changes/")) {
                    eachLineOnceInChanges += bytes;
                }
            }
        }

        Map<RequirementId, Long> scenarioBytes = new TreeMap<>();
        for (Scenario scenario : scenarios) {
            scenario.requirementId().ifPresent(id -> scenarioBytes.merge(
                    id, excerptBytes.get(scenario.provenance().evidenceId()), Long::sum));
        }
        List<Long> perRequirement = new ArrayList<>();
        List<Double> ratios = new ArrayList<>();
        for (Requirement requirement : requirements) {
            long block = excerptBytes.get(requirement.provenance().evidenceId());
            long charged = block + scenarioBytes.getOrDefault(requirement.id(), 0L);
            perRequirement.add(charged);
            ratios.add(block == 0 ? 0 : (double) charged / block);
        }
        perRequirement.sort(null);
        ratios.sort(null);

        return new Measurement(
                root.getFileName().toString(),
                specificationBytes(root.resolve("openspec/specs")),
                eachExcerpt,
                eachLineOnce,
                eachLineOnceInChanges,
                changeRoots.size(),
                session.evidenceBytes(),
                perRequirement.size(),
                median(perRequirement),
                perRequirement.isEmpty() ? 0 : perRequirement.getLast(),
                median(ratios),
                ratios.isEmpty() ? 0 : ratios.getLast(),
                failures);
    }

    record Measurement(
            String corpus,
            long specificationBytes,
            long eachExcerpt,
            long eachLineOnce,
            long eachLineOnceInChanges,
            int activeChanges,
            long countedBySession,
            int requirements,
            double medianPerRequirement,
            long maximumPerRequirement,
            double medianRatio,
            double maximumRatio,
            List<String> failures) {

        String row() {
            return String.join(" | ",
                    "| " + corpus,
                    Long.toString(specificationBytes),
                    Long.toString(eachExcerpt),
                    Long.toString(eachLineOnce),
                    eachLineOnceInChanges + " (" + activeChanges + ")",
                    Long.toString(countedBySession),
                    Integer.toString(requirements),
                    String.format(java.util.Locale.ROOT, "%.0f", medianPerRequirement),
                    Long.toString(maximumPerRequirement),
                    String.format(java.util.Locale.ROOT, "%.2f", medianRatio),
                    String.format(java.util.Locale.ROOT, "%.2f", maximumRatio),
                    failures.isEmpty() ? "aucun" : String.join("; ", failures)) + " |";
        }
    }

    private static DomainIdentity identity(
            com.morpheus.domain.provider.ProviderId provider, String entityType, String externalId) {
        return DomainIdentity.generate();
    }

    private static List<String> lines(Path file) {
        try {
            return Files.readString(file).lines().toList();
        } catch (IOException failure) {
            throw new IllegalStateException("cannot read " + file, failure);
        }
    }

    private static long specificationBytes(Path specsRoot) throws IOException {
        try (Stream<Path> paths = Files.walk(specsRoot)) {
            long total = 0;
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().equals("spec.md")).toList()) {
                total += Files.size(path);
            }
            return total;
        }
    }

    private static double median(List<? extends Number> sorted) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1
                ? sorted.get(middle).doubleValue()
                : (sorted.get(middle - 1).doubleValue() + sorted.get(middle).doubleValue()) / 2;
    }

    private static long utf8Bytes(String text) {
        return text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }
}
