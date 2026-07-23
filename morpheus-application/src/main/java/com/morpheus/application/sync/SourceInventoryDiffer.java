package com.morpheus.application.sync;

import com.morpheus.application.sync.SourceInventoryDiff.Change;
import com.morpheus.application.sync.SourceInventoryDiff.ChangeKind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Pure deterministic inventory differ. Ambiguous rename candidates are never guessed. */
public final class SourceInventoryDiffer {

    public SourceInventoryDiff diff(SourceInventory before, SourceInventory after) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        if (!before.projectId().equals(after.projectId())) {
            throw new IllegalArgumentException("inventories belong to different projects");
        }

        Map<SourcePath, SourceInventory.Entry> beforeByPath = byPath(before.entries());
        Map<SourcePath, SourceInventory.Entry> afterByPath = byPath(after.entries());
        List<Change> stablePathChanges = new ArrayList<>();
        List<SourceInventory.Entry> deleted = new ArrayList<>();
        List<SourceInventory.Entry> added = new ArrayList<>();

        for (SourceInventory.Entry oldEntry : before.entries()) {
            SourceInventory.Entry newEntry = afterByPath.get(oldEntry.path());
            if (newEntry == null) {
                deleted.add(oldEntry);
            } else if (oldEntry.equals(newEntry)) {
                stablePathChanges.add(new Change(ChangeKind.UNCHANGED, Optional.of(oldEntry), Optional.of(newEntry)));
            } else if (oldEntry.sameContentAs(newEntry)) {
                // Same content and path: only non-content metadata could differ. Entry currently has no such metadata.
                stablePathChanges.add(new Change(ChangeKind.UNCHANGED, Optional.of(oldEntry), Optional.of(newEntry)));
            } else {
                stablePathChanges.add(new Change(ChangeKind.MODIFIED, Optional.of(oldEntry), Optional.of(newEntry)));
            }
        }
        for (SourceInventory.Entry newEntry : after.entries()) {
            if (!beforeByPath.containsKey(newEntry.path())) {
                added.add(newEntry);
            }
        }

        Map<ContentKey, List<SourceInventory.Entry>> deletedByContent = groupByContent(deleted);
        Map<ContentKey, List<SourceInventory.Entry>> addedByContent = groupByContent(added);
        Map<SourcePath, SourceInventory.Entry> movedDeleted = new HashMap<>();
        Map<SourcePath, SourceInventory.Entry> movedAdded = new HashMap<>();
        List<Change> moved = new ArrayList<>();
        boolean ambiguous = false;

        for (Map.Entry<ContentKey, List<SourceInventory.Entry>> entry : deletedByContent.entrySet()) {
            List<SourceInventory.Entry> oldCandidates = entry.getValue();
            List<SourceInventory.Entry> newCandidates = addedByContent.getOrDefault(entry.getKey(), List.of());
            if (newCandidates.isEmpty()) {
                continue;
            }
            if (oldCandidates.size() == 1 && newCandidates.size() == 1) {
                SourceInventory.Entry oldEntry = oldCandidates.getFirst();
                SourceInventory.Entry newEntry = newCandidates.getFirst();
                movedDeleted.put(oldEntry.path(), oldEntry);
                movedAdded.put(newEntry.path(), newEntry);
                moved.add(new Change(ChangeKind.MOVED, Optional.of(oldEntry), Optional.of(newEntry)));
            } else {
                ambiguous = true;
            }
        }

        List<Change> result = new ArrayList<>(stablePathChanges);
        result.addAll(moved);
        deleted.stream()
                .filter(entry -> !movedDeleted.containsKey(entry.path()))
                .map(entry -> new Change(ChangeKind.DELETED, Optional.of(entry), Optional.empty()))
                .forEach(result::add);
        added.stream()
                .filter(entry -> !movedAdded.containsKey(entry.path()))
                .map(entry -> new Change(ChangeKind.ADDED, Optional.empty(), Optional.of(entry)))
                .forEach(result::add);

        return new SourceInventoryDiff(result, ambiguous);
    }

    private Map<SourcePath, SourceInventory.Entry> byPath(List<SourceInventory.Entry> entries) {
        Map<SourcePath, SourceInventory.Entry> result = new LinkedHashMap<>();
        entries.forEach(entry -> result.put(entry.path(), entry));
        return result;
    }

    private Map<ContentKey, List<SourceInventory.Entry>> groupByContent(List<SourceInventory.Entry> entries) {
        Map<ContentKey, List<SourceInventory.Entry>> grouped = new HashMap<>();
        for (SourceInventory.Entry entry : entries) {
            grouped.computeIfAbsent(new ContentKey(entry.fingerprint(), entry.sizeBytes()), ignored -> new ArrayList<>())
                    .add(entry);
        }
        grouped.values().forEach(list -> list.sort(SourceInventory.Entry::compareTo));
        return grouped;
    }

    private record ContentKey(SourceFingerprint fingerprint, long sizeBytes) {
    }
}
