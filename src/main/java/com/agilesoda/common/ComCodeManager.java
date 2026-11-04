package com.agilesoda.common;

import java.util.ArrayList;
import java.util.List;

public class ComCodeManager {
	
	private static ComCodeManager instance;
	
	private List<GroupCode> groups;
	
	private ComCodeManager() {
		this.groups = new ArrayList<>();
		
	}
	
	public static ComCodeManager getInstance() {
		if(instance == null) {
			instance = new ComCodeManager();
		}
		
		return instance;
	}
	
	public void setGroups(List<GroupCode> groups) {
		this.groups.clear();
		this.groups.addAll(groups);
	}
	
	public List<ComCode> getCodes(String groupCd){
		List<ComCode> codes = new ArrayList<>();
		
		for(GroupCode groupCode : groups) {
			if(groupCd.equals(groupCode.getGroupCd())) {
				codes = groupCode.getCodes();
				break;
			}
		}
		
		return codes;
	}
	
	public String getCodeNm(String groupCd, String codeCd) {
		List<ComCode> codes = getCodes(groupCd);
		
		String codeNm = "";
		for(ComCode comCode : codes) {
			if(codeCd.equals(comCode.getValue())) {
				codeNm = comCode.getName();
				break;
			}
		}
		
		return codeNm;
	}

}
