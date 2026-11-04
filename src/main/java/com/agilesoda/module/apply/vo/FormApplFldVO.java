package com.agilesoda.module.apply.vo;

import lombok.Data;

import java.util.List;

@Data
public class FormApplFldVO {
    private long applId;
    private long fldId;
    private String fldCd;
    private Long imgId;
    private Long upFldId;
    private Integer rowNo;
    private String fldTypeCd;
    private String srcTypeCd;
    private String fldNm;
    private String fldVal;
    private String fldDesc;
    private String reqYn;
    private String validRule;
    private String metaRule;
    private Double confidence;
    private String modId;
    private String modDt;
    // 추출설명 규칙 등록 모달
    private String dataTypeCd;
    private Integer dataLen;
    private String maskYn;
    private String maskRange;
    private String dataCmprRule;
    private List<String> matchFldIdList;
    // 추출설명 메타 정보 모달
    private String metaClss;
    private String metaType;
    private String metaWord;
    private String metaLnm;
    private String metaPnm;
}
