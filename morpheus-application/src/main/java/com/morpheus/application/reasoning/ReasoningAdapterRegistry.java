package com.morpheus.application.reasoning;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

/** Immutable registry. Discovery never implies execution: adapters must still be selected in each request. */
public final class ReasoningAdapterRegistry {
    private final Map<String, ReasoningAdapter> adapters;

    public ReasoningAdapterRegistry(Collection<? extends ReasoningAdapter> adapters) {
        Objects.requireNonNull(adapters, "adapters");
        Map<String, ReasoningAdapter> result = new LinkedHashMap<>();
        for (ReasoningAdapter adapter : adapters) {
            ReasoningAdapter value = Objects.requireNonNull(adapter, "adapter");
            String id = requireId(value.id());
            if (result.putIfAbsent(id, value) != null) {
                throw new IllegalArgumentException("duplicate reasoning adapter id: " + id);
            }
        }
        this.adapters = Map.copyOf(result);
    }

    public static ReasoningAdapterRegistry empty() {
        return new ReasoningAdapterRegistry(List.of());
    }

    public static ReasoningAdapterRegistry standard() {
        List<ReasoningAdapter> discovered = new ArrayList<>();
        discovered.add(new EvidenceSynthesisReasoningAdapter());
        ServiceLoader.load(ReasoningAdapter.class).forEach(discovered::add);
        return new ReasoningAdapterRegistry(discovered);
    }

    public List<Descriptor> descriptors() {
        return adapters.values().stream()
                .map(adapter -> new Descriptor(adapter.id(), adapter.description()))
                .sorted(Comparator.comparing(Descriptor::id))
                .toList();
    }

    public List<ReasoningAdapter> select(List<String> adapterIds) {
        Objects.requireNonNull(adapterIds, "adapterIds");
        List<ReasoningAdapter> selected = new ArrayList<>(adapterIds.size());
        for (String rawId : adapterIds) {
            String id = requireId(rawId);
            ReasoningAdapter adapter = adapters.get(id);
            if (adapter == null) {
                throw new IllegalArgumentException("unknown reasoning adapter: " + id);
            }
            selected.add(adapter);
        }
        return List.copyOf(selected);
    }

    private static String requireId(String raw) {
        String id = Objects.requireNonNull(raw, "adapter id").trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("adapter id must not be blank");
        }
        if (id.length() > 128) {
            throw new IllegalArgumentException("adapter id exceeds 128 characters");
        }
        return id;
    }

    public record Descriptor(String id, String description) {
        public Descriptor {
            id = requireId(id);
            description = Objects.requireNonNull(description, "description").trim();
            if (description.isEmpty()) {
                throw new IllegalArgumentException("adapter description must not be blank");
            }
        }
    }
}
