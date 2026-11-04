package com.agilesoda.module.ocr.vo;

import lombok.Data;

@Data
public class OcrTblColVO {
    private long colId;
    private long tblId;
    private int colSeq;
    private String colNm;
    private String rgsrId;
    private String regDt;
}
