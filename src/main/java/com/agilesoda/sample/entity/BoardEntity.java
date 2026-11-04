package com.agilesoda.sample.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class BoardEntity extends CommonEntity{
	private int num;
	private String subject;
	private String detail;
	private String writer;
}
