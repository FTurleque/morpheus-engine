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
import java.util.function.Consumer;

/** Immutable registry. Discovery never implies execution: adapters must still be selected in each request. */
public final class ReasoningAdapterRegistry {
    private final Map<String, ReasoningAdapter> adapters;
    private final Consumer<Throwable> setAside;

    /** A registry of adapters the caller chose itself; nobody is told of one it sets aside. */
    public ReasoningAdapterRegistry(Collection<? extends ReasoningAdapter> adapters) {
        this(adapters, failure -> { });
    }

    /**
     * @param setAside told of every optional adapter this registry leaves out because it failed, with the failure. The
     *        registry stays usable either way; the observer is what keeps a broken adapter from looking like an absent
     *        one.
     */
    public ReasoningAdapterRegistry(Collection<? extends ReasoningAdapter> adapters, Consumer<Throwable> setAside) {
        Objects.requireNonNull(adapters, "adapters");
        this.setAside = Objects.requireNonNull(setAside, "setAside");
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
     * <p>Malformed, unavailable or duplicate optional providers are left out so adapter discovery can never make
     * MORPHEUS facts-only operation unavailable. Discovery remains passive; execution still requires an explicit
     * adapter id in the request.</p>
     *
     * @param setAside told of every provider that was left out because it failed. A provider that is present and
     *        broken used to disappear exactly as if it had never been installed.
     */
    public static ReasoningAdapterRegistry standard(Consumer<Throwable> setAside) {
        Objects.requireNonNull(setAside, "setAside");
        Map<String, ReasoningAdapter> discovered = new LinkedHashMap<>();
        addOptional(discovered, new EvidenceSynthesisReasoningAdapter());
        List<ServiceLoader.Provider<ReasoningAdapter>> providers;
        try {
            providers = ServiceLoader.load(ReasoningAdapter.class).stream().toList();
        } catch (ServiceConfigurationError failure) {
            setAside.accept(failure);
            return new ReasoningAdapterRegistry(discovered.values(), setAside);
        }
        for (ServiceLoader.Provider<ReasoningAdapter> provider : providers) {
            try {
                addOptional(discovered, provider.get());
            } catch (ServiceConfigurationError | RuntimeException failure) {
                // Optional providers are fault-isolated from MORPHEUS facts-only operation.
                setAside.accept(failure);
            }
        }
        return new ReasoningAdapterRegistry(discovered.values(), setAside);
    }

    public List<Descriptor> descriptors() {
        List<Descriptor> result = new ArrayList<>();
        for (ReasoningAdapter adapter : adapters.values()) {
            try {
                result.add(new Descriptor(adapter.id(), adapter.description()));
            } catch (ServiceConfigurationError | RuntimeException failure) {
                // A broken optional descriptor cannot make the adapter catalog unavailable.
                setAside.accept(failure);
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
