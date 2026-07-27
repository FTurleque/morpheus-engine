package com.morpheus.application.composition;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.operability.LocalOperationalRuntime;
import com.morpheus.application.operability.OperationalExecution;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ProviderReadResult;
import com.morpheus.application.read.ReadCategoryReport;
import com.morpheus.application.read.ReadCategoryStatus;
import com.morpheus.application.read.SpecificationContentReader;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.provider.ProviderId;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Coordinates multiple provider reads and delegates all merge semantics to the composition service. */
public final class MultiProviderReadService {
    private final Map<ProviderId, SpecificationContentReader> readers;
    private final MultiProviderCompositionService compositionService;
    private final ObservedProviderContributionLoader observedLoader;

    public MultiProviderReadService(
            Collection<? extends SpecificationContentReader> readers,
            MultiProviderCompositionService compositionService) {
        this(readers, compositionService, new OperationalExecution(LocalOperationalRuntime.recorder()));
    }

    public MultiProviderReadService(
            Collection<? extends SpecificationContentReader> readers,
            MultiProviderCompositionService compositionService,
            OperationalExecution execution) {
        Objects.requireNonNull(readers, "readers");
        this.compositionService = Objects.requireNonNull(compositionService, "compositionService");
        this.observedLoader = new ObservedProviderContributionLoader(
                Objects.requireNonNull(execution, "execution"));
        Map<ProviderId, SpecificationContentReader> indexed = new LinkedHashMap<>();
        for (SpecificationContentReader reader : readers) {
            Objects.requireNonNull(reader, "reader");
            if (indexed.putIfAbsent(reader.providerId(), reader) != null) {
                throw new IllegalArgumentException("duplicate content reader: " + reader.providerId());
            }
        }
        this.readers = Map.copyOf(indexed);
    }

    public MultiProviderCompositionResult read(
            ProviderReadRequest request,
            EntityIdentityResolver identityResolver,
            List<ProviderCompositionSource> sources) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(identityResolver, "identityResolver");
        Objects.requireNonNull(sources, "sources");
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("at least one composition source is required");
        }

        List<ProviderContribution> contributions = sources.stream()
                .map(source -> contribution(request, identityResolver, source))
                .toList();
        return compositionService.compose(contributions);
    }

    private ProviderContribution contribution(
            ProviderReadRequest request,
            EntityIdentityResolver identityResolver,
            ProviderCompositionSource source) {
        SpecificationContentReader reader = readers.get(source.providerId());
        if (reader == null) {
            return new ProviderContribution(
                    source.providerId(), source.priority(), source.required(), unavailable(request, source));
        }
        return observedLoader.load(
                source.providerId(),
                source.priority(),
                source.required(),
                () -> reader.read(request, identityResolver));
    }

    private ProviderReadResult unavailable(ProviderReadRequest request, ProviderCompositionSource source) {
        Diagnostic diagnostic = Diagnostic.warning(
                DiagnosticCode.OPTIONAL_CAPABILITY_UNAVAILABLE,
                "No content reader is registered for requested composition provider",
                Map.of("provider", source.providerId().value(), "required", Boolean.toString(source.required())));
        ReadCategoryStatus status = source.required() ? ReadCategoryStatus.FAILED : ReadCategoryStatus.ABSENT;
        List<ReadCategoryReport> reports = request.requestedCategories().stream()
                .sorted()
                .map(category -> new ReadCategoryReport(
                        category,
                        status,
                        0,
                        List.of(DiagnosticCode.OPTIONAL_CAPABILITY_UNAVAILABLE),
                        Optional.of("content reader is not registered")))
                .toList();
        return new ProviderReadResult(source.providerId(), Optional.empty(), reports, List.of(diagnostic));
    }
}
