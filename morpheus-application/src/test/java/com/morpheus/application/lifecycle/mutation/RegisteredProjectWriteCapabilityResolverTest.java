package com.morpheus.application.lifecycle.mutation;

import com.morpheus.application.provider.ProviderSelectionPolicy;
import com.morpheus.application.provider.SpecificationProvider;
import com.morpheus.application.provider.SpecificationProviderRegistry;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderCapability;
import com.morpheus.domain.provider.ProviderCapabilitySet;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.provider.ProviderProbeResult;
import com.morpheus.domain.provider.ProviderProbeStatus;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** WRITE_CHANGE is granted only when exactly one supported provider of the registered root declares it. */
class RegisteredProjectWriteCapabilityResolverTest {
    private final ProjectSpecificationId projectId = ProjectSpecificationId.generate();
    private final Projects projects = new Projects();

    @Test
    void anUnregisteredProjectIsDenied() {
        ChangeWriteCapabilityObservation observation = resolver(List.of()).resolve(projectId);

        assertFalse(observation.writeAllowed());
        assertEquals("Project is not registered", observation.reason());
    }

    @Test
    void aRootThatIsNotAFileCannotBeProbed() {
        projects.entry = Optional.of(new ProjectStoreEntry(projectId, new SourceLocator("https", "example.org/repo")));

        ChangeWriteCapabilityObservation observation = resolver(List.of(
                writer("openspec", ProviderProbeStatus.SUPPORTED))).resolve(projectId);

        assertFalse(observation.writeAllowed());
        assertEquals("WRITE_CHANGE capability cannot be probed for non-file project root: https",
                observation.reason());
    }

    @Test
    void noSupportedWriterMeansNoWriteCapability() {
        projects.entry = Optional.of(new ProjectStoreEntry(projectId, SourceLocator.file("workspace")));

        ChangeWriteCapabilityObservation observation = resolver(List.of(
                writer("unsupported", ProviderProbeStatus.UNSUPPORTED),
                new FixedProvider(new ProviderId("reader"), ProviderProbeStatus.SUPPORTED,
                        ProviderCapabilitySet.of(ProviderCapability.READ_CHANGES)))).resolve(projectId);

        assertFalse(observation.writeAllowed());
        assertEquals("No supported provider explicitly exposes WRITE_CHANGE for this project", observation.reason());
    }

    @Test
    void twoWritersRequireAnExplicitSelectionInsteadOfAGuess() {
        projects.entry = Optional.of(new ProjectStoreEntry(projectId, SourceLocator.file("workspace")));

        ChangeWriteCapabilityObservation observation = resolver(List.of(
                writer("alpha", ProviderProbeStatus.SUPPORTED),
                writer("beta", ProviderProbeStatus.SUPPORTED))).resolve(projectId);

        assertFalse(observation.writeAllowed());
        assertEquals("Multiple providers expose WRITE_CHANGE; explicit provider selection is required",
                observation.reason());
    }

    @Test
    void theSingleSupportedWriterIsNamedAndProbedAtTheRegisteredRoot() {
        projects.entry = Optional.of(new ProjectStoreEntry(projectId, SourceLocator.file("workspace")));
        FixedProvider openspec = writer("openspec", ProviderProbeStatus.SUPPORTED);

        ChangeWriteCapabilityObservation observation = resolver(List.of(
                openspec, writer("dormant", ProviderProbeStatus.UNSUPPORTED))).resolve(projectId);

        assertTrue(observation.writeAllowed());
        assertEquals(Optional.of(new ProviderId("openspec")), observation.providerId());
        assertEquals("Provider explicitly exposes WRITE_CHANGE: openspec", observation.reason());
        assertEquals(List.of(Path.of("workspace")), openspec.probedRoots);
    }

    private RegisteredProjectWriteCapabilityResolver resolver(List<FixedProvider> providers) {
        return new RegisteredProjectWriteCapabilityResolver(
                projects, new SpecificationProviderRegistry(providers, new ProviderSelectionPolicy()));
    }

    private static FixedProvider writer(String id, ProviderProbeStatus status) {
        return new FixedProvider(new ProviderId(id), status,
                ProviderCapabilitySet.of(ProviderCapability.READ_CHANGES, ProviderCapability.WRITE_CHANGE));
    }

    private static final class FixedProvider implements SpecificationProvider {
        private final ProviderId id;
        private final ProviderProbeStatus status;
        private final ProviderCapabilitySet capabilities;
        private final List<Path> probedRoots = new ArrayList<>();

        private FixedProvider(ProviderId id, ProviderProbeStatus status, ProviderCapabilitySet capabilities) {
            this.id = id;
            this.status = status;
            this.capabilities = capabilities;
        }

        @Override public ProviderId id() { return id; }
        @Override public String version() { return "1"; }
        @Override public boolean remote() { return false; }

        @Override
        public ProviderProbeResult probe(Path workspaceRoot) {
            probedRoots.add(workspaceRoot);
            return new ProviderProbeResult(id, "1", status, Optional.empty(), Optional.empty(), Optional.empty(),
                    capabilities, false, List.of());
        }
    }

    private static final class Projects implements SpecificationKnowledgeStore {
        private Optional<ProjectStoreEntry> entry = Optional.empty();

        @Override public Optional<ProjectStoreEntry> findProject(ProjectSpecificationId id) {
            return entry.filter(project -> project.id().equals(id));
        }

        @Override public void putProject(ProjectStoreEntry project) { throw new UnsupportedOperationException(); }
        @Override public Optional<ProjectStoreEntry> findProjectByRoot(SourceLocator rootLocator) { throw new UnsupportedOperationException(); }
        @Override public List<ProjectStoreEntry> listProjects() { throw new UnsupportedOperationException(); }
        @Override public void putSnapshot(KnowledgeSnapshotMetadata snapshot) { throw new UnsupportedOperationException(); }
        @Override public Optional<KnowledgeSnapshotMetadata> findSnapshot(KnowledgeSnapshotId id) { throw new UnsupportedOperationException(); }
        @Override public Optional<KnowledgeSnapshotMetadata> activeSnapshot(ProjectSpecificationId id) { throw new UnsupportedOperationException(); }
        @Override public KnowledgeSnapshotMetadata transitionSnapshotState(
                KnowledgeSnapshotId id, KnowledgeSnapshotState from, KnowledgeSnapshotState to) {
            throw new UnsupportedOperationException();
        }
        @Override public KnowledgeSnapshotMetadata activateSnapshot(
                KnowledgeSnapshotId id, Optional<KnowledgeSnapshotId> expected) {
            throw new UnsupportedOperationException();
        }
    }
}
