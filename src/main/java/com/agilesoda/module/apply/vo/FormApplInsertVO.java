package com.agilesoda.module.apply.vo;

import lombok.Data;

@Data
public class FormApplInsertVO {
    private long applId;
    private String formNm;
    private String applicantUserId;
    private String applDeptCd;
    private String procStatCd;
    private String regId;
    private String regDt;
}
