package com.agilesoda.module.admin.vo;

import lombok.Data;

@Data
public class PermissionUserMenuVO {
	private String roleId;		// 역할ID
	private String menuId;		// 메뉴ID
	private String menuNm;		// 메뉴명
	private String uprnMenuId;		// 상위메뉴
	private Integer menuLevel;		// 레벨
	private Integer menuSer;		// 순번
	private String useYn;		// 사용여부
	private String frstRegEmpno;		// 최종변경자

}
