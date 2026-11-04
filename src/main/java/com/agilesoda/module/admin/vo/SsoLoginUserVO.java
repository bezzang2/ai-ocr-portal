package com.agilesoda.module.admin.vo;

import jakarta.servlet.http.HttpSession;
import lombok.Data;

@Data
public class SsoLoginUserVO {
	private String userId;			// 로그인ID
	private String userName;			// 로그인명
	private String deptCode;		// 부서코드
	private String deptName;		// 부서명
	private String role;			// 권한

	/** 로그용: 세션의 로그인 사용자 ID. 없으면 "-" */
	public static String userIdOf(HttpSession session) {
		Object user = session == null ? null : session.getAttribute("ssoLoginUser");
		return user instanceof SsoLoginUserVO vo && vo.getUserId() != null ? vo.getUserId() : "-";
	}
}
