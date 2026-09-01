package com.morpheus.api;

import com.sun.net.httpserver.HttpServer;

import java.util.Objects;

/** Delegating HttpServer that capability-protects every context registered on the internal remote hop. */
final class CapabilityProtectedHttpServer extends RequestProtectedHttpServer {
    CapabilityProtectedHttpServer(HttpServer delegate, MorpheusInternalCapability capability) {
        super(delegate, Objects.requireNonNull(capability, "capability")::protect);
    }
}
