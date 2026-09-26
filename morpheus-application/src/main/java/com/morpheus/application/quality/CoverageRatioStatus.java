package com.morpheus.application.quality;

/**
 * Whether a coverage ratio of a quality report is a measurement.
 *
 * <p>Over an empty population the ratio is 1.0 by convention, so that the records validate; that value observes
 * nothing. {@link QualityReportMetrics} is the only place that decides between the two states. It decides on the
 * report alone: a population that a multi-provider composition publishes twice is still {@code MEASURED} here, where
 * the policy frontier, which reads the composition state, calls it undefined.</p>
 */
public enum CoverageRatioStatus {
    MEASURED,
    UNDEFINED_EMPTY_POPULATION
}
