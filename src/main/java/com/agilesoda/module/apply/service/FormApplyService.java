package com.agilesoda.module.apply.service;

import java.util.List;

import com.agilesoda.module.apply.mapper.FormApplyMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agilesoda.module.apply.vo.CodeDtlVO;
import com.agilesoda.module.apply.vo.FormApplSearchVO;
import com.agilesoda.module.apply.vo.FormApplVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FormApplyService {
	
	private final FormApplyMapper formApplyMapper;

	/**
	 * 조회 진행상태
	 */
	public List<CodeDtlVO> getProcStatList() {
		return formApplyMapper.selectProcStatList();
	}

	/**
	 * OCR서식신청 조회
	 */
	public List<FormApplVO> getFormApplList(FormApplSearchVO searchVO) {
		return formApplyMapper.selectFormApplList(searchVO);
	}

	/**
	 * 엑셀다운
	 */
	public List<FormApplVO> getFormApplListAll(FormApplSearchVO searchVO) {
		return formApplyMapper.selectFormApplListAll(searchVO);
	}

	/**
	 * 페이징
	 */
	public int getFormApplCount(FormApplSearchVO searchVO) {
		return formApplyMapper.selectFormApplCount(searchVO);
	}

}
