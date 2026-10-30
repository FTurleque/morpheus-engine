package com.morpheus.application.store;

import java.util.List;
import java.util.Objects;

/** One page of CURRENT requirements and the number of requirements the criteria match, read from one state. */
public record RequirementPage(List<RequirementVersionRecord> items, int totalMatches) {

    public RequirementPage {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (totalMatches < items.size()) {
            throw new IllegalArgumentException("totalMatches must count at least the items of the page: "
                    + totalMatches + " < " + items.size());
        }
    }

    public boolean hasMoreAfter(int offset) {
        return (long) offset + items.size() < totalMatches;
    }
}
