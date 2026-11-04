package com.agilesoda.module.admin.vo;

import lombok.Data;

@Data
public class CommonUserListVO {
	private String empNo;			// 직원번호
	private String empKorFlnm;		// 직원명
	private String blngBrcd;		// 소속부점코드
	private String brNm;			// 부서명
	private String bsorgcd;			// 모점코드
	private String empJobclsCd;		// 직원직급코드
	private String empJobclsNm;		// 직원직급명
	private String hraPstnCd;		// 인사직위코드
	private String hraPstnNm;		// 인사직위명
	private String retmDt;			// 퇴직일자
	private String autGrpCd;		// 감사총괄책임자여부
	private String ojbbEmpDvcd;		// 당행직원구분코드
	private String workStcd;		// 근무상태코드
	private String brdd;			// 생년월일

}
