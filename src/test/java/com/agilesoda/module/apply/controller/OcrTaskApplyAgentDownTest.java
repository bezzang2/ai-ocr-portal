package com.agilesoda.module.apply.controller;

import com.agilesoda.common.response.BaseResponseBody;
import com.agilesoda.module.apply.service.OcrTaskApplyService;
import com.agilesoda.module.common.CommonService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Agent가 꺼져 있어도 업무신청 화면은 열리고, 목록 API는 내부 원인 없이 안내 문구만 돌려준다 */
class OcrTaskApplyAgentDownTest {
    private final OcrTaskApplyService service = mock(OcrTaskApplyService.class);
    private final CommonService commonService = mock(CommonService.class);

    @Test void pageOpensWithoutCallingAgent() throws Exception {
        when(commonService.getListComCode("BZWK_CD")).thenReturn(List.of(Map.of("CD_VAL", "01", "CD_NM", "가짜분류")));
        ExtendedModelMap model = new ExtendedModelMap();

        String view = new OcrTaskApplyController(commonService).ocrTaskApply(null, null, model);

        assertEquals("module/apply/ocr-task-apply", view);
        assertEquals(1, ((List<?>) ((Map<?, ?>) model.get("data")).get("bzwkCds")).size());
    }

    @Test void listApiHidesAgentFailure() throws Exception {
        when(service.getWorkflowList(any(), anyInt(), anyInt(), any()))
                .thenThrow(new ResourceAccessException("agent down"));

        ResponseEntity<?> response = new OcrTaskApplyRestController(service).ocrWkflowList("all", "");

        // 이 API는 HTTP 200 본문의 status로 결과를 알린다. 화면 JS가 res.status로 안내 문구를 띄운다.
        BaseResponseBody body = (BaseResponseBody) response.getBody();
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), body.getStatus());
        assertFalse(String.valueOf(body.getMessage()).contains("agent down"));
    }
}
