package com.agilesoda.common.rest;

import com.agilesoda.common.filter.RequestIdFilter;
import com.sun.net.httpserver.HttpServer;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RestAgentRequestIdTest {
    private HttpServer server;
    private RestAgentProvider provider;
    private final AtomicReference<String> received = new AtomicReference<>("unset");

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", exchange -> {
            received.set(exchange.getRequestHeaders().getFirst(RequestIdFilter.HEADER));
            byte[] body = "{\"status\":200,\"success\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        provider = new RestAgentProvider("http://127.0.0.1:" + server.getAddress().getPort(), "", "", false);
        server.start();
    }

    @AfterEach
    void stop() {
        MDC.remove(RequestIdFilter.MDC_KEY);
        server.stop(0);
    }

    @Test
    void sendsRequestIdFromMdc() throws Exception {
        MDC.put(RequestIdFilter.MDC_KEY, "req-abc123");
        provider.get("/api/ping", new JSONObject());
        assertEquals("req-abc123", received.get());
    }

    @Test
    void omitsHeaderWithoutMdc() throws Exception {
        provider.get("/api/ping", new JSONObject());
        assertNull(received.get());
    }
}
