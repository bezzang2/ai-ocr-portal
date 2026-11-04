package com.agilesoda.module.admin.vo;

import lombok.Data;

@Data
public class MenuManagementVO {
	private String menuId;			// 메뉴ID
	private String menuNm;			// 메뉴명
	private int menuLevel;		// 메뉴레벨
	private String uprnMenuId;		// 상위메뉴ID
	private int menuSer;			// 메뉴순서
	private String menuUrl;		// 화면URL
	private String useYn;			// 사용여부
	private String empKorFlnm;			// 변경자이름
	private String lastChngEmpno;	// 최종변경사번
	private String lastChngDttm;	// 최종변경일시
}
