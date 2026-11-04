package com.agilesoda.sample.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserInqDto {
	private long seq;
	private String userId;
	private String userNm;
}
