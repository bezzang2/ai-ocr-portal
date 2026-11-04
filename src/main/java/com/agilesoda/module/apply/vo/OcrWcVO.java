package com.agilesoda.module.apply.vo;

import lombok.Data;

/** 저장된 분류 클래스 사본(OCUOC_OCRWC_L) 한 행과 그 업무의 현재 릴리스. */
@Data
public class OcrWcVO {
    private String wkId;
    private String activeReleaseId;
    private String className;
    private String docTypeCode;
}
