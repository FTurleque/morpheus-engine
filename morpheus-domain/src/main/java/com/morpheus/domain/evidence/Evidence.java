package com.morpheus.domain.evidence;

import com.morpheus.domain.source.SourceLocator;

import java.util.Objects;
import java.util.Optional;

/** Evidence pointing back to the exact source material supporting normalized knowledge. */
public record Evidence(
        EvidenceId id,
        SourceLocator source,
        Optional<SourceRange> range,
        Optional<String> excerptHash) {

    public Evidence {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(source, "source");
        range = Objects.requireNonNull(range, "range");
        excerptHash = Objects.requireNonNull(excerptHash, "excerptHash")
                .map(String::trim)
                .filter(value -> !value.isEmpty());
    }
}
