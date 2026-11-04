package com.agilesoda.module.apply.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.agilesoda.module.apply.vo.CodeDtlVO;
import com.agilesoda.module.apply.vo.FormApplSearchVO;
import com.agilesoda.module.apply.vo.FormApplVO;

@Mapper
public interface FormApplyMapper {

	/**
	 * 조건 진행상태
	 */
	List<CodeDtlVO> selectProcStatList();

	/**
	 * OCR서식신청 조회
	 */
	List<FormApplVO> selectFormApplList(FormApplSearchVO searchVO);

	/**
	 * 페이징
	 */
	int selectFormApplCount(FormApplSearchVO searchVO);

	/**
	 * 엑셀다운
	 */
	List<FormApplVO> selectFormApplListAll(FormApplSearchVO searchVO);
}
