package com.agilesoda.module.apply.entity;

import lombok.Data;

import java.util.List;

@Data
public class TaskInfoResEntity {
	private int idx;
    private long appcId;
    private String wkflowNm;
    private String wkflowId;
    private String wkflowDesc;
    private String bzwkCd;
    private String bzwkNm;
    private String apctDprmCd;
    private String brNm;
    private String prgsStCd;
    private String prgsStNm;
    private String regId;
    private String regNm;
    private String regDt;
    private String modId;
    private String modNm;
    private String modDt;

    List<OcuocBzwkfEntity> wkflows;
    List<OcuocBzaffEntity> rules;
    List<OcuocBzaafEntity> attaches;
    List<OcuocBzscnEntity> screens;
}
