package com.agilesoda.module.apply.vo;

import lombok.Data;

@Data
public class ApprvlLineVO {
    private long apprvlId;
    private long applId;
    private String applTypeCd;
    private int apprvlSeq;
    private String apprvrUserId;
    private String apprvrUserNm;
    private String apprvlStatCd;
    private String apprvlOpnn;
    private String apprvlDt;
    private String regId;
    private String regDt;
}
