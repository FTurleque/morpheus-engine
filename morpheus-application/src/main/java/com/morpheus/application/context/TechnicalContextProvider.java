package com.morpheus.application.context;

import com.morpheus.application.reference.ExternalIntegrationStatus;

/** Port implemented by optional engines that own technical context selection/ranking/compression. */
public interface TechnicalContextProvider {
    String system();

    ExternalIntegrationStatus status();

    TechnicalContextObservation build(TechnicalContextRequest request);
}
