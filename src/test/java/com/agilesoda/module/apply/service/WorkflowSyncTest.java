package com.agilesoda.module.apply.service;

import com.agilesoda.common.response.entity.RestResponseEntity;
import com.agilesoda.common.rest.RestAgentProvider;
import com.agilesoda.module.apply.vo.WkflowListVO;
import com.agilesoda.module.apply.vo.WkflowVO;
import com.agilesoda.module.apply.vo.WorkflowSyncResultVO;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 워크플로우 동기화: Agent 응답(Map)을 변환하고 total 까지 페이지를 넘긴다. 한 번에 하나만 돈다. */
class WorkflowSyncTest {

    private static RestResponseEntity<Object> page(int status, int from, int count, int total) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = from; i < from + count; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("workflow_id", "wf-" + i);
            item.put("name", "업무 " + i);
            item.put("doc_type", null);
            item.put("status", "active");
            item.put("active_release_id", "r" + i);
            item.put("activated_at", "2026-09-29T15:40:10.185925+09:00");
            item.put("field_added_by_engine", "ignored");
            items.add(item);
        }
        RestResponseEntity<Object> res = new RestResponseEntity<>();
        res.setStatus(status);
        res.setData(Map.of("items", items, "page", 1, "page_size", 100, "total", total));
        return res;
    }

    private static AgentApiService storing() {
        AgentApiService service = mock(AgentApiService.class);
        when(service.syncWorkflowList(any(), any())).thenReturn(new WorkflowSyncResultVO());
        return service;
    }

    @Test void fetchesEveryPageUntilTotal() throws Exception {
        RestAgentProvider agent = mock(RestAgentProvider.class);
        AgentApiService service = storing();
        doReturn(page(200, 0, 100, 150), page(200, 100, 50, 150))
                .when(agent).get(eq("/api/v1/ocr/workflow/list"), any(JSONObject.class));
        doReturn(detail(200, List.of(cls("사업자등록증", null), cls("기타", "ETC"))))
                .when(agent).get(startsWith("/api/v1/ocr/workflow/wf-"), any(JSONObject.class));

        WorkflowSyncResultVO result = new WorkflowSyncService(agent, service).sync("test");

        ArgumentCaptor<WkflowListVO> synced = ArgumentCaptor.forClass(WkflowListVO.class);
        verify(service).syncWorkflowList(synced.capture(), any());
        assertEquals(150, synced.getValue().getItems().size());
        assertEquals("wf-149", synced.getValue().getItems().get(149).getWorkflowId());
        assertEquals(List.of(new WkflowVO.Classify("사업자등록증", null)), synced.getValue().getItems().get(0).getClasses());
        verify(agent, times(2)).get(eq("/api/v1/ocr/workflow/list"), any(JSONObject.class));
        assertEquals(150, result.getFetched());
        assertEquals(0, result.getDetailFailed());
    }

    @Test void keepsLinksWhenDetailFails() throws Exception {
        RestAgentProvider agent = mock(RestAgentProvider.class);
        AgentApiService service = storing();
        doReturn(page(200, 0, 1, 1)).when(agent).get(eq("/api/v1/ocr/workflow/list"), any(JSONObject.class));
        doReturn(detail(500, List.of())).when(agent).get(startsWith("/api/v1/ocr/workflow/wf-"), any(JSONObject.class));

        WorkflowSyncResultVO result = new WorkflowSyncService(agent, service).sync("test");

        ArgumentCaptor<WkflowListVO> synced = ArgumentCaptor.forClass(WkflowListVO.class);
        verify(service).syncWorkflowList(synced.capture(), any());
        assertNull(synced.getValue().getItems().get(0).getClasses());
        assertEquals(1, result.getDetailFailed());
    }

    @Test void readsClassesFromWorkflowIr() {
        Object data = detail(200, List.of(cls("가족관계증명서", "a111"), cls("주민등록표등본", ""), cls("기타", "ETC"))).getData();
        assertEquals(List.of(new WkflowVO.Classify("가족관계증명서", "a111"), new WkflowVO.Classify("주민등록표등본", null)),
                WorkflowSyncService.classesOf(data));
        assertEquals(List.of(), WorkflowSyncService.classesOf(Map.of("name", "no ir")));
    }

    private static Map<String, Object> cls(String name, String code) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("class_name", name);
        c.put("doc_type_code", code);
        return c;
    }

    private static RestResponseEntity<Object> detail(int status, List<Map<String, Object>> classes) {
        RestResponseEntity<Object> res = new RestResponseEntity<>();
        res.setStatus(status);
        res.setData(Map.of("workflow_ir", Map.of("steps", List.of(
                Map.of("config", Map.of("skill_type", "parse")),
                Map.of("config", Map.of("skill_type", "classify", "classes", classes))))));
        return res;
    }

    @Test void skipsSyncWhenAgentFails() throws Exception {
        RestAgentProvider agent = mock(RestAgentProvider.class);
        AgentApiService service = storing();
        RestResponseEntity<Object> failed = new RestResponseEntity<>();
        failed.setStatus(500);
        doReturn(failed).when(agent).get(anyString(), any(JSONObject.class));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> new WorkflowSyncService(agent, service).sync("test"));

        assertEquals(HttpStatus.BAD_GATEWAY, e.getStatusCode());
        verify(service, never()).syncWorkflowList(any(), any());
    }

    @Test void rejectsSecondSyncWhileRunningAndReleasesAfter() throws Exception {
        RestAgentProvider agent = mock(RestAgentProvider.class);
        AgentApiService service = storing();
        AtomicReference<WorkflowSyncService> self = new AtomicReference<>();
        AtomicReference<Exception> nested = new AtomicReference<>();
        doAnswer(inv -> {
            // 목록을 받는 도중(잠금 보유 중)에 같은 스레드에서 다시 동기화를 시도한다.
            try { self.get().sync("nested"); } catch (Exception e) { nested.set(e); }
            return page(200, 0, 0, 0);
        }).when(agent).get(eq("/api/v1/ocr/workflow/list"), any(JSONObject.class));
        WorkflowSyncService sync = new WorkflowSyncService(agent, service);
        self.set(sync);

        sync.sync("first");

        assertInstanceOf(ResponseStatusException.class, nested.get());
        assertEquals(HttpStatus.CONFLICT, ((ResponseStatusException) nested.get()).getStatusCode());

        doReturn(page(200, 0, 0, 0)).when(agent).get(eq("/api/v1/ocr/workflow/list"), any(JSONObject.class));
        assertNotNull(sync.sync("after"));
    }

    @Test void scheduledSyncSwallowsFailures() throws Exception {
        RestAgentProvider agent = mock(RestAgentProvider.class);
        doThrow(new IllegalStateException("down")).when(agent).get(anyString(), any(JSONObject.class));

        assertDoesNotThrow(() -> new WorkflowSyncService(agent, storing()).scheduledSync());
    }

    /** 업무 1건, 저장 사본의 릴리스는 r0 */
    private static AgentApiService storedAs(String releaseId) {
        AgentApiService service = storing();
        when(service.getStoredClasses()).thenReturn(Map.of("wf-0",
                new AgentApiService.StoredClasses(releaseId, List.of(new WkflowVO.Classify("저장된 클래스", "A")))));
        return service;
    }

    private static RestAgentProvider oneWorkflow(RestResponseEntity<Object> detail) throws Exception {
        RestAgentProvider agent = mock(RestAgentProvider.class);
        doReturn(page(200, 0, 1, 1)).when(agent).get(eq("/api/v1/ocr/workflow/list"), any(JSONObject.class));
        doReturn(detail).when(agent).get(startsWith("/api/v1/ocr/workflow/wf-"), any(JSONObject.class));
        return agent;
    }

    private static WkflowVO lastSynced(AgentApiService service, int times) {
        ArgumentCaptor<WkflowListVO> synced = ArgumentCaptor.forClass(WkflowListVO.class);
        verify(service, times(times)).syncWorkflowList(synced.capture(), any());
        return synced.getValue().getItems().get(0);
    }

    @Test void scheduledRunSkipsDetailWhenReleaseUnchangedExceptFirstOfDay() throws Exception {
        RestAgentProvider agent = oneWorkflow(detail(200, List.of(cls("새 클래스", "B"))));
        AgentApiService service = storedAs("r0");
        WorkflowSyncService sync = new WorkflowSyncService(agent, service);

        sync.sync("schedule"); // 그날 첫 회차: 전체 상세 조회
        assertEquals("새 클래스", lastSynced(service, 1).getClasses().get(0).className());
        verify(agent, times(1)).get(startsWith("/api/v1/ocr/workflow/wf-"), any(JSONObject.class));

        WorkflowSyncResultVO result = sync.sync("schedule"); // 같은 날: 사본 사용
        assertEquals("저장된 클래스", lastSynced(service, 2).getClasses().get(0).className());
        verify(agent, times(1)).get(startsWith("/api/v1/ocr/workflow/wf-"), any(JSONObject.class));
        assertEquals(1, result.getDetailSkipped());

        sync.lastFullDetailDate = LocalDate.now().minusDays(1); // 다음 날
        sync.sync("schedule");
        verify(agent, times(2)).get(startsWith("/api/v1/ocr/workflow/wf-"), any(JSONObject.class));
    }

    @Test void manualRunAlwaysFetchesDetail() throws Exception {
        RestAgentProvider agent = oneWorkflow(detail(200, List.of()));
        WorkflowSyncService sync = new WorkflowSyncService(agent, storedAs("r0"));
        sync.lastFullDetailDate = LocalDate.now();

        sync.sync("U1");

        verify(agent).get(startsWith("/api/v1/ocr/workflow/wf-"), any(JSONObject.class));
    }

    @Test void fetchesDetailWhenReleaseChanged() throws Exception {
        RestAgentProvider agent = oneWorkflow(detail(200, List.of(cls("새 클래스", "B"))));
        AgentApiService service = storedAs("r-old");
        WorkflowSyncService sync = new WorkflowSyncService(agent, service);
        sync.lastFullDetailDate = LocalDate.now();

        sync.sync("schedule");

        WkflowVO item = lastSynced(service, 1);
        assertEquals("새 클래스", item.getClasses().get(0).className());
        assertEquals("r0", item.getActiveReleaseId());
    }

    @Test void keepsOldReleaseWhenDetailOfNewReleaseFails() throws Exception {
        RestAgentProvider agent = oneWorkflow(detail(500, List.of()));
        AgentApiService service = storedAs("r-old");
        WorkflowSyncService sync = new WorkflowSyncService(agent, service);
        sync.lastFullDetailDate = LocalDate.now();

        sync.sync("schedule");

        WkflowVO item = lastSynced(service, 1);
        assertNull(item.getClasses());
        assertEquals("r-old", item.getActiveReleaseId()); // 다음 회차에 다시 받는다
    }

    @Test void failedFullRunIsRetriedNextCycle() throws Exception {
        RestAgentProvider agent = oneWorkflow(detail(200, List.of()));
        AgentApiService service = storedAs("r0");
        when(service.syncWorkflowList(any(), any())).thenThrow(new IllegalStateException("db"));
        WorkflowSyncService sync = new WorkflowSyncService(agent, service);

        assertThrows(IllegalStateException.class, () -> sync.sync("schedule"));

        assertNull(sync.lastFullDetailDate);
    }
}
