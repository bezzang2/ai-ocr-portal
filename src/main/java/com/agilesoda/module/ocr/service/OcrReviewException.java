package com.agilesoda.module.ocr.service;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 판독결과 검토의 예상 가능한 업무 거절(권한 없음·이미 검증완료·잘못된 항목 등).
 * 메시지는 사용자에게 그대로 보여 주는 안내 문구다. 개인정보나 입력값을 넣지 않는다.
 */
@Getter
public class OcrReviewException extends RuntimeException {
    private final HttpStatus status;

    public OcrReviewException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public OcrReviewException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}
