package com.morpheus.application.query;

import com.morpheus.application.constraint.ConstraintPolicyEvaluationService;
import com.morpheus.domain.constraint.ConstraintEvaluation;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A page of constraint evaluations evaluates the constraints it returns and no other. Filtering and sorting still run
 * over the whole change -- they give the total and the order -- but evaluating it all on every page made a caller
 * walking every page quadratic in the size of the change.
 */
class ConstraintEvaluationPageTest {

    @Test
    void aPageEvaluatesOnlyItsOwnSlice() {
        ConstraintSnapshotFixture fixture = ConstraintSnapshotFixture.nonBlocking(250);
        ConstraintPolicyEvaluationService real = new ConstraintPolicyEvaluationService();
        AtomicInteger evaluations = new AtomicInteger();
        ConstraintEvaluationQueryService service = new ConstraintEvaluationQueryService(
                fixture.snapshots(),
                fixture.content(),
                (constraint, target) -> {
                    evaluations.incrementAndGet();
                    return real.evaluate(constraint, target);
                });

        var page = service.activeEvaluations(
                        fixture.projectId(), fixture.changeId(), ConstraintSnapshotFixture.TARGET, new PageRequest(120, 50))
                .orElseThrow();

        assertEquals(50, evaluations.get(), "a page must evaluate its own slice and nothing else");
        assertEquals(250, page.totalMatches());
        assertTrue(page.hasMore());
        assertEquals(
                fixture.constraints().subList(120, 170).stream().map(item -> item.id()).toList(),
                page.items().stream().map(ConstraintEvaluation::constraintId).toList());
    }

    @Test
    void aPagePastTheEndEvaluatesNothingAndStillCountsTheChange() {
        ConstraintSnapshotFixture fixture = ConstraintSnapshotFixture.nonBlocking(7);
        AtomicInteger evaluations = new AtomicInteger();
        ConstraintEvaluationQueryService service = new ConstraintEvaluationQueryService(
                fixture.snapshots(),
                fixture.content(),
                (constraint, target) -> {
                    evaluations.incrementAndGet();
                    return new ConstraintPolicyEvaluationService().evaluate(constraint, target);
                });

        var page = service.activeEvaluations(
                        fixture.projectId(), fixture.changeId(), ConstraintSnapshotFixture.TARGET, new PageRequest(40, 10))
                .orElseThrow();

        assertEquals(0, evaluations.get());
        assertEquals(7, page.totalMatches());
        assertTrue(page.items().isEmpty());
    }
}
