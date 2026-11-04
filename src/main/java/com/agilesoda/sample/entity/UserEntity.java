package com.agilesoda.sample.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserEntity extends CommonEntity{
	private String userId;
	private String userNm;
	private String userPwd;
	private String email;
	private String hpNo;
}
