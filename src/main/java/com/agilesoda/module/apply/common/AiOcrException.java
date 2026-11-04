package com.agilesoda.module.apply.common;

public class AiOcrException extends RuntimeException {
    private int status;
    private String type;
    private String title;
    private String code;
    private String detail;

    public int getStatus() {
        return status;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getCode() {
        return code;
    }

    public String getDetail() {
        return detail;
    }

    public AiOcrException(int status, String type, String title, String code, String detail) {
        super(detail);
        this.status = status;
        this.type = type;
        this.title = title;
        this.code = code;
        this.detail = getDetail();
    }
}
