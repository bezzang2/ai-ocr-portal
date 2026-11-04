package com.agilesoda.module.apply.vo;

import lombok.Builder;
import lombok.Data;

@Data
public class FormApplVO {
	private int no;
	private String procStatCd;
	private String procStatNm;
	private String formNm;
	private String applicantNm;
	private String applDeptNm;
	private String applDate;
	private String updateDate;
}
