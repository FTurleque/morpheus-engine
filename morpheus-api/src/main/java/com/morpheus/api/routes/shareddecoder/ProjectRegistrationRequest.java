package com.morpheus.api.routes.shareddecoder;

/** The body of a project registration request. */
record ProjectRegistrationRequest(String workspace) {
    public ProjectRegistrationRequest {
        if (workspace == null || workspace.isBlank()) {
            throw new IllegalArgumentException("workspace is required");
        }
        workspace = workspace.trim();
    }
}
