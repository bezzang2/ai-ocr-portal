package com.agilesoda.module.ocr.vo;

import lombok.Data;

/** 검증 담당자 지정 요청. userId 가 비어 있으면 지정을 해제한다. */
@Data
public class OcrAssignRequestVO {
    private String userId;
}
