package com.agilesoda.module.ocr.vo;

import lombok.Data;

/** 판독결과 화면을 쓰는 로그인 사용자와 담당자 지정 권한(관리자·팀장). */
@Data
public class OcrReviewerVO {
    private String userId;
    private String userNm;
    /** 다른 사람을 담당자로 지정·해제할 수 있다 */
    private boolean canAssign;
}
