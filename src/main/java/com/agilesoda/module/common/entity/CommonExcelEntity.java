package com.agilesoda.module.common.entity;

import lombok.Data;

@Data
public class CommonExcelEntity {
	private Integer exdwnId;			// 엑셀다운ID
	private String userId;			// 사용자ID
	private String menuId;		// 메뉴ID
	private String downloadReason;	// 엑셀다운사유
	private String frstRegEmpno;	// 최종변경일시



}
