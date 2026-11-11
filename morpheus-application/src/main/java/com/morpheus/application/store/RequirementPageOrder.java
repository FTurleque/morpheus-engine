package com.morpheus.application.store;

import java.util.Comparator;

/**
 * The order of a page of CURRENT requirements. Each order is total: two versions the named key does not separate are
 * ordered by their entity version identity, so two pages of one read never repeat or skip a requirement.
 */
public enum RequirementPageOrder {
    REQUIREMENT_ID(Comparator.comparing(record -> record.entityVersion().content().id())),
    ENTITY_IDENTITY(Comparator.comparing(record -> record.entityVersion().entityIdentity()));

    private final Comparator<RequirementVersionRecord> comparator;

    RequirementPageOrder(Comparator<RequirementVersionRecord> key) {
        this.comparator = key.thenComparing(record -> record.entityVersion().id());
    }

    public Comparator<RequirementVersionRecord> comparator() {
        return comparator;
    }
}
