package com.agilesoda.module.admin.service;

import com.agilesoda.module.admin.mapper.CodeManagementMapper;
import com.agilesoda.module.admin.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CodeManagementService {
	
	private final CodeManagementMapper codeManagementMapper;

	public Map<String, Object> searchGropCode(CodeManagementInqVO vo){

		// 현재 페이지 목록
		List<CodeManagementResVO> list = codeManagementMapper.searchGropCode(vo);
		Map<String, Object> result = new HashMap<>();
		result.put("list",list);
		return result;
	}

	public Map<String, Object> selectCodeDetail(String cdGroup){

		List<CodeManagementDetailResVO> detailList = codeManagementMapper.selectCodeDetail(cdGroup);
		Map<String, Object> result = new HashMap<>();
		result.put("detailList",detailList);
		return result;
	}

	// 그룹 + 상세 동시 저장
	@Transactional
	public void saveGroupCode(CodeManagementSaveVO saveVO){
		// 그룹 저장 전 그룹있는지 확인
		int cnt = codeManagementMapper.groupCnt(saveVO.getGroup());
		if(cnt == 0){
			codeManagementMapper.insertGroupCode(saveVO.getGroup());
		}

		// 상세 저장
		if(saveVO.getDetailList() != null){
			for(CodeManagementDetailResVO detail : saveVO.getDetailList()){
				codeManagementMapper.insertGroupDetail(detail);
			}
		}
	}

	// 그룹 + 상세 삭제
	@Transactional
	public void deleteGroupCode(String cdGroup){

		// 상세코드가 있으면 상세먼저 삭제
		codeManagementMapper.deleteCodeDetailByGroup(cdGroup);
		// 그룹삭제
		codeManagementMapper.deleteGroupCode(cdGroup);
	}

	// 상세 삭제
	@Transactional
	public int deleteCodeDetail(Map<String, Object> param){
		return codeManagementMapper.deleteCodeDetail(param);
	}
}
