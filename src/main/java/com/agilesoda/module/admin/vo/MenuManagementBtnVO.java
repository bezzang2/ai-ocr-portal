package com.agilesoda.module.admin.vo;
import lombok.Data;

@Data
public class MenuManagementBtnVO {
	private String menuId;			// 메뉴ID
	private int btnSeq;			// 버튼순번
	private String btnCd;		// 버튼코드
	private String btnNm;		// 버튼명
	private String areaCd;		// 버튼영역코드
	private String actionCd;		// 액션코드
	private String useYn;			// 사용여부
	private String iocnUrl;			// 아이콘url
	private String functionNm;			// 실행함수명
	private String auditYn;			// 감사로그여부
	private String lastChngEmpno;       // 최종변경자사번
}
