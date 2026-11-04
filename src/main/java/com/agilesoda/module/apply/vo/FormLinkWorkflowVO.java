package com.agilesoda.module.apply.vo;

import lombok.Data;

/** 업무·서식 연결 관리 - 업무(OCUOC_OCRWF_M) 1건과 분류 클래스 연결 현황 */
@Data
public class FormLinkWorkflowVO {
    private String wkId;
    private String wkNm;
    private String wkStatCd;
    /** 마지막 동기화 일시(yyyy-MM-dd HH:mm:ss) */
    private String syncDt;
    /** 분류 클래스 수. 0이면 아직 클래스를 저장하지 못한 업무다 */
    private int classCnt;
    /** 서식과 연결된 클래스 수 */
    private int linkedCnt;
    /** 맞는 서식은 있지만 연결이 아직 없는 클래스 수(다음 동기화 또는 서식 저장 때 연결) */
    private int pendingCnt;
    /** 맞는 서식이 없는 클래스 수 */
    private int unlinkedCnt;
    /** 업무-서식 연결(OCUOC_OCRWL_L) 행 수 */
    private int formCnt;
}
