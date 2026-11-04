package com.agilesoda.module.apply.vo;

import lombok.Data;

/** 업무·서식 연결 관리 - 업무의 분류 클래스 1건과 연결 상태 */
@Data
public class FormLinkClassVO {
    public static final String LINKED = "LINKED";
    public static final String PENDING = "PENDING";
    public static final String UNLINKED = "UNLINKED";

    private String wkId;
    private String wkNm;
    private int classSeq;
    private String classNm;
    /** 엔진 문서 종류 영문코드. 없으면 서식명으로 연결한다 */
    private String docTypCd;
    /** 연결 규칙에 맞는 서식. 없으면 null */
    private Long lefrmId;
    private String lefrmNm;
    private String docId;
    /** 업무-서식 연결(OCUOC_OCRWL_L)에 실제 행이 있는지 */
    private String linkYn;

    /** LINKED 연결됨, PENDING 맞는 서식은 있으나 연결 전, UNLINKED 맞는 서식 없음 */
    public String getStatus() {
        if (lefrmId == null) return UNLINKED;
        return "Y".equals(linkYn) ? LINKED : PENDING;
    }
}
