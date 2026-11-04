package com.agilesoda.common.response.entity;

import java.sql.Timestamp;

import lombok.Data;

@Data
public class SampleUserEntity{
	private long seq;
	private String userId;
	private String userNm;
	private String userPwd;
	private Timestamp regDt;
	private String regId;
	private Timestamp modDt;
	private String modId;
}
