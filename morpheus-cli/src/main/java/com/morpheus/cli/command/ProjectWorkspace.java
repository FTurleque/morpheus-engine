package com.morpheus.cli.command;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.nio.file.Files;
import java.nio.file.Path;

/** The local directory of a registered project, as the commands that read a workspace require it. */
final class ProjectWorkspace {
    private ProjectWorkspace() {
    }

    /** The workspace of {@code projectId}, which must be registered with a {@code file:} root that is a directory. */
    static Path of(CliRuntime runtime, ProjectSpecificationId projectId) {
        ProjectStoreEntry project = runtime.snapshots().findProject(projectId)
                .orElseThrow(() -> CliFailure.notFound("project not found: " + projectId));
        if (!project.rootLocator().scheme().equals("file")) {
            throw CliFailure.state("CLI local workspace requires a file: project root");
        }
        return existingDirectory(Path.of(project.rootLocator().value()));
    }

    static Path existingDirectory(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(absolute)) {
            throw CliFailure.usage("workspace is not a directory: " + absolute);
        }
        return absolute;
    }
}
