package com.agilesoda.module.ocr.vo;

import lombok.Data;

import java.util.List;

@Data
public class OcrResultTblRowVO {
    private int rowSeq;
    private List<OcrTblCellVO> cells;
}
