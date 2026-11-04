package com.agilesoda.sample.entity.req;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserInqReqEntity {
	private long seq;
	private String userId;
	private String userNm;
}
