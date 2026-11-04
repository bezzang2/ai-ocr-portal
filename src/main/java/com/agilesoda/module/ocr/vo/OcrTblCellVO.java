package com.agilesoda.module.ocr.vo;

import lombok.Data;

@Data
public class OcrTblCellVO {
    private long cellId;
    private long tblId;
    private int rowSeq;
    private long colId;
    private String fildKey;
    private String fildVal;
    private String maskedVal;
    private String fildCd;
    /** Engine token_bbox JSON, in processed page pixels; null for older results. */
    private String tokenBbox;
    private Double cfdn;
    private String predictedMaskedVal;
    /** 저장한 확인값(OCUOC_OCRCV_L). cnfmYn 이 Y 면 인식값 대신 cnfmVal 을 보여 준다(빈 값 수정은 cnfmVal null). */
    private String cnfmVal;
    private String cnfmYn;
    private String rgsrId;
    private String regDt;
}
