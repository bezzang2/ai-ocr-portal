package com.agilesoda.module.apply.vo;

import lombok.Data;

/**
 * 서식간 규칙(OCUOC_BZCRR_L). 업무(OCUOC_OCRWF_M.WK_ID)에 묶인다.
 */
@Data
public class CrossRuleVO {
    private Long ruleId;
    private String wkId;
    private String ruleNm;
    /** EQUALS 일치, CONTAINS 포함 */
    private String cmprTypCd;
    private String rgsrId;
    private String regDt;
    private String edtrId;
    private String chngDt;
    /** 새 형식 규칙의 사용 버전. 기존 규칙은 null */
    private Integer curVer;
    /** Y 사용, N 사용 안 함 */
    private String useYn;
    /** 저장용 규칙 정의 JSON(OCUOC_BZCRV_L.DEF_JSON) */
    private String defJson;
}
