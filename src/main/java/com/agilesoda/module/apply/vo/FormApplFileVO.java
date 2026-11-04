package com.agilesoda.module.apply.vo;

import lombok.Data;

@Data
public class FormApplFileVO {
    private long fileId;
    private long applId;
    private String srcTypeCd;
    private String fileNm;
    private String filePath;
    private long fileSize;
    private String regId;
    private String regDt;
}
