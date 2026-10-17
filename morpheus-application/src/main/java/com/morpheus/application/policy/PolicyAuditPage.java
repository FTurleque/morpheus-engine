package com.morpheus.application.policy;

import com.morpheus.application.query.PageRequest;

import java.util.List;
import java.util.Objects;

/**
 * One page of a policy pack's audit, in {@link PolicyConfiguration.AuditRecord}'s order, with the number of records the
 * pack has. A journal only grows: no surface reads it whole, and a page says whether more follows.
 */
public record PolicyAuditPage(
        List<PolicyConfiguration.AuditRecord> items,
        PageRequest pageRequest,
        int totalMatches,
        boolean hasMore) {

    public PolicyAuditPage {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        Objects.requireNonNull(pageRequest, "pageRequest");
        if (totalMatches < 0) {
            throw new IllegalArgumentException("totalMatches must be >= 0");
        }
        if (items.size() > pageRequest.limit()) {
            throw new IllegalArgumentException("items must not exceed page limit");
        }
        long consumed = (long) pageRequest.offset() + items.size();
        if (hasMore != consumed < totalMatches) {
            throw new IllegalArgumentException("hasMore is inconsistent with page bounds");
        }
    }

    /** The page of {@code ordered}, a journal already in order, that {@code pageRequest} asks for. */
    public static PolicyAuditPage of(List<PolicyConfiguration.AuditRecord> ordered, PageRequest pageRequest) {
        Objects.requireNonNull(ordered, "ordered");
        Objects.requireNonNull(pageRequest, "pageRequest");
        int total = ordered.size();
        int from = Math.min(pageRequest.offset(), total);
        int to = (int) Math.min((long) from + pageRequest.limit(), total);
        return new PolicyAuditPage(ordered.subList(from, to), pageRequest, total, to < total);
    }
}
