package com.agilesoda.module.apply.entity;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class OcuocBzwkaEntity {
    private long appcId;
    private String wkflowNm;
    private String wkflowDesc;
    private String bzwkCd;
    private String apctDprmCd;
    private String prgsStCd;
    private String userId;
}
