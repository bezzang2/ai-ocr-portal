package com.agilesoda.module.apply.service;

import com.agilesoda.common.rest.RestAgentProvider;
import com.agilesoda.module.apply.mapper.AgentApiMapper;
import com.agilesoda.module.apply.vo.LefrmMatchVO;
import com.agilesoda.module.apply.vo.OcrWcVO;
import com.agilesoda.module.apply.vo.OcrWllVO;
import com.agilesoda.module.apply.vo.WkflowListVO;
import com.agilesoda.module.apply.vo.WkflowVO;
import com.agilesoda.module.apply.vo.WorkflowSyncResultVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** 동기화의 서식 매핑: 분류 클래스마다 서식을 찾아 달라진 업무만 다시 만들고, 달라진 클래스 사본만 저장한다. */
class WorkflowFormLinkTest {

    private static WkflowListVO one(List<WkflowVO.Classify> classes) {
        WkflowVO item = new WkflowVO();
        item.setWorkflowId("wf-1");
        item.setName("업무");
        item.setClasses(classes);
        WkflowListVO list = new WkflowListVO();
        list.setItems(List.of(item));
        return list;
    }

    private static LefrmMatchVO form(long id, String docId) {
        LefrmMatchVO vo = new LefrmMatchVO();
        vo.setLefrmId(id);
        vo.setDocId(docId);
        return vo;
    }

    private static final List<WkflowVO.Classify> THREE = List.of(new WkflowVO.Classify("사업자등록증", "KJB_BIZ_REG_CERT"),
            new WkflowVO.Classify("가족관계증명서", "a111"), new WkflowVO.Classify("등기사항전부증명서", null));

    private static AgentApiMapper twoMatching() {
        AgentApiMapper mapper = mock(AgentApiMapper.class);
        when(mapper.selectFormsForLink()).thenReturn(List.of(form(1, "BIZ_REG_CERT"), form(2, "a111")));
        return mapper;
    }

    @Test void rebuildsLinksFromClassesByCodeOnly() {
        AgentApiMapper mapper = twoMatching();
        AgentApiService service = new AgentApiService(mapper, mock(RestAgentProvider.class), null);

        WorkflowSyncResultVO result = service.syncWorkflowList(one(THREE), Map.of());

        verify(mapper).deleteOcrWllByWkId("wf-1");
        ArgumentCaptor<OcrWllVO> links = ArgumentCaptor.forClass(OcrWllVO.class);
        verify(mapper, times(2)).insertOcrWll(links.capture());
        assertEquals(List.of(1L, 2L), links.getAllValues().stream().map(OcrWllVO::getLefrmId).toList());
        assertEquals(1, result.getLinksChanged());
        assertEquals(1, result.getStored());
        assertEquals(3, result.getClasses());
        assertEquals(2, result.getLinked());
        assertEquals(1, result.getUnlinked());
    }

    @Test void storesEveryClassInOrderForTheScreen() {
        AgentApiMapper mapper = twoMatching();
        AgentApiService service = new AgentApiService(mapper, mock(RestAgentProvider.class), null);

        service.syncWorkflowList(one(THREE), Map.of());

        verify(mapper).deleteOcrWcByWkId("wf-1");
        verify(mapper).insertOcrWc("wf-1", 1, "사업자등록증", "KJB_BIZ_REG_CERT");
        verify(mapper).insertOcrWc("wf-1", 2, "가족관계증명서", "a111");
        verify(mapper).insertOcrWc("wf-1", 3, "등기사항전부증명서", null);
    }

    @Test void keepsLinksWhenClassTableIsMissing() {
        AgentApiMapper mapper = twoMatching();
        doThrow(new BadSqlGrammarException("deleteOcrWcByWkId", "DELETE", new SQLException("ORA-00942")))
                .when(mapper).deleteOcrWcByWkId(anyString());
        AgentApiService service = new AgentApiService(mapper, mock(RestAgentProvider.class), null);

        WorkflowSyncResultVO result = service.syncWorkflowList(one(THREE), Map.of());

        verify(mapper, times(2)).insertOcrWll(any());
        verify(mapper, never()).insertOcrWc(anyString(), anyInt(), anyString(), any());
        assertEquals(1, result.getStored());
        assertEquals(0, result.getFailed());
        assertEquals(1, result.getClassStoreFailed());
    }

    @Test void keepsExistingLinksWhenClassesUnknown() {
        AgentApiMapper mapper = mock(AgentApiMapper.class);
        AgentApiService service = new AgentApiService(mapper, mock(RestAgentProvider.class), null);

        service.syncWorkflowList(one(null), Map.of());

        verify(mapper).upsertOcrWfM(any());
        verify(mapper, never()).deleteOcrWllByWkId(any());
        verify(mapper, never()).insertOcrWll(any());
        verify(mapper, never()).deleteOcrWcByWkId(any());
    }

    private static OcrWllVO link(long id) {
        OcrWllVO vo = new OcrWllVO();
        vo.setWkId("wf-1");
        vo.setLefrmId(id);
        return vo;
    }

    private static final Map<String, AgentApiService.StoredClasses> STORED =
            Map.of("wf-1", new AgentApiService.StoredClasses("r1", THREE));

    @Test void writesNothingButWorkflowWhenUnchanged() {
        AgentApiMapper mapper = twoMatching();
        when(mapper.selectAllOcrWll()).thenReturn(List.of(link(2), link(1)));
        AgentApiService service = new AgentApiService(mapper, mock(RestAgentProvider.class), null);

        WorkflowSyncResultVO result = service.syncWorkflowList(one(THREE), STORED);

        verify(mapper).upsertOcrWfM(any());
        verify(mapper, never()).deleteOcrWllByWkId(any());
        verify(mapper, never()).insertOcrWll(any());
        verify(mapper, never()).deleteOcrWcByWkId(any());
        verify(mapper, never()).insertOcrWc(anyString(), anyInt(), anyString(), any());
        assertEquals(2, result.getLinked());
        assertEquals(1, result.getUnlinked());
        assertEquals(0, result.getLinksChanged());
    }

    @Test void relinksWhenFormIsNew() {
        AgentApiMapper mapper = twoMatching();
        // 서식 2 는 새로 등록돼 아직 연결이 없다
        when(mapper.selectAllOcrWll()).thenReturn(List.of(link(1)));
        AgentApiService service = new AgentApiService(mapper, mock(RestAgentProvider.class), null);

        WorkflowSyncResultVO result = service.syncWorkflowList(one(THREE), STORED);

        verify(mapper).deleteOcrWllByWkId("wf-1");
        verify(mapper, times(2)).insertOcrWll(any());
        verify(mapper, never()).deleteOcrWcByWkId(any()); // 클래스는 그대로
        assertEquals(1, result.getLinksChanged());
    }

    @Test void removesLinksWhenWorkflowLostItsClasses() {
        AgentApiMapper mapper = twoMatching();
        when(mapper.selectAllOcrWll()).thenReturn(List.of(link(1)));
        AgentApiService service = new AgentApiService(mapper, mock(RestAgentProvider.class), null);

        service.syncWorkflowList(one(List.of()), STORED);

        verify(mapper).deleteOcrWllByWkId("wf-1");
        verify(mapper, never()).insertOcrWll(any());
        verify(mapper).deleteOcrWcByWkId("wf-1");
    }

    @Test void picksLowestIdFormAndLinksItOnceForTwoClasses() {
        AgentApiMapper mapper = mock(AgentApiMapper.class);
        when(mapper.selectFormsForLink()).thenReturn(List.of(form(1, "50621004"), form(2, "KJB_50621004")));
        AgentApiService service = new AgentApiService(mapper, mock(RestAgentProvider.class), null);

        WorkflowSyncResultVO result = service.syncWorkflowList(one(List.of(
                new WkflowVO.Classify("가", "KJB_50621004"), new WkflowVO.Classify("나", "50621004"))), Map.of());

        ArgumentCaptor<OcrWllVO> links = ArgumentCaptor.forClass(OcrWllVO.class);
        verify(mapper).insertOcrWll(links.capture());
        assertEquals(1L, links.getValue().getLefrmId());
        assertEquals(2, result.getLinked());
    }

    @Test void clearsPartialCopyWhenClassInsertFails() {
        AgentApiMapper mapper = twoMatching();
        doThrow(new DataIntegrityViolationException("ORA-12899")).when(mapper)
                .insertOcrWc(anyString(), eq(2), anyString(), any());
        AgentApiService service = new AgentApiService(mapper, mock(RestAgentProvider.class), null);

        WorkflowSyncResultVO result = service.syncWorkflowList(one(THREE), Map.of());

        verify(mapper, times(2)).deleteOcrWcByWkId("wf-1");
        verify(mapper, times(2)).insertOcrWll(any());
        assertEquals(1, result.getClassStoreFailed());
    }

    @Test void readsStoredClassesInOrderAndToleratesMissingTable() {
        AgentApiMapper mapper = mock(AgentApiMapper.class);
        OcrWcVO a = new OcrWcVO(); a.setWkId("wf-1"); a.setActiveReleaseId("r1"); a.setClassName("가"); a.setDocTypeCode("A");
        OcrWcVO b = new OcrWcVO(); b.setWkId("wf-1"); b.setActiveReleaseId("r1"); b.setClassName("나");
        when(mapper.selectStoredClasses()).thenReturn(List.of(a, b));
        AgentApiService service = new AgentApiService(mapper, mock(RestAgentProvider.class), null);

        assertEquals(new AgentApiService.StoredClasses("r1", List.of(new WkflowVO.Classify("가", "A"), new WkflowVO.Classify("나", null))),
                service.getStoredClasses().get("wf-1"));

        doThrow(new BadSqlGrammarException("selectStoredClasses", "SELECT", new SQLException("ORA-00942")))
                .when(mapper).selectStoredClasses();
        assertTrue(service.getStoredClasses().isEmpty());
    }
}
