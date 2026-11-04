package com.agilesoda.module.apply.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DescItemVO {
    private long fldId;
    private String fldKey;
    private String fldNm;
    private String description;
    private String reqYn;
    // 규칙 등록 모달
    private String dataTypeCd;
    private Integer dataLen;
    private String maskYn;
    private String maskRange;
    private String dataCmprRule;
    private List<String> matchFldIdList = new ArrayList<>();
    // 메타 정보 모달
    private String metaClss;
    private String metaType;
    private String metaWord;
    private String metaLnm;
    private String metaPnm;
}
