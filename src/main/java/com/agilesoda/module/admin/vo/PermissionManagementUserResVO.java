package com.agilesoda.module.admin.vo;

import lombok.Data;

@Data
public class PermissionManagementUserResVO {
	private String roleId;		// 역활ID
	private String userId;		// 사용자ID
	private String empKorFlnm;		// 사용자명
	private String hraPstnNm;		// 인사직급명
	private String brNm;		// 부서명
	private String useYn;		// 사용여부
	private String frstRegEmpno;		// 최초등록자

}
