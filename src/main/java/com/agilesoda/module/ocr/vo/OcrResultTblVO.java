package com.agilesoda.module.ocr.vo;

import lombok.Data;

import java.util.List;

@Data
public class OcrResultTblVO {
    private long tblId;
    private String tblKey;
    private List<OcrTblColVO> columns;
    private List<OcrResultTblRowVO> rows;
}
