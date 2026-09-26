package com.morpheus.application.quality;

/**
 * Whether a coverage ratio of a quality report is a measurement.
 *
 * <p>Over an empty population the ratio is 1.0 by convention, so that the records validate; that value observes
 * nothing. {@link QualityReportMetrics} is the only place that decides between the two states.</p>
 */
public enum CoverageRatioStatus {
    MEASURED,
    UNDEFINED_EMPTY_POPULATION
}
