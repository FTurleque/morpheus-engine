package com.morpheus.architecture.budgetcatch.fixture;

import com.morpheus.application.read.ProviderIngestionBudget;
import com.morpheus.application.read.ProviderIngestionLimitException;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Compiled shapes the budget-refusal catch rule must judge, one per nested class. They are never run: the rule reads
 * their bytecode, and {@code ProviderIngestionLimitCatchArchitectureTest} names which ones it must refuse.
 */
public final class BudgetCatchFixtures {

    private BudgetCatchFixtures() {
    }

    public static final class SwallowsTheRefusalOfADirectBudgetCall {
        String read(ProviderIngestionBudget.Session budget) {
            try {
                budget.addBlocks(1, "fixture");
                return "read";
            } catch (IllegalArgumentException malformed) {
                return "malformed";
            }
        }
    }

    public static final class SwallowsTheRefusalOfAReadInAMultiCatch {
        String read(ProviderIngestionBudget.Session budget) {
            try {
                return budget.readDocument(Path.of("fixture.md"));
            } catch (IOException | IllegalArgumentException malformed) {
                return "malformed";
            }
        }
    }

    public static final class SwallowsTheRefusalThroughAHelper {
        String read(ProviderIngestionBudget.Session budget) {
            try {
                return normalize(budget);
            } catch (RuntimeException malformed) {
                return "malformed";
            }
        }

        private String normalize(ProviderIngestionBudget.Session budget) {
            budget.addEntities(1, "fixture");
            return "read";
        }
    }

    public static final class SwallowsTheRefusalThroughALambda {
        String read(ProviderIngestionBudget.Session budget) {
            try {
                return attribute(() -> {
                    budget.addEvidenceFragment("fixture", "fixture");
                    return "read";
                });
            } catch (IllegalArgumentException malformed) {
                return "malformed";
            }
        }

        private static String attribute(Supplier<String> work) {
            return work.get();
        }
    }

    public static final class SwallowsTheRefusalInACatchOfThrowable {
        String read(ProviderIngestionBudget.Session budget) {
            try {
                return budget.readDocument(Path.of("fixture.md"));
            } catch (Throwable anything) {
                return "failed";
            }
        }
    }

    public static final class ClosesAResourceAroundABudgetCall {
        String read(ProviderIngestionBudget.Session budget) throws IOException {
            try (StringReader resource = new StringReader("fixture")) {
                budget.addBlocks(resource.read(), "fixture");
                return "read";
            }
        }
    }

    public static final class HandlesTheRefusalBeforeTheBroaderClause {
        String read(ProviderIngestionBudget.Session budget) {
            try {
                return budget.readDocument(Path.of("fixture.md"));
            } catch (ProviderIngestionLimitException limit) {
                throw limit;
            } catch (IOException | IllegalArgumentException malformed) {
                return "malformed";
            }
        }
    }

    public static final class CatchesABroaderTypeAroundNoBudgetCall {
        Thread.State read(String raw) {
            try {
                return Thread.State.valueOf(raw);
            } catch (IllegalArgumentException unknown) {
                return Thread.State.NEW;
            }
        }
    }

    public static final class CatchesOnlyAnUnrelatedTypeAroundABudgetCall {
        String read(ProviderIngestionBudget.Session budget) {
            try {
                return budget.readDocument(Path.of("fixture.md"));
            } catch (IOException unreadable) {
                return "unreadable";
            }
        }
    }
}
