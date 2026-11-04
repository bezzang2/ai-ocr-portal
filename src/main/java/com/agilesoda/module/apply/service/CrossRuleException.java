package com.agilesoda.module.apply.service;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 서식간 규칙 관리의 예상 가능한 업무 거절(입력 누락·없는 업무나 서식 등).
 * 메시지는 사용자에게 그대로 보여 주는 안내 문구다.
 */
@Getter
public class CrossRuleException extends RuntimeException {
    private final HttpStatus status;

    public CrossRuleException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public CrossRuleException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}
