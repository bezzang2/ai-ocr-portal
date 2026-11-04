package com.agilesoda.module.apply.vo;

import lombok.Data;

/** 업무-서식 문서 역할 설정(OCUOC_OCRWR_L) */
@Data
public class CrossRoleVO {
    private String wkId;
    private Long formId;
    private String roleNm;
    /** CUSTOMER_NAME, REMAINDER, VALUE_CONTAINS, MANUAL */
    private String methodCd;
    /** 구분에 쓰는 항목(CUSTOMER_NAME·VALUE_CONTAINS) */
    private String fieldKey;
}
