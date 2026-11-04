package com.agilesoda.module.ocr.vo;

import lombok.Data;

/**
 * 판독 문서 1건(transId, docId)의 검증 담당자·검증완료·확인값 저장 상태(OCUOC_OCRVR_L).
 * 담당자는 판독작업 담당자(DOC_ID '*' 행) 1명이다.
 */
@Data
public class OcrReviewStateVO {
    public static final String VERIFIED = "VERIFIED";

    private String transId;
    private String docId;
    /** 판독작업 담당자 */
    private String transAsgnUserId;
    private String transAsgnUserNm;
    private String vrfyStatCd;
    private String vrfyUserId;
    private String vrfyUserNm;
    private String vrfyDt;
    private String saveUserNm;
    private String saveDt;

    /** 실제 검증 담당자: 판독작업 담당자 */
    public String getAsgnUserId() {
        return transAsgnUserId;
    }

    public String getAsgnUserNm() {
        return transAsgnUserNm;
    }

    public boolean isVerified() {
        return VERIFIED.equals(vrfyStatCd);
    }
}
