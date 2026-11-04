package com.agilesoda.sample.entity;

import java.sql.Timestamp;

import lombok.Data;

@Data
public class CommonEntity {
	protected long seq;

	protected Timestamp regDt;
	protected String regId;

	protected Timestamp modDt;
	protected String modId;

}
