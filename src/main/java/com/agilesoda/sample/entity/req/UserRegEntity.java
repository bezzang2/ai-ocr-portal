package com.agilesoda.sample.entity.req;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserRegEntity {
	private String userId;
	private String userNm;
	private String userPwd;
	private String hpNo;
	private String email;
}
