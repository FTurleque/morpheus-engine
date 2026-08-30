package com.morpheus.api;

import com.sun.net.httpserver.Authenticator;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Delegating server that applies the loopback Host/Origin policy to every registered local HTTP context. */
final class LoopbackRequestProtectedHttpServer extends HttpServer {
    private static final byte[] REJECTED_BODY = ("{\"apiVersion\":\"v1\",\"error\":{"
            + "\"code\":\"LOCAL_REQUEST_ORIGIN_REJECTED\","
            + "\"message\":\"local MORPHEUS API accepts loopback hosts and origins only\","
            + "\"details\":{}}}").getBytes(StandardCharsets.UTF_8);

    private final HttpServer delegate;

    LoopbackRequestProtectedHttpServer(HttpServer delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
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
        return new ProtectedContext(delegate.createContext(path, protect(handler)));
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

    private HttpHandler protect(HttpHandler handler) {
        Objects.requireNonNull(handler, "handler");
        return exchange -> {
            try {
                LoopbackRequestPolicy.requireAllowed(exchange);
            } catch (LoopbackRequestPolicy.RejectedRequestException rejected) {
                reject(exchange);
                return;
            }
            handler.handle(exchange);
        };
    }

    private static void reject(HttpExchange exchange) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer");
        exchange.sendResponseHeaders(403, REJECTED_BODY.length);
        exchange.getResponseBody().write(REJECTED_BODY);
        exchange.close();
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
            return LoopbackRequestProtectedHttpServer.this;
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
