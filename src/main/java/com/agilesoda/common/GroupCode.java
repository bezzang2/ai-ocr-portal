package com.agilesoda.common;

import java.util.List;

import lombok.Data;

@Data
public class GroupCode {
	private String groupCd;
	private String groupNm;
	
	List<ComCode> codes;
}
