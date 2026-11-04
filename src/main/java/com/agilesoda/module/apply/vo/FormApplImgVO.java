package com.agilesoda.module.apply.vo;

import lombok.Data;

@Data
public class FormApplImgVO {
    private long imgId;
    private long applId;
    private String fileNm;
    private String filePath;
    private String imgTypeCd;
    private int sortOrd;
    private String regId;
    private String regDt;
}
