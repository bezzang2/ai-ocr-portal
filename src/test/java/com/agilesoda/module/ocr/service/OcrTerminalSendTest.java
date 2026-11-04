package com.agilesoda.module.ocr.service;

import com.agilesoda.common.rest.RestAgentProvider;
import com.agilesoda.module.ocr.mapper.OcrReqMapper;
import com.agilesoda.module.ocr.vo.OcrVerifyFieldVO;
import com.agilesoda.module.ocr.vo.OcrVerifyRequestVO;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** 검증완료 단말 전송: 식별자와 확인값을 보내고, 미설정·실패는 예외 없이 넘어간다. */
class OcrTerminalSendTest {

    private final OcrReqService service = new OcrReqService(mock(OcrReqMapper.class), mock(RestAgentProvider.class),
            mock(OcrAgentDataClient.class));

    private static OcrVerifyRequestVO request() {
        OcrVerifyFieldVO field = new OcrVerifyFieldVO(); field.setFildId(1); field.setFildVal("확인값");
        OcrVerifyRequestVO request = new OcrVerifyRequestVO();
        request.setFields(List.of(field));
        request.setTables(List.of());
        return request;
    }

    @Test void postsIdentifiersAndConfirmValues() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/verify", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            ReflectionTestUtils.setField(service, "terminalVerifyUrl",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/verify");
            service.sendVerifyResultToTerminal("wk", 10L, "t1", "d1", "me", request());
        } finally {
            server.stop(0);
        }
        String body = received.get();
        assertNotNull(body);
        for (String expected : List.of("\"wkId\":\"wk\"", "\"lefrmId\":10", "\"transId\":\"t1\"",
                "\"verifierId\":\"me\"", "\"fildVal\":\"확인값\"")) {
            assertTrue(body.contains(expected), expected + " in " + body);
        }
    }

    @Test void skipsWhenUrlNotSet() {
        ReflectionTestUtils.setField(service, "terminalVerifyUrl", "");
        assertDoesNotThrow(() -> service.sendVerifyResultToTerminal("wk", 10L, "t1", "d1", "me", request()));
    }

    @Test void failureDoesNotThrow() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        ReflectionTestUtils.setField(service, "terminalVerifyUrl", "http://127.0.0.1:" + closedPort + "/verify");
        assertDoesNotThrow(() -> service.sendVerifyResultToTerminal("wk", 10L, "t1", "d1", "me", request()));
    }
}
