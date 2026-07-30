package com.morpheus.application.reasoning;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceConfigurationError;
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

    /**
     * Returns the built-in deterministic adapter plus any valid classpath adapters.
     *
     * <p>Malformed, unavailable or duplicate optional providers are ignored so adapter discovery can never make
     * MORPHEUS facts-only operation unavailable. Discovery remains passive; execution still requires an explicit
     * adapter id in the request.</p>
     */
    public static ReasoningAdapterRegistry standard() {
        Map<String, ReasoningAdapter> discovered = new LinkedHashMap<>();
        addOptional(discovered, new EvidenceSynthesisReasoningAdapter());
        List<ServiceLoader.Provider<ReasoningAdapter>> providers;
        try {
            providers = ServiceLoader.load(ReasoningAdapter.class).stream().toList();
        } catch (ServiceConfigurationError failure) {
            return new ReasoningAdapterRegistry(discovered.values());
        }
        for (ServiceLoader.Provider<ReasoningAdapter> provider : providers) {
            try {
                addOptional(discovered, provider.get());
            } catch (ServiceConfigurationError | RuntimeException failure) {
                // Optional providers are fault-isolated from MORPHEUS facts-only operation.
            }
        }
        return new ReasoningAdapterRegistry(discovered.values());
    }

    public List<Descriptor> descriptors() {
        List<Descriptor> result = new ArrayList<>();
        for (ReasoningAdapter adapter : adapters.values()) {
            try {
                result.add(new Descriptor(adapter.id(), adapter.description()));
            } catch (ServiceConfigurationError | RuntimeException failure) {
                // A broken optional descriptor cannot make the adapter catalog unavailable.
            }
        }
        return result.stream().sorted(Comparator.comparing(Descriptor::id)).toList();
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

    private static void addOptional(Map<String, ReasoningAdapter> target, ReasoningAdapter adapter) {
        if (adapter == null) {
            return;
        }
        String id = requireId(adapter.id());
        target.putIfAbsent(id, adapter);
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
