package com.agilesoda.module.ocr.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent OCR 데이터 API(/api/v1/ocr/data) 응답·요청 계약. Agent 의 OcrData 와 이름을 맞춘다.
 * 모르는 필드는 무시해 Agent 가 필드를 추가해도 깨지지 않는다.
 */
public final class OcrAgentData {
    private OcrAgentData() {
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Transaction {
        private String workflowId;
        private String transactionId;
        private String status;
        /** 스캔일시(yyyyMMddHHmmss) */
        private String scanDateTime;
        private List<Document> documents = new ArrayList<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Document {
        private String documentId;
        private String typeCode;
        private String typeName;
        private String status;
        private String redactYn;
        private String fileName;
        private String originalFileName;
        private String fileExtension;
        /** 문서 역할(예: 신청인). 정하지 못했으면 null */
        private String role;
        /** AUTO 자동 구분, MANUAL 담당자 지정 */
        private String roleSource;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Field {
        private long fieldId;
        private Long groupId;
        private String key;
        private String value;
        private String code;
        private String bbox;
        private Double confidence;
        private boolean confirmed;
        private String confirmedValue;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Table {
        private long tableId;
        private String key;
        private String code;
        private List<Column> columns = new ArrayList<>();
        private List<Row> rows = new ArrayList<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Column {
        private long columnId;
        private int seq;
        private String name;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Row {
        private int rowSeq;
        private List<Cell> cells = new ArrayList<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Cell {
        private long cellId;
        private int rowSeq;
        private long columnId;
        private String key;
        private String value;
        private String code;
        private String bbox;
        private Double confidence;
        private boolean confirmed;
        private String confirmedValue;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReviewState {
        private String transactionId;
        private String documentId;
        private String transactionAssigneeId;
        private String verifyStatus;
        private String verifierId;
        private String verifiedAt;
        private String saverId;
        private String savedAt;
    }

    /** 요청한 Portal 사용자와 담당자 지정 권한 */
    /** 판독결과 검토 목록의 판독작업 1건(Agent GET /review-transactions). documents 는 종류·상태만 채워져 있다 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReviewTransaction {
        private String transactionId;
        private String workflowId;
        private String workflowName;
        private String status;
        private String scanDateTime;
        private String assigneeId;
        private String reviewStatus;
        private int documentCount;
        private int failedCount;
        private int verifiedCount;
        private int mismatchCount;
        private int unverifiableCount;
        private List<Document> documents = new ArrayList<>();
    }

    public record Actor(String userId, boolean canAssign) { }

    public record AssignRequest(Actor actor, String assigneeId) { }

    public record RoleRequest(Actor actor, String role) { }

    public record ItemValue(long id, String value) { }

    public record ValuesRequest(Actor actor, List<ItemValue> fields, List<ItemValue> cells) { }

    /** 결과 파일에 넣을 서식과 문서의 연결 */
    public record Form(String documentId, Long formId, String formName) { }

    public record VerifyRequest(Actor actor, String workflowId, List<ItemValue> fields, List<ItemValue> cells, List<Form> forms) { }

    public record CancelVerifyRequest(Actor actor) { }

    public record DocumentKey(String transactionId, String documentId) { }

    public record ReviewSearchRequest(List<DocumentKey> documents) { }

    /** 규칙 정의 사본. definition 은 서류마다 서식 코드를 넣은 JSON 이다 */
    public record CrossDefinitionRule(long ruleId, int version, String name, String definition) { }

    public record CrossJudgeRequest(String transactionId, List<CrossDefinitionRule> rules) { }

    /** 서식간 검증 판독작업 요약. result: MATCH·MISMATCH·UNVERIFIABLE·NOT_APPLICABLE */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CrossSummary {
        private String transactionId;
        private String workflowId;
        private String workflowName;
        private String scannedAt;
        private String result;
        private int matchCount;
        private int mismatchCount;
        private int unverifiableCount;
        private int notApplicableCount;
        private int staleCount;
        private String evaluatedAt;
    }

    /** 서식간 검증 일괄 다시 판정 결과. truncated 면 상한(최근 스캔 순 500건)까지만 판정했다 */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CrossBulkResult {
        private int targets;
        private int evaluated;
        private int failed;
        private boolean truncated;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CrossDetail {
        private String transactionId;
        private String workflowId;
        /** Portal 이 채운다(업무명) */
        private String workflowName;
        private String result;
        /** 판독에 성공한 문서(documentId, formCode). 구조는 화면에 그대로 넘긴다 */
        private List<Map<String, Object>> documents = new ArrayList<>();
        /** Portal 이 채운다(formId, formCode, formName). 판독 이미지·확인값 저장 경로에 쓴다 */
        private List<Map<String, Object>> forms = new ArrayList<>();
        private List<CrossRuleResult> rules = new ArrayList<>();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CrossRuleResult {
        private long ruleId;
        private String name;
        private int version;
        private String definition;
        /** 저장된 판정. 판정 전이면 null */
        private String result;
        private String reason;
        private String evaluatedAt;
        private boolean stale;
        /** 지금 값으로 계산한 값과 근거. 구조는 화면에 그대로 넘긴다 */
        private Map<String, Object> live;
    }
}
