package com.morpheus.api;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Canonical bind policy for unauthenticated local MORPHEUS HTTP surfaces. */
public final class LoopbackHostPolicy {
    private LoopbackHostPolicy() {
    }

    public static String requireLoopback(String host) {
        return resolve(host, InetAddress::getAllByName).normalizedHost();
    }

    static String requireLoopback(String host, HostResolver resolver) {
        return resolve(host, resolver).normalizedHost();
    }

    static InetAddress requireLoopbackAddress(String host) {
        return resolve(host, InetAddress::getAllByName).bindAddress();
    }

    private static ResolvedLoopback resolve(String host, HostResolver resolver) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("local API host must not be blank");
        }
        String normalized = host.trim();
        try {
            InetAddress[] addresses = resolver.resolve(normalized);
            if (addresses.length == 0) {
                throw new IllegalArgumentException("local API host did not resolve");
            }
            for (InetAddress address : addresses) {
                if (!address.isLoopbackAddress()) {
                    throw new IllegalArgumentException(
                            "non-loopback API bind requires explicit remote mode with TLS and authentication");
                }
            }
            return new ResolvedLoopback(normalized, addresses[0]);
        } catch (UnknownHostException failure) {
            throw new IllegalArgumentException("cannot resolve local API host", failure);
        }
    }

    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private record ResolvedLoopback(String normalizedHost, InetAddress bindAddress) {
    }
}
