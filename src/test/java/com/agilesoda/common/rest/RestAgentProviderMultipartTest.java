package com.agilesoda.common.rest;

import com.agilesoda.common.response.entity.RestResponseEntity;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** multipart 중계: 실제 HTTP 로 필드·파일 파트를 보내고, Agent 오류 응답도 본문 그대로 읽는다. */
class RestAgentProviderMultipartTest {

    private static MultiValueMap<String, Object> parts() {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("req_sys", "MOCK");
        parts.add("wkflow_id", "wf-1");
        parts.add("wkflow_id", "wf-2");
        parts.add("attach_file", new ByteArrayResource(new byte[]{1, 2, 3}) {
            @Override public String getFilename() { return "sample.png"; }
        });
        return parts;
    }

    private static HttpServer agent(int status, String body, Map<String, String> received) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/aiocr/api/v1/ocr/extract", exchange -> {
            received.put("contentType", exchange.getRequestHeaders().getFirst("Content-Type"));
            received.put("body", new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }

    @Test void sendsFieldsAndFilesAsMultipart() throws Exception {
        Map<String, String> received = new java.util.concurrent.ConcurrentHashMap<>();
        HttpServer server = agent(200, "{\"status\":200,\"message\":\"성공\",\"data\":{\"TRANS_LIST\":[\"t-1\"]}}", received);
        try {
            RestAgentProvider provider = new RestAgentProvider(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/aiocr", "id", "pw", false);
            RestResponseEntity<?> res = provider.postMultipart("/api/v1/ocr/extract", parts());

            assertEquals(200, res.getStatus());
            assertEquals(Map.of("TRANS_LIST", java.util.List.of("t-1")), res.getData());
            assertTrue(received.get("contentType").startsWith("multipart/form-data;"), received.get("contentType"));
            String body = received.get("body");
            assertTrue(body.contains("name=\"req_sys\""));
            assertEquals(2, body.split("name=\"wkflow_id\"", -1).length - 1);
            assertTrue(body.contains("name=\"attach_file\"; filename=\"sample.png\""));
        } finally {
            server.stop(0);
        }
    }

    @Test void readsAgentErrorStatusWithoutThrowing() throws Exception {
        Map<String, String> received = new java.util.concurrent.ConcurrentHashMap<>();
        HttpServer rejected = agent(401, "", received);
        try {
            RestAgentProvider provider = new RestAgentProvider(
                    "http://127.0.0.1:" + rejected.getAddress().getPort() + "/aiocr", "id", "pw", false);
            assertEquals(401, provider.postMultipart("/api/v1/ocr/extract", parts()).getStatus());
        } finally {
            rejected.stop(0);
        }
    }
}
