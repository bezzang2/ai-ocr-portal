package com.agilesoda.common.response;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import com.agilesoda.common.util.DateUtil;

import net.sf.json.JSONObject;

@Component
public class BaseResponse {

    public static ResponseEntity<BaseResponseBody> ok(String message) {
        return ok(HttpStatus.OK, message, Collections.emptyList());
    }

    public static ResponseEntity<BaseResponseBody> ok(String message, Object data){
        return ok(HttpStatus.OK, message, data);
    }

    public static ResponseEntity<BaseResponseBody> ok(HttpStatus status, String message, Object data){
        return ResponseEntity.ok(getSuccessBody(status, message, data));
    }

    public static ResponseEntity<BaseResponseBody> error(String message){
        return error(HttpStatus.INTERNAL_SERVER_ERROR, message, Collections.emptyList());
    }

    public static ResponseEntity<BaseResponseBody> error(HttpStatus status, String message){
        return error(status, message, Collections.emptyList());
    }

    public static ResponseEntity<BaseResponseBody> error(List<ObjectError> errors){
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "", errors);
    }

    public static ResponseEntity<BaseResponseBody> error(HttpStatus status, String message, List<ObjectError> errors){
        return ResponseEntity.ok(getErrorBody(status, message, errors));
    }

    public static BaseResponseBody getSuccessBody(HttpStatus status, String message, Object data){
    	 return BaseResponseBody.builder()
                 .status(status.value())
                 .success(true)
                 .message(message)
                 .errorMessage("")
                 .data(data)
                 .timestamp(DateUtil.getCurrentDateTime("yyyy-MM-dd hh:mm:ss"))
                 .build();
    }

    public static BaseResponseBody getErrorBody(HttpStatus status, String message){
        return getErrorBody(status, message, Collections.emptyList());
    }

    public static BaseResponseBody getErrorBody(HttpStatus status, String message, List<ObjectError> errors){
    	 return BaseResponseBody.builder()
                 .status(status.value())
                 .success(false)
                 .message(message)
                 .errorMessage(convertString(errors))
                 .data(null)
                 .timestamp(DateUtil.getCurrentDateTime("yyyy-MM-dd hh:mm:ss"))
                 .build();
    }

    private static String convertString(List<ObjectError> errors){
        String fieldErrors = errors.stream().map( e -> {
            if (e instanceof FieldError) {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("field", ((FieldError) e).getField());
                jsonObject.put("defaultMessage", e.getDefaultMessage().replace("\"", "'"));
                return jsonObject.toString();
            }
            else {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("object", e.getObjectName());
                jsonObject.put("defaultMessage", e.getDefaultMessage().replace("\"", "'"));
                return jsonObject.toString();
            }
        }).collect((Collectors.joining(",")));

        return "[" + fieldErrors + "]";
    }
}
