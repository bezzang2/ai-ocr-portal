package com.agilesoda.module.admin.vo;

import lombok.Data;

@Data
public class PermissionManagementResVO {
	private String roleId;		// 역활ID
	private String roleNm;		// 역활명
	private String useYn;		// 사용여부
	private String frstRegEmpno;		// 최종변경자

}
