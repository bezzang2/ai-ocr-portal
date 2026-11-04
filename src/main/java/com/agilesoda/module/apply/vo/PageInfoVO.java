package com.agilesoda.module.apply.vo;

import lombok.Data;

@Data
public class PageInfoVO {
	
	private static final int PAGE_GROUP_SIZE = 5;

	private final int pageNo;
	private final int pageSize;
	private final int totalCount;
	private final int totalPageCount;
	private final int startPage;
	private final int endPage;
	private final boolean hasPrevGroup;
	private final boolean hasNextGroup;
	private final int rangeStart;
	private final int rangeEnd;
	
	public PageInfoVO(int pageNo, int pageSize, int totalCount) {
		this.pageNo = pageNo;
		this.pageSize = pageSize;
		this.totalCount = totalCount;
		this.totalPageCount = totalCount == 0 ? 1 : (int) Math.ceil((double) totalCount / pageSize);
		
		int currentGroup = (pageNo - 1) / PAGE_GROUP_SIZE;
		this.startPage = currentGroup * PAGE_GROUP_SIZE + 1;
		this.endPage = Math.min(startPage + PAGE_GROUP_SIZE -1, totalPageCount);
		this.hasPrevGroup = startPage > 1;
		this.hasNextGroup = endPage < totalPageCount;
		
		this.rangeStart = totalCount == 0 ? 0 : (pageNo - 1) * pageSize + 1;
		this.rangeEnd = Math.min(pageNo * pageSize, totalCount);
	}
}
