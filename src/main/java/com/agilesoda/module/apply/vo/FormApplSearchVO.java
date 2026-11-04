package com.agilesoda.module.apply.vo;

import lombok.Data;

@Data
public class FormApplSearchVO {

	private String procStatCd;
	private String applDeptNm;
	private String applicantNm;
	private String applDateFrom;
	private String applDateTo;
	private String keywordType;
	private String keyword;
	private boolean onlyMine;
	private String loginUserId;
	private int pageSize = 15;
	private int pageNo = 1;
}
