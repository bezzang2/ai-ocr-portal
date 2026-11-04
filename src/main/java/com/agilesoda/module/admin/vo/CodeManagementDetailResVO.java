package com.agilesoda.module.admin.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CodeManagementDetailResVO {
    private String cdGroup;			// 그룹코드
    private String cdVal;		// 코드값
    private String cdNm;		// 코드명
    private String cdDesc;		// 코드 설명
    private Integer lnupSer;		// 정렬순서
    private String uzYn;		// 사용여부
    private LocalDate vldStdd;		// 유효시작일자
    private LocalDate vldEndt;		// 유효종료일자
    private String lastChngEmpno;       // 최종변경자사번


}

