package com.agilesoda.common.response;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@Builder
@ToString
public class BaseResponseBody {

    //  Http Status (ex: 200, 400, 401...)
    private int status;

    //  성공 여부 ( true / false )
    private boolean success;

    //  메시지
    private String message;

    //  에러 메시지
    private String errorMessage;

    //  결과 데이터
    private Object data;

    //  Timestamp
    private String timestamp;

}