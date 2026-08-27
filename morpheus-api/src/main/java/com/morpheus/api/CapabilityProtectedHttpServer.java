package com.morpheus.api;

import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Delegating HttpServer that capability-protects every context registered on the internal remote hop. */
final class CapabilityProtectedHttpServer extends HttpServer {
    private final HttpServer delegate;
    private final MorpheusInternalCapability capability;

    CapabilityProtectedHttpServer(HttpServer delegate, MorpheusInternalCapability capability) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.capability = Objects.requireNonNull(capability, "capability");
    }

    @Override
    public void bind(InetSocketAddress address, int backlog) throws IOException {
        delegate.bind(address, backlog);
    }

    @Override
    public void start() {
        delegate.start();
    }

    @Override
    public void setExecutor(Executor executor) {
        delegate.setExecutor(executor);
    }

    @Override
    public Executor getExecutor() {
        return delegate.getExecutor();
    }

    @Override
    public void stop(int delay) {
        delegate.stop(delay);
    }

    @Override
    public HttpContext createContext(String path, HttpHandler handler) {
        return new ProtectedContext(delegate.createContext(path, capability.protect(handler)));
    }

    @Override
    public HttpContext createContext(String path) {
        return new ProtectedContext(delegate.createContext(path));
    }

    @Override
    public void removeContext(String path) throws IllegalArgumentException {
        delegate.removeContext(path);
    }

    @Override
    public void removeContext(HttpContext context) {
        delegate.removeContext(unwrap(context));
    }

    @Override
    public InetSocketAddress getAddress() {
        return delegate.getAddress();
    }

    private HttpContext unwrap(HttpContext context) {
        return context instanceof ProtectedContext protectedContext ? protectedContext.delegate : context;
    }

    private final class ProtectedContext extends HttpContext {
        private final HttpContext delegate;

        private ProtectedContext(HttpContext delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public HttpHandler getHandler() {
            return delegate.getHandler();
        }

        @Override
        public void setHandler(HttpHandler handler) {
            delegate.setHandler(capability.protect(handler));
        }

        @Override
        public String getPath() {
            return delegate.getPath();
        }

        @Override
        public HttpServer getServer() {
            return CapabilityProtectedHttpServer.this;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return delegate.getAttributes();
        }

        @Override
        public List<Filter> getFilters() {
            return delegate.getFilters();
        }

        @Override
        public Authenticator setAuthenticator(Authenticator authenticator) {
            return delegate.setAuthenticator(authenticator);
        }

        @Override
        public Authenticator getAuthenticator() {
            return delegate.getAuthenticator();
        }
    }
}
