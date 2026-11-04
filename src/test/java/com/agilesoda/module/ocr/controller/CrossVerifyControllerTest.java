package com.agilesoda.module.ocr.controller;

import com.agilesoda.module.ocr.service.CrossVerifyService;
import com.agilesoda.module.ocr.vo.OcrAgentData;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import com.agilesoda.common.menu.ReviewMenuNavigation;
import com.agilesoda.module.common.entity.CommonMenuEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 서식간 검증 결과 일괄 다시 판정: 화면 기간(yyyy-MM-dd)을 Agent 형식으로 넘기고 오류는 안내 문구만 돌려준다. */
class CrossVerifyControllerTest {
    private final CrossVerifyService service = mock(CrossVerifyService.class);
    private final CrossVerifyController controller = new CrossVerifyController(service, new ReviewMenuNavigation());

    @Test void listResolvesCurrentRouteInsteadOfRequestedMenu() {
        MockHttpSession session = menuSession();
        ExtendedModelMap model = new ExtendedModelMap();
        assertEquals("module/ocr/cross-verify", controller.list(null, null, null, null, "MENU0301", model, session));
        assertEquals("C777", model.get("menuId"));
        assertEquals("서식간 검증 결과", model.get("menuNm"));
    }

    @Test void directDetailResolvesMenuWithoutRequestedId() {
        ExtendedModelMap model = new ExtendedModelMap();
        assertEquals("module/ocr/cross-verify-detail", controller.detailPage("fake-trans", "", model, menuSession()));
        assertEquals("C777", model.get("menuId"));
        assertEquals("통합검색", model.get("parentMenuNm"));
    }

    private MockHttpSession menuSession() {
        CommonMenuEntity menu = new CommonMenuEntity();
        menu.setMenuId("C777");
        menu.setMenuNm("서식간 검증 결과");
        menu.setUprnMenuNm("통합검색");
        menu.setMenuLevel(2);
        menu.setMenuUrl("module/ocr/crossVerify");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("menuList", List.of(menu));
        return session;
    }

    @Test void evaluateAllPassesPeriodInAgentFormat() {
        OcrAgentData.CrossBulkResult result = new OcrAgentData.CrossBulkResult();
        result.setTargets(2);
        result.setEvaluated(2);
        when(service.evaluateAll("wk", "20261001", "20261027")).thenReturn(result);
        // 거꾸로 준 기간은 바꿔서 넘긴다(목록 조회와 같다).
        ResponseEntity<?> response = controller.evaluateAll("wk", "2026-10-27", "2026-10-01");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(result, response.getBody());
    }

    @Test void evaluateAllHidesInternalErrors() {
        when(service.evaluateAll(any(), any(), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "조회 기간을 확인해 주세요."));
        ResponseEntity<?> rejected = controller.evaluateAll(null, null, null);
        assertEquals(HttpStatus.BAD_REQUEST, rejected.getStatusCode());
        assertEquals(Map.of("message", "조회 기간을 확인해 주세요."), rejected.getBody());

        reset(service);
        when(service.evaluateAll(any(), any(), any())).thenThrow(new IllegalStateException("agent down"));
        ResponseEntity<?> failed = controller.evaluateAll(null, null, null);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, failed.getStatusCode());
        assertFalse(String.valueOf(failed.getBody()).contains("agent down"));
    }
}
