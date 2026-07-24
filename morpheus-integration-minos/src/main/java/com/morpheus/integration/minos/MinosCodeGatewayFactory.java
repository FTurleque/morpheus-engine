package com.morpheus.integration.minos;

@FunctionalInterface
public interface MinosCodeGatewayFactory {
    MinosCodeGateway open();
}
