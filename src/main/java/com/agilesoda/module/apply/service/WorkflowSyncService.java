package com.agilesoda.module.apply.service;

import com.agilesoda.common.response.entity.RestResponseEntity;
import com.agilesoda.common.rest.RestAgentProvider;
import com.agilesoda.module.apply.vo.WkflowListVO;
import com.agilesoda.module.apply.vo.WkflowVO;
import com.agilesoda.module.apply.vo.WorkflowSyncResultVO;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * 엔진 워크플로(업무) 동기화. Agent 에서 활성 워크플로 목록과 상세(분류 클래스)를 받아 AgentApiService 로 DB에 반영한다.
 * 정기 실행(cron)과 업무·서식 연결 관리 화면의 즉시 동기화가 같은 메서드를 쓰고, 한 번에 하나만 돈다.
 * 활성 워크플로는 수정할 수 없어 릴리스가 그대로면 분류 클래스도 그대로다. 정기 실행은 릴리스가 바뀐 업무만 상세를 받고,
 * 혹시 모를 어긋남은 하루 첫 회차와 즉시 동기화의 전체 상세 조회가 바로잡는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowSyncService {

    /** 엔진 워크플로우 목록 조회 페이지 크기. 전체 건수(total)까지 페이지를 넘기며 모두 동기화한다. */
    private static final int WORKFLOW_SYNC_PAGE_SIZE = 100;
    /** 엔진 응답 이상으로 끝없이 돌지 않게 하는 상한 */
    private static final int WORKFLOW_SYNC_MAX_PAGES = 50;
    /** 엔진이 자동으로 넣는 분류. 추출하지 않으므로 서식과 연결하지 않는다. */
    private static final String OTHER_CLASS = "기타";
    private static final String TRIGGER_SCHEDULE = "schedule";
    private static final ObjectMapper AGENT_JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final RestAgentProvider restAgentProvider;
    private final AgentApiService agentApiService;
    /** 업무별 서식 연결을 지우고 다시 만들므로 두 동기화가 겹치면 연결이 섞인다. 같은 스레드의 재진입도 막는다. */
    private final Semaphore running = new Semaphore(1);
    /** 마지막으로 전체 상세 조회를 한 날. running 잠금 안에서만 읽고 쓴다. */
    LocalDate lastFullDetailDate;

    //  기본: 5분마다 실행. 엔진에 만든 업무·분류 클래스가 곧 업무·서식 연결에 반영되게 한다. aiocr.workflow-sync.cron 으로 바꾼다.
    @Scheduled(cron = "${aiocr.workflow-sync.cron:0 */5 * * * *}")
    public void scheduledSync() {
        try {
            sync(TRIGGER_SCHEDULE);
        } catch (ResponseStatusException e) {
            log.warn("[워크플로우 동기화] 정기 실행 건너뜀 status={} reason={}", e.getStatusCode().value(), e.getReason());
        } catch (Exception e) {
            log.error("[워크플로우 동기화] 정기 실행 실패", e);
        }
    }

    /**
     * 동기화 1회. 이미 도는 중이면 409, 목록을 끝까지 받지 못하면 502(이번 동기화는 반영하지 않음).
     * @param trigger 로그용 실행 주체(schedule 또는 사용자 ID)
     */
    public WorkflowSyncResultVO sync(String trigger) throws Exception {
        if (!running.tryAcquire()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 동기화 중입니다. 잠시 후 다시 확인해 주세요.");
        }
        try {
            return syncLocked(trigger);
        } finally {
            running.release();
        }
    }

    private WorkflowSyncResultVO syncLocked(String trigger) throws Exception {
        log.info("[워크플로우 동기화] 시작 trigger={}", trigger);
        long started = System.currentTimeMillis();
        String uri = "/api/v1/ocr/workflow/list";

        List<WkflowVO> items = new ArrayList<>();
        int total = 0;
        for (int page = 1; page <= WORKFLOW_SYNC_MAX_PAGES; page++) {
            JSONObject jobj = new JSONObject();
            jobj.put("status", "active");
            jobj.put("page", page);
            jobj.put("page_size", WORKFLOW_SYNC_PAGE_SIZE);

            RestResponseEntity<?> resData = restAgentProvider.get(uri, jobj);
            if (resData.getStatus() != HttpStatus.OK.value()) {
                // 일부 페이지만 반영하면 목록이 어긋나므로 이번 동기화는 건너뛰고 다음 주기에 다시 한다.
                log.warn("[워크플로우 동기화] 중단 trigger={} agentStatus={} page={} message={}",
                        trigger, resData.getStatus(), page, resData.getMessage());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "워크플로우 목록을 가져오지 못했습니다.");
            }
            WkflowListVO pageList = AGENT_JSON.convertValue(resData.getData(), WkflowListVO.class);
            List<WkflowVO> pageItems = pageList == null || pageList.getItems() == null ? List.of() : pageList.getItems();
            items.addAll(pageItems);
            total = pageList == null ? items.size() : pageList.getTotal();
            log.debug("[워크플로우 동기화] 페이지 수신 page={} items={} total={}", page, pageItems.size(), total);
            if (pageItems.isEmpty() || items.size() >= total) break;
        }

        // 서식 매핑에 쓰는 분류 클래스는 상세(workflow_ir)에만 있다. DB 트랜잭션 밖에서 먼저 가져온다.
        LocalDate today = LocalDate.now();
        boolean fullDetail = !TRIGGER_SCHEDULE.equals(trigger) || !today.equals(lastFullDetailDate);
        Map<String, AgentApiService.StoredClasses> stored = agentApiService.getStoredClasses();
        int detailFailed = 0;
        int detailSkipped = 0;
        for (WkflowVO item : items) {
            AgentApiService.StoredClasses known = stored.get(item.getWorkflowId());
            if (!fullDetail && known != null && item.getActiveReleaseId() != null
                    && item.getActiveReleaseId().equals(known.releaseId())) {
                item.setClasses(known.classes());
                detailSkipped++;
                continue;
            }
            item.setClasses(fetchClasses(item.getWorkflowId()));
            if (item.getClasses() == null) {
                detailFailed++;
                // 새 릴리스를 저장하면 다음 회차가 옛 사본을 최신으로 착각한다. 옛 릴리스를 남겨 다시 받게 한다.
                if (known != null) item.setActiveReleaseId(known.releaseId());
            }
        }
        long detailDone = System.currentTimeMillis();

        WkflowListVO all = new WkflowListVO();
        all.setItems(items);
        all.setTotal(items.size());
        WorkflowSyncResultVO result = agentApiService.syncWorkflowList(all, stored);
        if (fullDetail) lastFullDetailDate = today;
        result.setFetched(items.size());
        result.setDetailFailed(detailFailed);
        result.setDetailSkipped(detailSkipped);
        long finished = System.currentTimeMillis();
        result.setElapsedMs(finished - started);

        log.info("[워크플로우 동기화] 완료 trigger={} fetched={} total={} fullDetail={} detailSkipped={} detailFailed={} engineMs={} dbMs={} elapsedMs={}",
                trigger, items.size(), total, fullDetail, detailSkipped, detailFailed, detailDone - started, finished - detailDone, result.getElapsedMs());
        return result;
    }

    /** 워크플로우 상세의 분류 클래스. 가져오지 못하면 null(호출부는 기존 서식 매핑을 유지한다). */
    private List<WkflowVO.Classify> fetchClasses(String workflowId) {
        try {
            RestResponseEntity<?> res = restAgentProvider.get("/api/v1/ocr/workflow/" + workflowId, new JSONObject());
            if (res.getStatus() != HttpStatus.OK.value()) {
                log.warn("[워크플로우 동기화] 상세 조회 건너뜀 wkId={} agentStatus={} message={}", workflowId, res.getStatus(), res.getMessage());
                return null;
            }
            return classesOf(res.getData());
        } catch (Exception e) {
            log.warn("[워크플로우 동기화] 상세 조회 실패, 기존 서식 매핑 유지 wkId={}", workflowId, e);
            return null;
        }
    }

    /** workflow_ir.steps[].config(classify).classes[] 에서 '기타'를 뺀 클래스명·영문코드. */
    static List<WkflowVO.Classify> classesOf(Object detail) {
        List<WkflowVO.Classify> classes = new ArrayList<>();
        for (JsonNode step : AGENT_JSON.valueToTree(detail).path("workflow_ir").path("steps")) {
            JsonNode config = step.path("config");
            if (!"classify".equals(config.path("skill_type").asText())) continue;
            for (JsonNode cls : config.path("classes")) {
                String name = cls.path("class_name").asText("");
                if (name.isBlank() || OTHER_CLASS.equals(name)) continue;
                String code = cls.path("doc_type_code").asText("");
                classes.add(new WkflowVO.Classify(name, code.isBlank() ? null : code));
            }
        }
        return classes;
    }
}
