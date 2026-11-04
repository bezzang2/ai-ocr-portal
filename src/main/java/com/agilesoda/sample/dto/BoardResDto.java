package com.agilesoda.sample.dto;

import java.sql.Timestamp;
import java.util.List;

import com.agilesoda.sample.entity.BoardAttachEntity;

import lombok.Data;

@Data
public class BoardResDto {
	private int num;
	private int seq;
	private String subject;
	private String detail;
	private String writer;
	private Timestamp regDt;
	private String regId;
	private Timestamp modDt;
	private String modId;

	private List<BoardAttachEntity> attaches;
}
