package com.morpheus.application.quality.compact;

import com.morpheus.application.quality.QualityFinding;
import com.morpheus.application.quality.QualityReport;
import com.morpheus.application.quality.QualityReportMetrics;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.query.compact.CompactQueryTypes.QueryMetadata;
import com.morpheus.application.query.compact.CompactQueryTypes.SnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Projects aggregate M6 quality reports into compact deterministic JSON. */
public final class CompactQualityReportService {
    private static final int SCHEMA_VERSION = 1;
    private static final String OPERATION = "get_quality_report";

    private final CanonicalJsonSerializer serializer;

    public CompactQualityReportService() {
        this(new CanonicalJsonSerializer());
    }

    public CompactQualityReportService(CanonicalJsonSerializer serializer) {
        this.serializer = Objects.requireNonNull(serializer, "serializer");
    }

    public CompactQualityReportView view(QualityReport report) {
        Objects.requireNonNull(report, "report");
        return new CompactQualityReportView(
                new QueryMetadata(SCHEMA_VERSION, OPERATION),
                snapshot(report.snapshot()),
                metrics(report),
                report.findings().stream().map(this::finding).toList());
    }

    public String toJson(QualityReport report) {
        return serializer.toJson(view(report));
    }

    public byte[] toUtf8(QualityReport report) {
        return serializer.toUtf8(view(report));
    }

    private SnapshotMetadata snapshot(KnowledgeSnapshotMetadata snapshot) {
        return new SnapshotMetadata(
                snapshot.id().toString(),
                snapshot.projectId().toString(),
                snapshot.state().name(),
                snapshot.predecessorId().map(Object::toString),
                snapshot.sourceRevision(),
                snapshot.createdAt().toString());
    }

    private CompactQualityReportView.MetricsView metrics(QualityReport report) {
        QualityReportMetrics metrics = report.metrics();
        return new CompactQualityReportView.MetricsView(
                metrics.totalFindings(),
                metrics.totalRequirements(),
                metrics.linkedRequirements(),
                metrics.orphanRequirements(),
                metrics.requirementCoverageRatio(),
                metrics.totalTasks(),
                metrics.coveredTasks(),
                metrics.uncoveredTasks(),
                metrics.taskCoverageRatio(),
                metrics.acceptanceCoverageStatus().name(),
                report.lifecycleAggregationStatus().name(),
                metrics.totalChanges(),
                metrics.totalDesignDecisions(),
                metrics.totalExternalReferences(),
                enumCounts(metrics.findingsByCode()),
                enumCounts(metrics.findingsBySeverity()),
                enumCounts(metrics.findingsByEvidenceKind()));
    }

    private CompactQualityReportView.FindingView finding(QualityFinding finding) {
        return new CompactQualityReportView.FindingView(
                finding.code().name(),
                finding.severity().name(),
                finding.evidenceKind().name(),
                finding.subject().kind().name(),
                finding.subject().identity().toString(),
                finding.message(),
                finding.details(),
                finding.confidence(),
                finding.evidenceIds().stream().map(Object::toString).toList());
    }

    private Map<String, Integer> enumCounts(Map<? extends Enum<?>, Integer> counts) {
        TreeMap<String, Integer> result = new TreeMap<>();
        counts.forEach((key, value) -> result.put(key.name(), value));
        return result;
    }
}
