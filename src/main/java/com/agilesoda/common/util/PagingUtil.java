package com.agilesoda.common.util;

import lombok.Data;

@Data
public class PagingUtil {
	//	한 페이지에 보일 게시물 사이즈
	private int pageSize;
	
	//	총 페이지 개수
	private int totalPage;
	
	//	현재 시작 페이지
	private int pageNum;
	
	//	페이지 보여줄 단위
	private int pageNavSize;
	
	//	총 게시물 개수
	private int totalRecord;
	
	//	게시물의 시작번호
	private int startRowIndex;
	
	//	게시물의 끝번호. 마지막 페이지는 한 페이지 크기보다 적을 수 있어 총 개수를 넘지 않게 한다. 게시물이 없으면 0
	private int endRowIndex;
	
	//	페이지의 시작번호
	private int firstPageNo;
	
	//	페이지의 마지막 번호
	private int lastPageNo;
	
	//	이전 페이지 유무
	private boolean hasPrevPageNav;
	
	//	다음 페이지 유무
	private boolean hasNextPageNav;
	
	//	맨 첫번째 페이지
	private boolean hasFirstPageNav;
	
	//	맨 마지막 페이지
	private boolean hasLastPageNav;
	
	public PagingUtil(int pageNum, int totalRecord, int pageSize, int pageNavSize) {
		this.pageNum = pageNum;
		this.totalRecord = totalRecord;
		this.pageSize = pageSize;
		this.pageNavSize = pageNavSize;
		
		if( this.pageNum < 1) {
			this.pageNum = 1;
		}
		
		if( this.pageNavSize < 1) {
			this.pageSize = 10;
		}

		calculation();
	}
	
	private void calculation() {
		/*	페이지 수 */
		this.totalPage = (int)(((this.totalRecord - 1) / this.pageSize) + 1 );
		if( this.pageNum > this.totalPage) {
			this.pageNum = this.totalPage;
		}
		
		/*	현재 페이지번호로 페이지 링크 블럭의 시작페이지번호화 마지막페이지번호 취득	*/
		this.firstPageNo = (((int) Math.ceil((float)this.pageNum / (float)this.pageNavSize)) -1) * this.pageNavSize + 1;
		this.lastPageNo = this.firstPageNo + this.pageNavSize - 1;
		
		if( this.lastPageNo > this.totalPage) {
			this.lastPageNo = this.totalPage;
		}
		
		//this.startRowIndex = this.totalRecord - ((this.pageNum - 1) * this.pageSize);
		this.startRowIndex = ((this.pageNum - 1) * this.pageSize) + 1;
		this.endRowIndex = Math.min(this.pageNum * this.pageSize, this.totalRecord);
		
		this.hasPrevPageNav = this.firstPageNo == 1 ? false : true;
		this.hasNextPageNav = (this.lastPageNo * this.pageSize) < this.totalRecord;
		
		this.hasFirstPageNav = (this.pageNum > 1 && this.pageNavSize < this.pageNum);
		this.hasLastPageNav = (this.pageNum < this.totalPage && this.lastPageNo + 1 < this.totalPage);
	}
}
