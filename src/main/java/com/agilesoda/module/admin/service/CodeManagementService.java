package com.agilesoda.module.admin.service;

import com.agilesoda.module.admin.entity.ComCodeEntity;
import com.agilesoda.module.admin.entity.GroupCodeEntity;
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

	// 그룹 + 상세 동시 저장
	@Transactional
	public int saveGroupCode(GroupCodeEntity entity) throws Exception{
		// 그룹 저장 전 그룹있는지 확인
		int cnt = codeManagementMapper.groupCnt(entity.getGroupCd());
		if(cnt == 0){
			cnt = codeManagementMapper.insertGroupCode(entity);
		}

		return cnt;
	}

	@Transactional
	public void saveComCode(List<ComCodeEntity> list) throws Exception{
		for(ComCodeEntity entity : list){
			codeManagementMapper.insertComCode(entity);
		}
	}

	// 그룹 + 상세 삭제
	@Transactional
	public void deleteGroupCode(String cdGroup) throws Exception{

		// 상세코드가 있으면 상세먼저 삭제
		codeManagementMapper.deleteCodeDetailByGroup(cdGroup);
		// 그룹삭제
		codeManagementMapper.deleteGroupCode(cdGroup);
	}

	// 상세 삭제
	@Transactional
	public int deleteComCode(ComCodeEntity entity) throws Exception{
		return codeManagementMapper.deleteCodeDetail(entity);
	}
}
