package com.agilesoda.sample.entity.req;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BoardInqReqEntity {
	private int seq;
	private int offset;
	private int limit;
	private String subject;
	private String writer;
}
