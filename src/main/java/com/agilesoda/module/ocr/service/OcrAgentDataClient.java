package com.agilesoda.module.ocr.service;

import com.agilesoda.common.response.entity.RestResponseEntity;
import com.agilesoda.common.rest.RestAgentProvider;
import com.agilesoda.module.ocr.vo.OcrAgentData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent OCR 데이터 API(/api/v1/ocr/data) 호출. Portal 은 OCR 테이블을 직접 읽지 않고 이 클래스로만 OCR 데이터를 다룬다.
 * <ul>
 *   <li>Agent 가 거절한 요청(4xx)은 그 상태와 안내 문구로 ResponseStatusException 을 던진다.</li>
 *   <li>연결 실패·5xx·잘못된 응답은 IllegalStateException(원인 포함)으로 던진다. 로그는 최종 처리 지점에서 남긴다.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcrAgentDataClient {
    private static final String BASE = "/api/v1/ocr/data";
    /** 원본 파일 중계 한도. 업로드 한도(파일 10MB·요청 50MB)보다 넉넉히 둔다. */
    static final int MAX_ORIGINAL_BYTES = 64 * 1024 * 1024;
    private static final String REJECTED = "요청을 처리할 수 없습니다. 화면을 새로 고친 뒤 다시 시도해 주세요.";

    private final RestAgentProvider agent;
    private final ObjectMapper json = new ObjectMapper();

    /** 업무별 최근 판독작업 1건과 문서. wkId·transId 가 비면 조건 없이 */
    public List<OcrAgentData.Transaction> latestTransactions(String wkId, String transId) {
        return latestTransactions(wkId, transId, 1);
    }

    /** 업무별 최근 판독작업 perWorkflow 건과 문서(업무 안에서는 최근 스캔 순). wkId·transId 가 비면 조건 없이 */
    public List<OcrAgentData.Transaction> latestTransactions(String wkId, String transId, int perWorkflow) {
        Map<String, String> query = new HashMap<>();
        query.put("workflowId", blankToNull(wkId));
        query.put("transactionId", blankToNull(transId));
        query.put("perWorkflow", String.valueOf(perWorkflow));
        return call(HttpMethod.GET, BASE + "/workflows/latest-transactions", query, null, new TypeReference<>() { });
    }

    public List<OcrAgentData.Field> fields(String transId, String docId) {
        return call(HttpMethod.GET, document(transId, docId) + "/fields", null, null, new TypeReference<>() { });
    }

    public List<OcrAgentData.Table> tables(String transId, String docId) {
        return call(HttpMethod.GET, document(transId, docId) + "/tables", null, null, new TypeReference<>() { });
    }

    public RestAgentProvider.Download original(String transId, String docId) {
        try {
            return agent.download(document(transId, docId) + "/original", MAX_ORIGINAL_BYTES);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Agent original download failed transId=" + transId + " docId=" + docId, e);
        }
    }

    public List<OcrAgentData.ReviewState> reviewStates(List<OcrAgentData.DocumentKey> documents) {
        return call(HttpMethod.POST, BASE + "/reviews/search", null, new OcrAgentData.ReviewSearchRequest(documents),
                new TypeReference<>() { });
    }

    /**
     * 판독결과 검토 목록. 기간은 스캔일 yyyyMMdd 이고 transId 가 없으면 필수다.
     * transId 가 있으면 다른 조건 없이 그 1건만 찾는다.
     */
    public List<OcrAgentData.ReviewTransaction> reviewTransactions(String wkId, String transId, String assigneeId,
                                                                   String reviewStatus, String from, String to) {
        Map<String, String> query = new HashMap<>();
        query.put("workflowId", blankToNull(wkId));
        query.put("transactionId", blankToNull(transId));
        query.put("assigneeId", blankToNull(assigneeId));
        query.put("reviewStatus", blankToNull(reviewStatus));
        query.put("from", blankToNull(from));
        query.put("to", blankToNull(to));
        return call(HttpMethod.GET, BASE + "/review-transactions", query, null, new TypeReference<>() { });
    }

    public OcrAgentData.ReviewState assignTransaction(String wkId, String transId, OcrAgentData.AssignRequest request) {
        checkId(transId);
        return call(HttpMethod.POST, BASE + "/transactions/" + transId + "/assignee", Map.of("workflowId", wkId), request,
                new TypeReference<>() { });
    }

    public OcrAgentData.ReviewState saveValues(String transId, String docId, OcrAgentData.ValuesRequest request) {
        return call(HttpMethod.POST, document(transId, docId) + "/confirm-values", null, request, new TypeReference<>() { });
    }

    public OcrAgentData.ReviewState verify(String transId, String docId, OcrAgentData.VerifyRequest request) {
        return call(HttpMethod.POST, document(transId, docId) + "/verify", null, request, new TypeReference<>() { });
    }

    public OcrAgentData.ReviewState cancelVerify(String transId, String docId, OcrAgentData.CancelVerifyRequest request) {
        return call(HttpMethod.POST, document(transId, docId) + "/verify/cancel", null, request, new TypeReference<>() { });
    }

    public OcrAgentData.ReviewState setRole(String transId, String docId, OcrAgentData.RoleRequest request) {
        return call(HttpMethod.POST, document(transId, docId) + "/role", null, request, new TypeReference<>() { });
    }

    /** 저장하지 않는 판정. 결과 구조는 화면에 그대로 넘긴다 */
    public List<Map<String, Object>> judgeCrossRules(OcrAgentData.CrossJudgeRequest request) {
        checkId(request.transactionId());
        return call(HttpMethod.POST, BASE + "/cross-rules/judge", null, request, new TypeReference<>() { });
    }

    /** 서식간 검증 결과 판독작업 목록. 기간은 스캔일 yyyyMMdd */
    public List<OcrAgentData.CrossSummary> crossVerifications(String wkId, String result, String from, String to) {
        Map<String, String> query = new HashMap<>();
        query.put("workflowId", blankToNull(wkId));
        query.put("result", blankToNull(result));
        query.put("from", blankToNull(from));
        query.put("to", blankToNull(to));
        return call(HttpMethod.GET, BASE + "/cross-verifications", query, null, new TypeReference<>() { });
    }

    /** 조건(업무·스캔일 yyyyMMdd)에 맞는 판독작업을 지금 Rule 로 다시 판정 */
    public OcrAgentData.CrossBulkResult evaluateCrossVerifications(String wkId, String from, String to) {
        Map<String, String> query = new HashMap<>();
        query.put("workflowId", blankToNull(wkId));
        query.put("from", blankToNull(from));
        query.put("to", blankToNull(to));
        return call(HttpMethod.POST, BASE + "/cross-verifications/evaluate", query, null, new TypeReference<>() { });
    }

    public OcrAgentData.CrossDetail crossVerification(String transId) {
        checkId(transId);
        return call(HttpMethod.GET, BASE + "/transactions/" + transId + "/cross-verifications", null, null,
                new TypeReference<>() { });
    }

    public OcrAgentData.CrossDetail evaluateCrossVerification(String transId) {
        checkId(transId);
        return call(HttpMethod.POST, BASE + "/transactions/" + transId + "/cross-verifications/evaluate", null, null,
                new TypeReference<>() { });
    }

    private <T> T call(HttpMethod method, String uri, Map<String, String> query, Object body, TypeReference<T> type) {
        log.debug("[Agent데이터] 호출 method={} uri={} queryKeys={} hasBody={}", method, uri,
                query == null ? 0 : query.size(), body != null);
        RestResponseEntity<?> response;
        try {
            response = agent.exchange(method, uri, query, body);
        } catch (Exception e) {
            throw new IllegalStateException("Agent OCR data call failed uri=" + uri, e);
        }
        if (response == null) {
            throw new IllegalStateException("Agent OCR data call returned no body uri=" + uri);
        }
        int status = response.getStatus();
        if (status >= 400 && status < 500) {
            String message = response.getMessage();
            log.debug("[Agent데이터] Agent가 요청 거절 uri={} agentStatus={}", uri, status);
            throw new ResponseStatusException(HttpStatus.valueOf(status), message == null || message.isBlank() ? REJECTED : message);
        }
        if (status != 200 || !response.isSuccess()) {
            throw new IllegalStateException("Agent OCR data call failed uri=" + uri + " status=" + status);
        }
        try {
            T converted = json.convertValue(response.getData(), type);
            log.debug("[Agent데이터] 응답 변환 uri={} agentStatus={} resultCount={}", uri, status,
                    converted instanceof java.util.Collection<?> c ? c.size() : (converted == null ? 0 : 1));
            return converted;
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Agent OCR data response invalid uri=" + uri, e);
        }
    }

    /** 경로에 그대로 넣는 식별자는 형식을 제한한다. */
    private static String document(String transId, String docId) {
        checkId(transId);
        checkId(docId);
        return BASE + "/transactions/" + transId + "/documents/" + docId;
    }

    static void checkId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,100}")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "판독 문서를 찾을 수 없습니다.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
