package com.agilesoda.module.apply.entity;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OcuocBzscnEntity {
    private long appcId;
    private String wkflowId;
    private String scnNo;
    private String userId;
}
