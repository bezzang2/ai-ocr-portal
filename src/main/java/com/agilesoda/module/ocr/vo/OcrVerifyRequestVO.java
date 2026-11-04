package com.agilesoda.module.ocr.vo;

import com.agilesoda.module.apply.vo.OcrVerifyCellVO;
import lombok.Data;

import java.util.List;

/**
 * "검증완료" 버튼 클릭 시 브라우저(result-file-list.js)가 POST 로 보내는 요청 바디.
 * lefrmId 기준 "확인 정보" 필드 목록을 담는다.
 */
@Data
public class OcrVerifyRequestVO {
    private List<OcrVerifyFieldVO> fields;
    private List<OcrVerifyCellVO> tables;
}
