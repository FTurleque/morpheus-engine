package com.morpheus.application.sync;

import com.morpheus.domain.project.ProjectSpecificationId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A scan is usable only when it is complete; partial observations never become a synchronization baseline. */
public record SourceInventoryScanResult(
        ProjectSpecificationId projectId,
        Optional<SourceInventory> inventory,
        List<Failure> failures) {

    public SourceInventoryScanResult {
        Objects.requireNonNull(projectId, "projectId");
        inventory = Objects.requireNonNull(inventory, "inventory");
        failures = Objects.requireNonNull(failures, "failures").stream()
                .peek(failure -> Objects.requireNonNull(failure, "failures item"))
                .sorted()
                .toList();
        if (inventory.isPresent() != failures.isEmpty()) {
            throw new IllegalArgumentException("complete scan requires inventory and no failures; incomplete scan requires failures only");
        }
        inventory.ifPresent(value -> {
            if (!value.projectId().equals(projectId)) {
                throw new IllegalArgumentException("scan inventory belongs to another project");
            }
        });
    }

    public static SourceInventoryScanResult complete(SourceInventory inventory) {
        Objects.requireNonNull(inventory, "inventory");
        return new SourceInventoryScanResult(inventory.projectId(), Optional.of(inventory), List.of());
    }

    public static SourceInventoryScanResult incomplete(ProjectSpecificationId projectId, List<Failure> failures) {
        return new SourceInventoryScanResult(projectId, Optional.empty(), failures);
    }

    public boolean complete() {
        return inventory.isPresent();
    }

    public record Failure(Optional<String> source, String message) implements Comparable<Failure> {
        public Failure {
            source = Objects.requireNonNull(source, "source").map(String::trim);
            Objects.requireNonNull(message, "message");
            message = message.trim();
            if (message.isEmpty()) {
                throw new IllegalArgumentException("failure message must not be blank");
            }
        }

        @Override
        public int compareTo(Failure other) {
            int sourceOrder = source.orElse("").compareTo(other.source.orElse(""));
            return sourceOrder != 0 ? sourceOrder : message.compareTo(other.message);
        }
    }
}
