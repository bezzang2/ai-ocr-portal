package com.agilesoda.module.ocr.vo;

import lombok.Data;

import java.util.Set;

@Data
public class OcrResultLefrmVO {
    private Long lefrmId;
    private String lefrmNm;
    /** tb_lefrm_m.doc_id (서식 논리코드) */
    private String formId;
    /** 서식이 연결된 업무(ocuoc_ocrwl_l.wk_id) */
    private String wkId;
    private String regStaCd;
    /** 판독 문서의 요청 파일(Agent 응답): 저장 파일명·확장자·원본 파일명 */
    private String fileNm;
    private String fileExt;
    private String orgFileNm;
    private String transId;
    private String docId;
    /** 판독 문서 상태(OCUOC_OCRDO_L.DOC_STAT_CD, 엔진 documents[].status) */
    private String docStatCd;
    private String redactYn;
    /** 검증 담당자·검증완료 상태. 판독 문서가 있는 서식만 채운다. */
    private OcrReviewStateVO review;

    /** 엔진 문서 상태 중 결과가 없는 실패·취소(정의서 §6.2). Agent OcrDataService.FAILED_DOCUMENT_STATUSES 와 같은 값이다. */
    private static final Set<String> FAILED_DOC_STAT_CDS = Set.of("failed", "canceled");

    /** 업무-서식 연결 정보만 담은 새 행. 판독작업마다 문서를 따로 붙일 때 쓴다. */
    /** 같은 서식이 여러 건일 때 이 문서의 순서와 전체 건수. 한 건이면 표시하지 않는다 */
    private int docSeq;
    private int docTotal;
    /** 문서 역할(예: 신청인)과 출처(AUTO, MANUAL) */
    private String docRole;
    private String docRoleSrc;

    /** 서식목록 이름 뒤에 붙는 표시. 예: (2/2) */
    public String getDocLabel() {
        return docTotal > 1 ? "(" + docSeq + "/" + docTotal + ")" : "";
    }

    public OcrResultLefrmVO linkOnly() {
        OcrResultLefrmVO copy = new OcrResultLefrmVO();
        copy.setLefrmId(lefrmId);
        copy.setLefrmNm(lefrmNm);
        copy.setFormId(formId);
        copy.setWkId(wkId);
        copy.setRegStaCd(regStaCd);
        return copy;
    }

    /** 판독 문서가 실패·취소된 서식. 서식목록의 "실패" 묶음에 표시한다. */
    public boolean isFailed() {
        return docId != null && isFailedStatus(docStatCd);
    }

    public static boolean isFailedStatus(String docStatCd) {
        return docStatCd != null && FAILED_DOC_STAT_CDS.contains(docStatCd);
    }
}
