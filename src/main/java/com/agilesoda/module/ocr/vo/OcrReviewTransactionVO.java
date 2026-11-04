package com.agilesoda.module.ocr.vo;

import lombok.Data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 판독결과 검토 목록의 판독작업 1건. 화면(templates/module/ocr/ocr-result-list.html)과 상세 요약줄이 쓴다.
 * 검토상태 판정은 Agent 매퍼(selectReviewTransactions)에 있고, 이 객체는 화면 문구만 붙인다.
 */
@Data
public class OcrReviewTransactionVO {
    /** 검토상태 코드 → 화면 문구. 조회 조건 선택칸도 이 순서로 쓴다. */
    public static final Map<String, String> STATUS_LABELS;

    static {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("UNASSIGNED", "미지정");
        labels.put("IN_REVIEW", "검토중");
        labels.put("VERIFIED", "검증완료");
        labels.put("PROCESSING", "판독중");
        labels.put("NO_RESULT", "결과없음");
        labels.put("FAILED", "실패");
        STATUS_LABELS = Collections.unmodifiableMap(labels);
    }

    private String transId;
    private String wkId;
    private String wkNm;
    /** 스캔일시 yyyyMMddHHmmss */
    private String scanDt;
    /** 엔진 처리 상태(OCUOC_OCRWF_L.RSLT_CD) */
    private String rsltCd;
    private String asgnUserId;
    private String asgnUserNm;
    /** STATUS_LABELS 의 키 */
    private String reviewStatus;
    /** 검증 대상 서식 수: 실패·취소를 뺀 문서 수 */
    private int formCount;
    /** 검증완료 서식 수 */
    private int verifiedCount;
    /** 실패·취소 문서 수 */
    private int failedCount;
    /** 업무에 연결됐는데 판독 문서가 없는 서식 수. 판독중·판독 실패면 0이다. */
    private int emptyCount;
    /** 서식간 검증 불일치 규칙 수 */
    private int mismatchCount;
    /** 서식간 검증 검증불가 규칙 수 */
    private int unverifiableCount;

    public String getReviewStatusLabel() {
        return STATUS_LABELS.getOrDefault(reviewStatus, reviewStatus);
    }

    /** 스캔일시 표시값(yyyy-MM-dd HH:mm). 형식이 다르면 그대로 */
    public String getScanDtLabel() {
        return OcrResultWkGroupVO.scanDtLabel(scanDt);
    }
}
