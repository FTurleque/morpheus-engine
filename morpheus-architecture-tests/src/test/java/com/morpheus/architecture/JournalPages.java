package com.morpheus.architecture;

import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationAuditPage;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationAuditRecord;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationStore;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.store.SyncStateStore;
import com.morpheus.application.sync.SourceArchivePage;
import com.morpheus.application.sync.SourceArchiveRecord;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.util.ArrayList;
import java.util.List;

/** Reads a whole journal the only way a store offers it: one page after another, until no page says more follows. */
final class JournalPages {
    private JournalPages() {
    }

    static List<SourceArchiveRecord> allArchives(SyncStateStore store, ProjectSpecificationId projectId) {
        List<SourceArchiveRecord> records = new ArrayList<>();
        SourceArchivePage page;
        do {
            page = store.listArchives(projectId, new PageRequest(records.size(), PageRequest.MAX_LIMIT));
            records.addAll(page.items());
        } while (page.hasMore());
        return List.copyOf(records);
    }

    static List<ChangeLifecycleMutationAuditRecord> allAudit(
            ChangeLifecycleMutationStore store, ProjectSpecificationId projectId, ChangeId changeId) {
        List<ChangeLifecycleMutationAuditRecord> records = new ArrayList<>();
        ChangeLifecycleMutationAuditPage page;
        do {
            page = store.listAudit(projectId, changeId, new PageRequest(records.size(), PageRequest.MAX_LIMIT));
            records.addAll(page.items());
        } while (page.hasMore());
        return List.copyOf(records);
    }
}
