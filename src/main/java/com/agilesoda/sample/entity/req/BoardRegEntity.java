package com.agilesoda.sample.entity.req;

import lombok.Data;

@Data
public class BoardRegEntity {
	private int seq; 
	private String subject;
	private String detail;
	private String writer;
	private String userId;
}
