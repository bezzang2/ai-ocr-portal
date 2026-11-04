package com.agilesoda.module.apply.controller;

import com.agilesoda.module.apply.service.CrossRuleDefService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 서식간 규칙 미리보기: Agent 거절은 상태와 사유를 그대로, 내부 오류는 안내 문구만 돌려준다 */
class CrossRuleDefControllerTest {
    private final CrossRuleDefService service = mock(CrossRuleDefService.class);
    private final CrossRuleDefController controller = new CrossRuleDefController(service);

    @Test void judgeKeepsAgentRejection() {
        when(service.judge(any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "판독작업을 찾을 수 없습니다."));
        ResponseEntity<?> response = controller.judge("wk", "tx", List.of(), new MockHttpSession());
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(Map.of("message", "판독작업을 찾을 수 없습니다."), response.getBody());
    }

    @Test void judgeHidesInternalErrors() {
        when(service.judge(any(), any(), any())).thenThrow(new IllegalStateException("agent down"));
        ResponseEntity<?> failed = controller.judge("wk", "tx", List.of(), new MockHttpSession());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, failed.getStatusCode());
        assertFalse(String.valueOf(failed.getBody()).contains("agent down"));

        reset(service);
        when(service.judge(any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "agent body"));
        ResponseEntity<?> upstream = controller.judge("wk", "tx", List.of(), new MockHttpSession());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, upstream.getStatusCode());
        assertFalse(String.valueOf(upstream.getBody()).contains("agent body"));
    }
}
