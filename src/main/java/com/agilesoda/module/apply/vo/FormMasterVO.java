package com.agilesoda.module.apply.vo;

import lombok.Data;

/** 서식 마스터(OCUOC_LEFRM_M) 1건. 등록·수정 요청 본문과 목록 응답에 함께 쓴다 */
@Data
public class FormMasterVO {
    /** 수정할 서식. 등록이면 null */
    private Long lefrmId;
    private String lefrmNm;
    /** 문서 종류 코드. 엔진 클래스 영문코드와 같으면 그 클래스에 연결된다 */
    private String docId;
    private String regStaCd;
    /** 이 서식이 연결된 업무 수 */
    private int linkCnt;
    /** 최종 변경 일시(yyyy-MM-dd HH:mm:ss) */
    private String chngDt;
}
