package com.morpheus.application.reference;

@FunctionalInterface
public interface ExternalIntegrationStatusProvider {
    ExternalIntegrationStatus status();
}
