package com.agilesoda.module.admin.vo;

import lombok.Data;

@Data
public class CommonMenuListVO {
	private String menuId;			// 메뉴ID
	private String menuNm;			// 메뉴명
	private int menuLevel;		// 메뉴레벨
	private String uprnMenuId;		// 상위메뉴ID
	private String uprnMenuNm;		// 상위메뉴명
	private int menuSer;			// 메뉴순서
	private String menuUrl;		// 화면URL
	private String useYn;			// 사용여부
	private String lastChngEmpno;	// 최종변경사번
	private String lastChngDttm;	// 최종변경일시
	private String roleId;			// 권한ID


	// 버튼 관련변수
	private String btnCd;	// 버튼코드
	private String btnNm;	// 버튼명
	private String areaCd;	// 버튼명
	private String actionCd;	// 버튼명
	private String iocnUrl;	// 버튼명
	private String auditYn;	// 버튼명

}
