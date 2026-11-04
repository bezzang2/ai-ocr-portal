package com.agilesoda.common.response.entity;

import lombok.Data;

@Data
public class RestResponseEntity<T> {
	//  Http Status (ex: 200, 400, 401...)
	private int status;

	//  성공 여부 ( true / false )
	private boolean success;

	//  메시지
	private String message;

	//  에러 메시지
	private String errorMessage;

	//  Timestamp
	private String timestamp;

	private T data;
}
