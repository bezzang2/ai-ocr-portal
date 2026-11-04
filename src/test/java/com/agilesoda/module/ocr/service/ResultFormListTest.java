package com.agilesoda.module.ocr.service;

import com.agilesoda.common.rest.RestAgentProvider;
import com.agilesoda.module.ocr.mapper.OcrReqMapper;
import com.agilesoda.module.ocr.vo.OcrAgentData;
import com.agilesoda.module.ocr.vo.OcrResultLefrmVO;
import com.agilesoda.module.ocr.vo.OcrResultWkGroupVO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 판독결과 서식목록: 업무·서식(Portal)과 Agent 의 최근 판독작업·문서를 맞춰
 * 판독한 업무만 표시하고 서식을 결과·결과 없음·실패로 나눈다.
 */
class ResultFormListTest {
    private final OcrReqMapper mapper = mock(OcrReqMapper.class);
    private final OcrAgentDataClient agentData = mock(OcrAgentDataClient.class);
    private final OcrReqService service = new OcrReqService(mapper, mock(RestAgentProvider.class), agentData);

    private static OcrResultWkGroupVO group(String wkId) {
        OcrResultWkGroupVO g = new OcrResultWkGroupVO();
        g.setWkId(wkId);
        return g;
    }

    /** 업무(wkId)에 연결된 서식. formId 는 서식 DOC_ID(문서 종류 코드와 맞춘다) */
    private static OcrResultLefrmVO form(String wkId, long lefrmId, String formId, String lefrmNm) {
        OcrResultLefrmVO f = new OcrResultLefrmVO();
        f.setWkId(wkId);
        f.setLefrmId(lefrmId);
        f.setFormId(formId);
        f.setLefrmNm(lefrmNm);
        return f;
    }

    private static OcrAgentData.Transaction transaction(String wkId, String transId, String status, OcrAgentData.Document... documents) {
        OcrAgentData.Transaction t = new OcrAgentData.Transaction();
        t.setWorkflowId(wkId);
        t.setTransactionId(transId);
        t.setStatus(status);
        t.setDocuments(new ArrayList<>(Arrays.asList(documents)));
        return t;
    }

    private static OcrAgentData.Document doc(String docId, String typeCode, String typeName, String status) {
        OcrAgentData.Document d = new OcrAgentData.Document();
        d.setDocumentId(docId);
        d.setTypeCode(typeCode);
        d.setTypeName(typeName);
        d.setStatus(status);
        return d;
    }

    private static List<Long> ids(List<OcrResultLefrmVO> forms) {
        return forms.stream().map(OcrResultLefrmVO::getLefrmId).toList();
    }

    @Test void splitsFormsIntoResultEmptyAndFailed() {
        when(mapper.selectWkGroupList(null)).thenReturn(List.of(
                group("done"), group("no-doc"), group("waiting"), group("failed"), group("no-trans")));
        when(mapper.selectWkFormList(null)).thenReturn(List.of(
                form("done", 1L, "A", "가"), form("done", 2L, "B", "나"), form("done", 3L, "C", "다"),
                form("no-doc", 4L, "A", "가"), form("waiting", 5L, "A", "가"),
                form("failed", 6L, "A", "가"), form("no-trans", 7L, "A", "가")));
        when(agentData.latestTransactions(null, null, 3)).thenReturn(List.of(
                transaction("done", "t1", "partially_completed",
                        doc("d1", "A", "가", "pass"), doc("d3", "C", "다", "failed"), doc("d9", "Z", "기타", "canceled")),
                transaction("no-doc", "t2", "completed"),
                transaction("waiting", "t3", "timeout"),
                transaction("failed", "t4", "failed")));

        List<OcrResultWkGroupVO> shown = service.getResultWkGroupList(null, null);

        assertEquals(List.of("done", "no-doc", "waiting", "failed"), shown.stream().map(OcrResultWkGroupVO::getWkId).toList());

        OcrResultWkGroupVO done = shown.get(0);
        assertEquals("t1", done.getTransId());
        assertEquals(List.of(1L), ids(done.getLefrmList()));
        assertEquals("d1", done.getLefrmList().get(0).getDocId());
        assertEquals("t1", done.getLefrmList().get(0).getTransId());
        assertEquals(List.of(2L), ids(done.getEmptyLefrmList()));
        assertEquals(Arrays.asList(3L, null), ids(done.getFailedLefrmList())); // 서식에 맞는 실패 + 맞는 서식이 없는 취소 문서
        assertEquals("d9", done.getFailedLefrmList().get(1).getDocId());
        assertEquals("기타", done.getFailedLefrmList().get(1).getLefrmNm());
        assertEquals(4, done.getTotalCount());

        OcrResultWkGroupVO noDoc = shown.get(1);
        assertEquals(List.of(4L), ids(noDoc.getEmptyLefrmList()));

        OcrResultWkGroupVO waiting = shown.get(2);
        assertTrue(waiting.isPending());
        assertTrue(waiting.getEmptyLefrmList().isEmpty());
        assertEquals(0, waiting.getTotalCount());

        OcrResultWkGroupVO failed = shown.get(3);
        assertTrue(failed.isTransFailed());
        assertEquals(List.of(6L), ids(failed.getFailedLefrmList())); // 판독작업 실패: 연결 서식은 모두 실패
    }

    @Test void documentMatchesFormByCodeOnly() {
        OcrResultLefrmVO byCode = form("wk", 1L, "INCOME", "소득증명");
        OcrResultLefrmVO nameOnly = form("wk", 2L, "OTHER", "주민등록등본");
        // 코드로만 맞춘다. 같은 서식에 맞는 문서가 여럿이면 응답 순서(순서·문서 ID)의 첫 문서다.
        OcrAgentData.Transaction t = transaction("wk", "t1", "completed",
                doc("d0", "X", "소득증명", "pass"), doc("d1", "INCOME", "다른이름", "pass"),
                doc("d2", "INCOME", "소득증명", "pass"), doc("d3", null, "주민등록등본", "pass"));

        OcrReqService.bindDocument(byCode, t);
        OcrReqService.bindDocument(nameOnly, t);

        assertEquals("d1", byCode.getDocId());
        assertNull(nameOnly.getDocId()); // 이름이 같아도 코드가 없으면 연결하지 않는다
        assertFalse(OcrReqService.matches(doc("d", null, null, "pass"), nameOnly));
        assertFalse(OcrReqService.matches(doc("d", "", "주민등록등본", "pass"), nameOnly));
    }

    @Test void engineCodePrefixMatchesBankFormCode() {
        OcrResultLefrmVO form = form("wk", 15L, "50621004", "적합성 확인서");
        assertTrue(OcrReqService.matches(doc("d1", "KJB_50621004", "적합성 확인서", "corrected"), form));
        assertTrue(OcrReqService.matches(doc("d2", "50621004", null, "pass"), form));
        assertFalse(OcrReqService.matches(doc("d3", "KJB_88871002", "적합성 확인서", "pass"), form));
        assertFalse(OcrReqService.matches(doc("d4", "XKJB_50621004", null, "pass"), form));
        assertFalse(OcrReqService.sameFormCode("KJB_", ""));
    }

    @Test void workflowScreenShowsEveryFormLinkedToThatWorkflow() {
        // 서식 마스터(LEFRM_M)에는 업무 컬럼이 없다. 업무 화면의 서식은 업무-서식 연결(OCRWL_L) 전체다.
        when(mapper.selectWkGroupList("wk")).thenReturn(List.of(group("wk")));
        when(mapper.selectWkFormList("wk")).thenReturn(List.of(form("wk", 1L, "A", "가"), form("wk", 2L, "B", "나")));
        when(agentData.latestTransactions("wk", null, 3)).thenReturn(List.of(
                transaction("wk", "t1", "completed", doc("d1", "A", "가", "pass"), doc("d2", "B", "나", "failed"))));

        OcrResultWkGroupVO group = service.getResultWkGroupList("wk", null).get(0);

        assertEquals(List.of(1L), ids(group.getLefrmList()));
        assertEquals(List.of(2L), ids(group.getFailedLefrmList()));
    }

    @Test void upperCaseWorkflowIdFindsLowerCaseWorkflow() {
        // 업무·서식 연결은 엔진 형식(소문자) ID 다. 단말 화면이 대문자 wkId 로 열어도 같은 업무를 찾는다.
        when(mapper.selectWkGroupList("wk-a")).thenReturn(List.of(group("wk-a")));
        when(mapper.selectWkFormList("wk-a")).thenReturn(List.of(form("wk-a", 1L, "A", "가")));
        when(agentData.latestTransactions("wk-a", "t1", 1)).thenReturn(List.of(
                transaction("wk-a", "t1", "completed", doc("d1", "A", "가", "pass"))));

        List<OcrResultWkGroupVO> groups = service.getResultWkGroupList("WK-A", "t1");

        assertEquals(List.of(1L), ids(groups.get(0).getLefrmList()));
    }

    @Test void recentTransactionsOfOneWorkflowBecomeSeparateGroups() {
        // 업무마다 최근 판독작업 3건을 묶음 1개씩. 같은 서식 행이라도 판독작업마다 따로 문서를 붙인다.
        when(mapper.selectWkGroupList(null)).thenReturn(List.of(group("wk")));
        when(mapper.selectWkFormList(null)).thenReturn(List.of(form("wk", 1L, "A", "가")));
        OcrAgentData.Transaction newest = transaction("wk", "t3", "completed", doc("d3", "ETC", "기타", "corrected"));
        newest.setScanDateTime("20261029150000");
        when(agentData.latestTransactions(null, null, 3)).thenReturn(List.of(newest,
                transaction("wk", "t2", "completed", doc("d2", "A", "가", "pass")),
                transaction("wk", "t1", "running")));

        List<OcrResultWkGroupVO> shown = service.getResultWkGroupList(null, null);

        assertEquals(List.of("t3", "t2", "t1"), shown.stream().map(OcrResultWkGroupVO::getTransId).toList());
        assertEquals(List.of(1L), ids(shown.get(0).getEmptyLefrmList()));
        assertEquals("2026-10-29 15:00", shown.get(0).getScanDtLabel());
        assertEquals("d2", shown.get(1).getLefrmList().get(0).getDocId());
        assertEquals("t2", shown.get(1).getLefrmList().get(0).getTransId());
        assertTrue(shown.get(2).isPending());
        assertNotSame(shown.get(0).getEmptyLefrmList().get(0), shown.get(1).getLefrmList().get(0));
    }

    @Test void transactionFilterKeepsOnlyItsWorkflow() {
        when(mapper.selectWkGroupList(null)).thenReturn(List.of(group("a"), group("b")));
        when(mapper.selectWkFormList(null)).thenReturn(List.of(form("a", 1L, "A", "가"), form("b", 2L, "A", "가")));
        when(agentData.latestTransactions(null, "t9", 1)).thenReturn(List.of(
                transaction("b", "t9", "completed", doc("d1", "A", "가", "pass"))));

        List<OcrResultWkGroupVO> shown = service.getResultWkGroupList(null, "t9");

        assertEquals(List.of("b"), shown.stream().map(OcrResultWkGroupVO::getWkId).toList());
        assertEquals("t9", shown.get(0).getLefrmList().get(0).getTransId());
    }

    @Test void resolveDocumentPicksRequestedDocumentOfSameFormOnly() {
        when(mapper.selectWkForm(1L, "wk")).thenAnswer(inv -> form("wk", 1L, "A", "가"));
        when(agentData.latestTransactions("wk", "t1")).thenReturn(List.of(transaction("wk", "t1", "completed",
                doc("d1", "A", "가", "pass"), doc("d2", "A", "가", "pass"), doc("d3", "B", "나", "pass"))));

        assertEquals("d1", service.resolveDocument(1L, "wk", "t1", null).getDocId());
        assertEquals("d2", service.resolveDocument(1L, "wk", "t1", "d2").getDocId());
        assertNull(service.resolveDocument(1L, "wk", "t1", "d3"));    // 다른 서식의 문서
        assertNull(service.resolveDocument(1L, "wk", "t1", "other")); // 다른 판독작업의 문서
    }

    @Test void resolveDocumentNeedsLinkedFormAndMatchingDocument() {
        when(mapper.selectWkForm(1L, "wk")).thenAnswer(inv -> form("wk", 1L, "A", "가")); // 매 조회 새 행
        when(agentData.latestTransactions("wk", "t1")).thenReturn(List.of(
                transaction("wk", "t1", "completed", doc("d1", "A", "가", "pass"))));

        OcrResultLefrmVO key = service.resolveDocument(1L, "wk", "t1");
        assertEquals("t1", key.getTransId());
        assertEquals("d1", key.getDocId());

        assertNull(service.resolveDocument(2L, "wk", "t1"));
        when(agentData.latestTransactions("wk", "t2")).thenReturn(List.of());
        assertNull(service.resolveDocument(1L, "wk", "t2"));
    }

    @Test
    void 같은_서식이_여러_건이면_문서마다_행을_만든다() {
        OcrResultLefrmVO linked = form("wk", 1L, "F", "가");
        List<OcrResultLefrmVO> rows = OcrReqService.documentRows(linked,
                transaction("wk", "t1", "completed", doc("d1", "F", "가", "pass"), doc("x", "OTHER", "나", "pass"), doc("d2", "KJB_F", "가", "pass")));
        assertEquals(List.of("d1", "d2"), rows.stream().map(OcrResultLefrmVO::getDocId).toList());
        assertEquals("(1/2)", rows.get(0).getDocLabel());
        assertEquals("(2/2)", rows.get(1).getDocLabel());
        // 한 건이면 표시가 없고, 문서가 없어도 빈 행 하나를 둔다
        assertEquals("", OcrReqService.documentRows(linked, transaction("wk", "t1", "completed", doc("d1", "F", "가", "pass"))).get(0).getDocLabel());
        assertNull(OcrReqService.documentRows(linked, transaction("wk", "t1", "completed")).get(0).getDocId());
    }

    @Test void resultFormsSkipFormsWithoutDocumentOrFailed() {
        when(mapper.selectWkFormList("wk")).thenReturn(List.of(
                form("wk", 1L, "A", "가"), form("wk", 2L, "B", "나"), form("wk", 3L, "C", "다")));
        when(agentData.latestTransactions("wk", "t1")).thenReturn(List.of(
                transaction("wk", "t1", "completed", doc("d1", "A", "가", "pass"), doc("d3", "C", "다", "failed"))));

        List<OcrAgentData.Form> forms = service.resultForms("wk", "t1");

        assertEquals(List.of(new OcrAgentData.Form("d1", 1L, "가")), forms);
    }
}
