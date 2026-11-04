package com.agilesoda.common.rest;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RestAgentViewerImageTest {
    private HttpServer server;
    private RestAgentProvider provider;
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0};

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider = new RestAgentProvider("http://127.0.0.1:" + server.getAddress().getPort(), "", "", false);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void acceptsPngAndPassesSelectedImageView() throws Exception {
        AtomicReference<String> query = new AtomicReference<>();
        server.createContext("/page", exchange -> {
            query.set(exchange.getRequestURI().getQuery());
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, PNG.length);
            try (var out = exchange.getResponseBody()) { out.write(PNG); }
        });
        assertArrayEquals(PNG, provider.getViewerImage("/page", "masked"));
        assertEquals("view=masked", query.get());
    }

    @Test
    void doesNotServeHtmlOrInvalidPngPayloadsAsImages() {
        server.createContext("/html", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, PNG.length);
            try (var out = exchange.getResponseBody()) { out.write(PNG); }
        });
        server.createContext("/invalid", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, 3);
            try (var out = exchange.getResponseBody()) { out.write(new byte[]{1, 2, 3}); }
        });
        assertThrows(Exception.class, () -> provider.getViewerImage("/html", "processed"));
        assertThrows(Exception.class, () -> provider.getViewerImage("/invalid", "processed"));
    }

    @Test
    void preservesExpiredImageStatusWithoutParsingErrorPayload() {
        server.createContext("/expired", exchange -> {
            exchange.sendResponseHeaders(410, -1);
            exchange.close();
        });
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> provider.getViewerImage("/expired", "processed"));
        assertEquals(410, ex.getStatusCode().value());
    }
}
