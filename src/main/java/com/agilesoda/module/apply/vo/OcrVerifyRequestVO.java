package com.agilesoda.module.apply.vo;

import com.agilesoda.module.ocr.vo.OcrVerifyFieldVO;
import lombok.Data;

import java.util.List;

@Data
public class OcrVerifyRequestVO {
    private List<OcrVerifyFieldVO> fields;
    private List<OcrVerifyCellVO> tables;
}
