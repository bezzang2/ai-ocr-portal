package com.agilesoda.module.ocr.vo;

import lombok.Data;

/**
 * "검증완료" 전송 시 "확인 정보" 패널의 필드 1건. tb_ocrdf_l.fild_id 기준으로
 * 사용자가 최종 확인/수정한 값(fildVal)을 담아 단말시스템(C#)에 전달한다.
 */
@Data
public class OcrVerifyFieldVO {
    private long fildId;
    private String fildKey;
    private String fildVal;
}
