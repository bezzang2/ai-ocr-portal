package com.agilesoda.module.ocr.service;

import com.agilesoda.module.apply.vo.OcrVerifyCellVO;
import com.agilesoda.module.ocr.mapper.OcrReviewMapper;
import com.agilesoda.module.ocr.vo.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 판독결과 검토(Portal): 로그인 사용자·지정 권한을 Agent 에 넘기고, Agent 의 거절 문구를 그대로 돌려주며,
 * 담당자 이름을 Portal 사용자 정보로 채운다. 검토 규칙 자체는 Agent OcrReviewServiceTest 가 확인한다.
 */
class OcrReviewServiceTest {
    private OcrReviewMapper reviewMapper;
    private OcrReqService reqService;
    private OcrAgentDataClient agentData;
    private OcrReviewService service;

    @BeforeEach void setUp() {
        reviewMapper = mock(OcrReviewMapper.class);
        reqService = mock(OcrReqService.class);
        agentData = mock(OcrAgentDataClient.class);
        service = new OcrReviewService(reviewMapper, reqService, agentData, mock(com.agilesoda.module.apply.mapper.CrossRuleMapper.class));
        OcrResultLefrmVO key = new OcrResultLefrmVO();
        key.setTransId("t1");
        key.setDocId("d1");
        key.setDocStatCd("pass");
        when(reqService.resolveDocument(10L, "wk", "t1", null)).thenReturn(key);
        when(reviewMapper.selectUserNm(anyString())).thenReturn("이름");
        when(reviewMapper.selectUserNmList(any())).thenReturn(List.of(user("me", "나"), user("lead", "팀장")));
    }

    private static OcrReviewerVO user(String id, String name) {
        OcrReviewerVO user = new OcrReviewerVO();
        user.setUserId(id);
        user.setUserNm(name);
        return user;
    }

    private static OcrReviewerVO reviewer(String id, boolean canAssign) {
        OcrReviewerVO reviewer = user(id, null);
        reviewer.setCanAssign(canAssign);
        return reviewer;
    }

    private static OcrAgentData.ReviewState state(String docId, String transAssignee) {
        OcrAgentData.ReviewState state = new OcrAgentData.ReviewState();
        state.setTransactionId("t1");
        state.setDocumentId(docId);
        state.setTransactionAssigneeId(transAssignee);
        return state;
    }

    private static OcrVerifyRequestVO values() {
        OcrVerifyFieldVO a = new OcrVerifyFieldVO(); a.setFildId(1); a.setFildVal("가");
        OcrVerifyCellVO c = new OcrVerifyCellVO(); c.setCellId(3); c.setFildVal("다");
        OcrVerifyRequestVO request = new OcrVerifyRequestVO();
        request.setFields(List.of(a));
        request.setTables(List.of(c));
        return request;
    }

    @Test void assignPassesActorAndFillsNames() {
        when(agentData.assignTransaction(eq("wk"), eq("t1"), any())).thenReturn(state("*", "me"));

        OcrReviewStateVO result = service.assignTransaction("wk", "t1", " me ", reviewer("lead", true));

        verify(agentData).assignTransaction("wk", "t1",
                new OcrAgentData.AssignRequest(new OcrAgentData.Actor("lead", true), "me"));
        assertEquals("me", result.getAsgnUserId());
        assertEquals("나", result.getAsgnUserNm());
    }

    private static OcrAgentData.Document document(String typeCode) {
        OcrAgentData.Document document = new OcrAgentData.Document();
        document.setTypeCode(typeCode);
        return document;
    }

    private static OcrResultLefrmVO form(String formId) {
        OcrResultLefrmVO form = new OcrResultLefrmVO();
        form.setFormId(formId);
        return form;
    }

    @Test void reviewTransactionsFillNamesCountsAndEmptyForms() {
        OcrAgentData.ReviewTransaction row = new OcrAgentData.ReviewTransaction();
        row.setTransactionId("t1");
        row.setWorkflowId("wk");
        row.setWorkflowName("가짜업무");
        row.setScanDateTime("20261031181300");
        row.setStatus("completed");
        row.setAssigneeId("me");
        row.setReviewStatus("IN_REVIEW");
        row.setDocumentCount(3);
        row.setFailedCount(1);
        row.setVerifiedCount(1);
        row.setDocuments(List.of(document("KJB_100"), document("200")));
        when(agentData.reviewTransactions("wk", null, "me", null, "20261026", "20261101")).thenReturn(List.of(row));
        when(reqService.linkedFormsByWorkflow("wk")).thenReturn(Map.of("wk", List.of(form("100"), form("200"), form("300"))));

        // 업무 ID 는 소문자로 바꿔 Agent 에 넘긴다.
        OcrReviewTransactionVO vo = service.reviewTransactions(" WK ", null, "me", null, "20261026", "20261101").get(0);

        assertEquals("나", vo.getAsgnUserNm());
        assertEquals(2, vo.getFormCount());
        assertEquals(1, vo.getEmptyCount());
        assertEquals("검토중", vo.getReviewStatusLabel());
        assertEquals("2026-10-31 18:13", vo.getScanDtLabel());
    }

    @Test void pendingOrFailedTransactionHasNoEmptyForms() {
        OcrAgentData.ReviewTransaction row = new OcrAgentData.ReviewTransaction();
        row.setReviewStatus("PROCESSING");
        assertEquals(0, OcrReviewService.emptyFormCount(row, List.of(form("100"))));
        row.setReviewStatus("FAILED");
        row.setStatus("failed");
        assertEquals(0, OcrReviewService.emptyFormCount(row, List.of(form("100"))));
        row.setStatus("completed");
        row.setReviewStatus("NO_RESULT");
        assertEquals(1, OcrReviewService.emptyFormCount(row, List.of(form("100"))));
    }

    @Test void agentRejectionKeepsStatusAndGuidance() {
        when(agentData.saveValues(eq("t1"), eq("d1"), any())).thenThrow(
                new ResponseStatusException(HttpStatus.FORBIDDEN, "검증 담당자만 저장하거나 검증완료할 수 있습니다."));

        OcrReviewException e = assertThrows(OcrReviewException.class,
                () -> service.saveConfirmValues("wk", 10L, "t1", null, values(), reviewer("me", false)));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        assertEquals("검증 담당자만 저장하거나 검증완료할 수 있습니다.", e.getMessage());
    }

    @Test void agentFailureIsNotShownAsBusinessRejection() {
        when(agentData.saveValues(eq("t1"), eq("d1"), any())).thenThrow(new IllegalStateException("Agent down"));
        assertThrows(IllegalStateException.class, () -> service.saveConfirmValues("wk", 10L, "t1", null, values(), reviewer("me", false)));
    }

    @Test void portalChecksLoginDocumentAndTargetUserBeforeAgent() {
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(OcrReviewException.class,
                () -> service.assignTransaction("wk", "t1", "me", null)).getStatus());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(OcrReviewException.class,
                () -> service.saveConfirmValues("wk", 99L, "t1", null, values(), reviewer("me", false))).getStatus());
        when(reviewMapper.selectUserNm("ghost")).thenReturn(null);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(OcrReviewException.class,
                () -> service.assignTransaction("wk", "t1", "ghost", reviewer("lead", true))).getStatus());
        verifyNoInteractions(agentData);
    }

    @Test void failedDocumentIsNotReviewed() {
        OcrResultLefrmVO failed = new OcrResultLefrmVO();
        failed.setTransId("t1");
        failed.setDocId("d3");
        failed.setDocStatCd("failed");
        when(reqService.resolveDocument(11L, "wk", "t1", null)).thenReturn(failed);
        assertEquals(HttpStatus.CONFLICT, assertThrows(OcrReviewException.class,
                () -> service.verify("wk", 11L, "t1", null, values(), reviewer("me", false))).getStatus());
        verifyNoInteractions(agentData);
        verify(reqService, never()).sendVerifyResultToTerminal(any(), anyLong(), any(), any(), any(), any());
    }

    @Test void agentRejectionIsNotSentToTerminal() {
        when(reqService.resultForms("wk", "t1")).thenReturn(List.of());
        when(agentData.verify(eq("t1"), eq("d1"), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "이미 검증완료한 서식입니다."));
        assertEquals(HttpStatus.CONFLICT, assertThrows(OcrReviewException.class,
                () -> service.verify("wk", 10L, "t1", null, values(), reviewer("me", false))).getStatus());
        verify(reqService, never()).sendVerifyResultToTerminal(any(), anyLong(), any(), any(), any(), any());
    }

    @Test void verifySendsValuesAndFormsOfTransaction() {
        List<OcrAgentData.Form> forms = List.of(new OcrAgentData.Form("d1", 10L, "서식"));
        when(reqService.resultForms("wk", "t1")).thenReturn(forms);
        when(agentData.verify(eq("t1"), eq("d1"), any())).thenReturn(state("d1", "me"));

        service.verify("wk", 10L, "t1", null, values(), reviewer("me", false));

        ArgumentCaptor<OcrAgentData.VerifyRequest> request = ArgumentCaptor.forClass(OcrAgentData.VerifyRequest.class);
        verify(agentData).verify(eq("t1"), eq("d1"), request.capture());
        assertEquals("wk", request.getValue().workflowId());
        assertEquals(List.of(new OcrAgentData.ItemValue(1, "가")), request.getValue().fields());
        assertEquals(List.of(new OcrAgentData.ItemValue(3, "다")), request.getValue().cells());
        assertEquals(forms, request.getValue().forms());
        verify(reqService).sendVerifyResultToTerminal(eq("wk"), eq(10L), eq("t1"), any(), eq("me"), any());
    }

    @Test void cancelVerifyPassesActorAndDoesNotResendToTerminal() {
        when(agentData.cancelVerify(eq("t1"), eq("d1"), any())).thenReturn(state("d1", "me"));

        OcrReviewStateVO state = service.cancelVerify("wk", 10L, "t1", null, reviewer("lead", true));

        verify(agentData).cancelVerify("t1", "d1", new OcrAgentData.CancelVerifyRequest(new OcrAgentData.Actor("lead", true)));
        assertFalse(state.isVerified());
        verify(reqService, never()).sendVerifyResultToTerminal(any(), anyLong(), any(), any(), any(), any());
    }

    @Test void cancelVerifyRejectionKeepsGuidance() {
        when(agentData.cancelVerify(eq("t1"), eq("d1"), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "검증완료 취소는 검증한 사람이나 관리자·팀장만 할 수 있습니다."));
        OcrReviewException e = assertThrows(OcrReviewException.class,
                () -> service.cancelVerify("wk", 10L, "t1", null, reviewer("me", false)));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatus());
        assertEquals("검증완료 취소는 검증한 사람이나 관리자·팀장만 할 수 있습니다.", e.getMessage());
    }

    @Test void listStatesAreLoadedOnceForAllDocuments() {
        OcrResultWkGroupVO group = new OcrResultWkGroupVO();
        group.setTransId("t1");
        OcrResultLefrmVO lefrm = new OcrResultLefrmVO();
        lefrm.setTransId("t1");
        lefrm.setDocId("d1");
        group.getLefrmList().add(lefrm);
        when(agentData.reviewStates(any())).thenReturn(List.of(state("*", "lead"), state("d1", "lead")));

        service.applyReviewStates(List.of(group));

        verify(agentData, times(1)).reviewStates(List.of(new OcrAgentData.DocumentKey("t1", "*"),
                new OcrAgentData.DocumentKey("t1", "d1")));
        assertEquals("팀장", group.getTransAsgnUserNm());
        assertEquals("lead", lefrm.getReview().getAsgnUserId());
    }
}
