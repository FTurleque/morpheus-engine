package com.morpheus.api.routes.shareddecoder;

/** The body of a project synchronisation request. */
record SyncRequest(String revision) {
    public SyncRequest {
        revision = revision == null ? null : revision.trim();
        if (revision != null && revision.isEmpty()) {
            revision = null;
        }
    }
}
