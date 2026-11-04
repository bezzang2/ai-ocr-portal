package com.agilesoda.sample.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BoardInqDto {
	private int seq;
	private int offset;
	private int limit;
	private String subject;
	private String writer;
}
