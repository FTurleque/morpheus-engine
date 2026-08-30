package com.morpheus.api;

import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;

/** Shared fail-closed delegating server for protections applied at the HTTP handler boundary. */
abstract class RequestProtectedHttpServer extends HttpServer {
    private final HttpServer delegate;
    private final UnaryOperator<HttpHandler> protector;

    RequestProtectedHttpServer(HttpServer delegate, UnaryOperator<HttpHandler> protector) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.protector = Objects.requireNonNull(protector, "protector");
    }

    @Override
    public final void bind(InetSocketAddress address, int backlog) throws IOException {
        delegate.bind(address, backlog);
    }

    @Override
    public final void start() {
        delegate.start();
    }

    @Override
    public final void setExecutor(Executor executor) {
        delegate.setExecutor(executor);
    }

    @Override
    public final Executor getExecutor() {
        return delegate.getExecutor();
    }

    @Override
    public final void stop(int delay) {
        delegate.stop(delay);
    }

    @Override
    public final HttpContext createContext(String path, HttpHandler handler) {
        return new ProtectedContext(delegate.createContext(path, protect(handler)));
    }

    @Override
    public final HttpContext createContext(String path) {
        return new ProtectedContext(delegate.createContext(path));
    }

    @Override
    public final void removeContext(String path) throws IllegalArgumentException {
        delegate.removeContext(path);
    }

    @Override
    public final void removeContext(HttpContext context) {
        delegate.removeContext(unwrap(context));
    }

    @Override
    public final InetSocketAddress getAddress() {
        return delegate.getAddress();
    }

    private HttpHandler protect(HttpHandler handler) {
        HttpHandler protectedHandler = protector.apply(Objects.requireNonNull(handler, "handler"));
        return Objects.requireNonNull(protectedHandler, "protectedHandler");
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
            delegate.setHandler(protect(handler));
        }

        @Override
        public String getPath() {
            return delegate.getPath();
        }

        @Override
        public HttpServer getServer() {
            return RequestProtectedHttpServer.this;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return delegate.getAttributes();
        }

        @Override
        public List<Filter> getFilters() {
            // The protection wraps the handler. A mutable filter list could answer before that handler and bypass it.
            return Collections.unmodifiableList(delegate.getFilters());
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
