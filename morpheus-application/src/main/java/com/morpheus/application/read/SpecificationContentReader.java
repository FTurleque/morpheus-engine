package com.morpheus.application.read;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.domain.provider.ProviderId;

/** Provider adapter port for normalized content reads beyond capability probing. */
public interface SpecificationContentReader {
    ProviderId providerId();

    ProviderReadResult read(ProviderReadRequest request, EntityIdentityResolver identityResolver);
}
