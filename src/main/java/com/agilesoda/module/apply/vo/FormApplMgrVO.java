package com.agilesoda.module.apply.vo;

import lombok.Data;

@Data
public class FormApplMgrVO {
    private long mgrId;
    private long applId;
    private String mgrTypeCd;
    private int mgrSeq;
    private String mgrUserId;
    private String mgrUserNm;
    private String regId;
    private String regDt;
}
