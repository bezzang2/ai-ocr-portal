package com.agilesoda.module.devicemock;

import com.agilesoda.common.response.entity.RestResponseEntity;
import com.agilesoda.common.rest.RestAgentProvider;
import net.sf.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 단말 mock: 설정으로만 켜지고, 화면 값을 Agent 판독요청 형식으로 넘기며, 수신함은 최근 건만 남긴다. */
class DeviceMockControllerTest {
    private final RestAgentProvider agent = mock(RestAgentProvider.class);
    private final DeviceMockController controller = new DeviceMockController(agent);

    DeviceMockControllerTest() {
        ReflectionTestUtils.setField(controller, "workflowListUri", "/api/v1/ocr/workflow/list");
        ReflectionTestUtils.setField(controller, "extractUri", "/api/v1/ocr/extract");
    }

    private static RestResponseEntity<Object> agentResponse(int status, String message, Object data) {
        RestResponseEntity<Object> res = new RestResponseEntity<>();
        res.setStatus(status);
        res.setMessage(message);
        res.setData(data);
        return res;
    }

    private static List<MultipartFile> image() {
        return List.of(new MockMultipartFile("attach_file", "sample.png", "image/png", new byte[]{1, 2, 3}));
    }

    @Test void registeredOnlyWhenEnabled() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withBean(RestAgentProvider.class, () -> agent)
                .withPropertyValues("aiocr.agent.apis.wkflow-list=/list")
                .withUserConfiguration(DeviceMockController.class);
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(DeviceMockController.class));
        runner.withPropertyValues("aiocr.device-mock.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(DeviceMockController.class));
        runner.withPropertyValues("aiocr.device-mock.enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(DeviceMockController.class));
    }

    @Test @SuppressWarnings("unchecked")
    void sendRelaysFieldsAndFillsDefaults() throws Exception {
        doReturn(agentResponse(200, "성공", Map.of("TRANS_LIST", List.of("t-1", "t-2"))))
                .when(agent).postMultipart(eq("/api/v1/ocr/extract"), any());

        ResponseEntity<?> response = controller.send(
                Map.of("scnpe_no", "26W0001", "scan_br_cd", "0001", "cus_nm", "테스트고객"),
                List.of("wf-1", "wf-2"), image());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<MultiValueMap<String, Object>> parts = ArgumentCaptor.forClass(MultiValueMap.class);
        verify(agent).postMultipart(eq("/api/v1/ocr/extract"), parts.capture());
        MultiValueMap<String, Object> sent = parts.getValue();
        assertEquals(List.of("wf-1", "wf-2"), sent.get("wkflow_id"));
        assertEquals("26W0001", sent.getFirst("scnpe_no"));
        assertEquals("MOCK", sent.getFirst("req_sys"));
        assertTrue(((String) sent.getFirst("req_guid")).startsWith("MOCK-"));
        assertTrue(((String) sent.getFirst("scan_dt")).matches("\\d{14}"));
        assertEquals("sample.png", ((Resource) sent.getFirst("attach_file")).getFilename());
        assertEquals(sent.getFirst("req_guid"), ((Map<String, Object>) response.getBody()).get("reqGuid"));
    }

    @Test void sendRejectsMissingInputWithoutCallingAgent() throws Exception {
        assertEquals(HttpStatus.BAD_REQUEST, controller.send(Map.of(), null, image()).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, controller.send(Map.of(), List.of("wf-1"), null).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, controller.send(Map.of(), List.of("wf-1"),
                List.of(new MockMultipartFile("attach_file", "empty.png", "image/png", new byte[0]))).getStatusCode());
        verify(agent, never()).postMultipart(any(), any());
    }

    @Test void sendHidesInternalErrors() throws Exception {
        doReturn(agentResponse(400, "필수 값 누락", null)).when(agent).postMultipart(any(), any());
        ResponseEntity<?> rejected = controller.send(Map.of(), List.of("wf-1"), image());
        assertEquals(HttpStatus.BAD_GATEWAY, rejected.getStatusCode());
        assertTrue(String.valueOf(rejected.getBody()).contains("필수 값 누락"));

        reset(agent);
        when(agent.postMultipart(any(), any())).thenThrow(new IllegalStateException("agent down"));
        ResponseEntity<?> failed = controller.send(Map.of(), List.of("wf-1"), image());
        assertEquals(HttpStatus.BAD_GATEWAY, failed.getStatusCode());
        assertFalse(String.valueOf(failed.getBody()).contains("agent down"));
    }

    @Test void workflowsReturnsIdAndName() throws Exception {
        doReturn(agentResponse(200, "성공", Map.of("total", 1, "items",
                List.of(Map.of("workflow_id", "wf-1", "name", "업무 1", "status", "active")))))
                .when(agent).get(eq("/api/v1/ocr/workflow/list"), any(JSONObject.class));
        ResponseEntity<?> response = controller.workflows();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(Map.of("workflowId", "wf-1", "name", "업무 1")), response.getBody());

        reset(agent);
        doReturn(agentResponse(500, "엔진 오류", null)).when(agent).get(any(), any(JSONObject.class));
        assertEquals(HttpStatus.BAD_GATEWAY, controller.workflows().getStatusCode());
    }

    @Test void inboxKeepsLatestEntriesFirst() {
        for (int i = 1; i <= DeviceMockController.INBOX_SIZE + 5; i++) {
            controller.receiveVerify(Map.of("transId", "t-" + i));
        }
        List<Map<String, Object>> inbox = controller.inbox();
        assertEquals(DeviceMockController.INBOX_SIZE, inbox.size());
        assertEquals(Map.of("transId", "t-" + (DeviceMockController.INBOX_SIZE + 5)), inbox.get(0).get("body"));
    }
}
