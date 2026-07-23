package com.morpheus.application.sync;

import java.util.List;
import java.util.Objects;

/** Pure policy separating noisy WatchService signals from synchronization correctness. */
public final class WatchSignalPolicy {

    public boolean requiresFullRebuild(List<SourceWatchSignal> signals) {
        Objects.requireNonNull(signals, "signals");
        return signals.stream().anyMatch(signal -> signal.kind() == SourceWatchSignal.Kind.OVERFLOW);
    }

    public List<SourcePath> affectedPaths(List<SourceWatchSignal> signals) {
        Objects.requireNonNull(signals, "signals");
        return signals.stream()
                .map(SourceWatchSignal::path)
                .flatMap(java.util.Optional::stream)
                .distinct()
                .sorted()
                .toList();
    }
}
