package com.agilesoda.module.devicemock;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.agilesoda.common.response.entity.RestResponseEntity;
import com.agilesoda.common.rest.RestAgentProvider;
import com.agilesoda.module.apply.vo.WkflowListVO;
import com.agilesoda.module.apply.vo.WkflowVO;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 단말(스캔프로그램) mock. 단말 대신 이미지를 올려 Agent 판독요청(/api/v1/ocr/extract)을 보내고,
 * 검증완료 때 포털이 단말로 보내는 결과(aiocr.terminal.verify-url)를 받아 보여 준다.
 * 개발·데모 전용이라 aiocr.device-mock.enabled=true 일 때만 등록한다(꺼지면 404).
 * 전송 형식은 단말 연계 규격 확정 전이라 지금 Agent 가 받는 형식을 따른다(agent/스캔요청.MD).
 */
@Slf4j
@Controller
@RequestMapping("/device-mock")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "aiocr.device-mock.enabled", havingValue = "true")
@Tag(name = "단말 mock", description = "개발용 판독요청 전송 및 검증완료 수신 확인")
public class DeviceMockController {

    /** 수신함에 남기는 최근 건수. 메모리에만 두므로 재기동하면 비워진다. */
    static final int INBOX_SIZE = 20;
    private static final int WORKFLOW_PAGE_SIZE = 100;
    private static final DateTimeFormatter SCAN_DT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ObjectMapper AGENT_JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final RestAgentProvider restAgentProvider;
    private final Deque<Map<String, Object>> inbox = new ConcurrentLinkedDeque<>();

    @Value("${aiocr.agent.apis.wkflow-list}")
    private String workflowListUri;

    @Value("${aiocr.agent.apis.extract:/api/v1/ocr/extract}")
    private String extractUri;

    @GetMapping
    public String page() {
        return "module/devicemock/device-mock";
    }

    /** 단말 ① 업무목록 조회. 엔진의 활성 업무를 이름과 ID 만 돌려준다. */
    @Operation(summary = "단말 mock 업무 목록 조회")
    @GetMapping("/workflows")
    @ResponseBody
    public ResponseEntity<?> workflows() {
        long started = System.currentTimeMillis();
        try {
            JSONObject query = new JSONObject();
            query.put("status", "active");
            query.put("page", 1);
            query.put("page_size", WORKFLOW_PAGE_SIZE);
            RestResponseEntity<?> res = restAgentProvider.get(workflowListUri, query);
            if (res.getStatus() != HttpStatus.OK.value()) {
                log.warn("[단말mock] 업무 목록 조회 실패 agentStatus={} message={}", res.getStatus(), res.getMessage());
                return message(HttpStatus.BAD_GATEWAY, "업무 목록을 가져오지 못했습니다.");
            }
            WkflowListVO page = AGENT_JSON.convertValue(res.getData(), WkflowListVO.class);
            List<Map<String, String>> items = new ArrayList<>();
            if (page != null && page.getItems() != null) {
                for (WkflowVO wf : page.getItems()) {
                    items.add(Map.of("workflowId", Objects.toString(wf.getWorkflowId(), ""),
                            "name", Objects.toString(wf.getName(), "")));
                }
            }
            log.info("[단말mock] 업무 목록 조회 완료 items={} elapsedMs={}", items.size(), System.currentTimeMillis() - started);
            return ResponseEntity.ok(items);
        } catch (Exception e) {
            log.error("[단말mock] 업무 목록 조회 오류 elapsedMs={}", System.currentTimeMillis() - started, e);
            return message(HttpStatus.BAD_GATEWAY, "업무 목록을 가져오지 못했습니다.");
        }
    }

    /**
     * 단말 ② 이미지 + 업무 전송. 화면 값을 Agent 판독요청 multipart 로 그대로 넘긴다.
     * 비운 req_guid·req_sys·scan_dt 는 mock 값으로 채운다.
     */
    @Operation(summary = "단말 mock 판독요청 전송")
    @PostMapping("/send")
    @ResponseBody
    public ResponseEntity<?> send(@RequestParam Map<String, String> params,
                                  @RequestParam(name = "wkflow_id", required = false) List<String> workflowIds,
                                  @RequestParam(name = "attach_file", required = false) List<MultipartFile> files) {
        if (workflowIds == null || workflowIds.isEmpty()) {
            return message(HttpStatus.BAD_REQUEST, "업무를 하나 이상 선택해 주세요.");
        }
        List<MultipartFile> attachments = files == null ? List.of() : files.stream().filter(f -> !f.isEmpty()).toList();
        if (attachments.isEmpty()) {
            return message(HttpStatus.BAD_REQUEST, "이미지를 하나 이상 선택해 주세요.");
        }

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        for (String name : List.of("scnpe_no", "scan_br_cd", "cus_nm", "cus_no", "apl_dscd", "sub_apl_dscd")) {
            parts.add(name, params.getOrDefault(name, "").trim());
        }
        String reqGuid = withDefault(params.get("req_guid"), "MOCK-" + UUID.randomUUID());
        parts.add("req_guid", reqGuid);
        parts.add("req_sys", withDefault(params.get("req_sys"), "MOCK"));
        parts.add("scan_dt", withDefault(params.get("scan_dt"), LocalDateTime.now().format(SCAN_DT)));
        workflowIds.forEach(id -> parts.add("wkflow_id", id));
        attachments.forEach(f -> parts.add("attach_file", f.getResource()));

        long started = System.currentTimeMillis();
        log.info("[단말mock] 판독요청 중계 시작 reqGuid={} workflows={} files={}", reqGuid, workflowIds.size(), attachments.size());
        try {
            RestResponseEntity<?> res = restAgentProvider.postMultipart(extractUri, parts);
            long elapsed = System.currentTimeMillis() - started;
            if (res.getStatus() != HttpStatus.OK.value()) {
                log.warn("[단말mock] 판독요청 거절 reqGuid={} agentStatus={} message={} elapsedMs={}",
                        reqGuid, res.getStatus(), res.getMessage(), elapsed);
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(result(reqGuid, res.getStatus(), Objects.toString(res.getMessage(), "판독요청이 거절되었습니다."), null));
            }
            log.info("[단말mock] 판독요청 중계 완료 reqGuid={} elapsedMs={}", reqGuid, elapsed);
            log.debug("[단말mock] 판독요청 응답 reqGuid={} data={}", reqGuid, res.getData());
            return ResponseEntity.ok(result(reqGuid, res.getStatus(), res.getMessage(), res.getData()));
        } catch (Exception e) {
            log.error("[단말mock] 판독요청 중계 오류 reqGuid={} elapsedMs={}", reqGuid, System.currentTimeMillis() - started, e);
            return message(HttpStatus.BAD_GATEWAY, "판독요청을 보내지 못했습니다.");
        }
    }

    /** 단말 ③ 검증완료 결과 수신(포털 OcrReqService 가 보내는 JSON). 최근 {@link #INBOX_SIZE}건만 남긴다. */
    @Operation(summary = "단말 mock 검증완료 수신")
    @PostMapping("/terminal/verify")
    @ResponseBody
    public ResponseEntity<Void> receiveVerify(@RequestBody Map<String, Object> body) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("receivedAt", LocalDateTime.now().withNano(0).toString());
        entry.put("body", body);
        inbox.addFirst(entry);
        while (inbox.size() > INBOX_SIZE) inbox.pollLast();
        log.info("[단말mock] 검증완료 결과 수신 wkId={} lefrmId={} transId={}",
                body.get("wkId"), body.get("lefrmId"), body.get("transId"));
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "단말 mock 검증완료 수신 내역 조회")
    @GetMapping("/terminal/inbox")
    @ResponseBody
    public List<Map<String, Object>> inbox() {
        return List.copyOf(inbox);
    }

    private static String withDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static Map<String, Object> result(String reqGuid, int agentStatus, String message, Object data) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reqGuid", reqGuid);
        body.put("agentStatus", agentStatus);
        body.put("message", message);
        body.put("data", data);
        return body;
    }

    private static ResponseEntity<Map<String, String>> message(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }
}
