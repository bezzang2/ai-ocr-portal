package com.agilesoda.module.admin.entity;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ComCodeEntity {
    private String groupCd;
    private String cdVal;
    private String cdNm;
    private String cdDesc;
    private int lnupSer;
    private String useYn;
    private String userId;
}
