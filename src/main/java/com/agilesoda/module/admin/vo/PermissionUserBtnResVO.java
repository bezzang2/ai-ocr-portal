package com.agilesoda.module.admin.vo;

import lombok.Data;

@Data
public class PermissionUserBtnResVO {
	private String btnCd;		// 버튼코드
	private String btnNm;		// 버튼명
	private String useYn;		// 사용여부
	private String areaCd;		// 버튼영역코드
	private String actionCd;		// 액션코드
	private String iocnUrl;			// 아이콘url
	private String frstRegEmpno;       // 최초등록자사번

}
