package com.agilesoda.module.admin.entity;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GroupCodeEntity {
    private String groupCd;
    private String groupNm;
    private String userId;
}
