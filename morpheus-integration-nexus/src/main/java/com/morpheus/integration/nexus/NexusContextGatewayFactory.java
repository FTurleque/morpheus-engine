package com.morpheus.integration.nexus;

@FunctionalInterface
interface NexusContextGatewayFactory {
    NexusContextGateway open();
}
