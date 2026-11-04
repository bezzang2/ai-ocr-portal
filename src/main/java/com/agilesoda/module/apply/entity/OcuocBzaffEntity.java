package com.agilesoda.module.apply.entity;


import lombok.Data;

import java.util.List;

@Data
public class OcuocBzaffEntity {
    private long ruleId;
    private long appcId;
    private String ruleNm;
    private String ruleCd;
    private String useYn;
    private String userId;

    private List<OcuocBzafrEntity> fields;
}
