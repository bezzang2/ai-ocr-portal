package com.agilesoda.module.admin.service;

import com.agilesoda.module.admin.mapper.AuditLogMapper;
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
public class AuditLogService {
	
	private final AuditLogMapper auditLogMapper;

	public Map<String, Object> selectLogLsit(AuditLogInqVO vo) throws Exception{
		int page = vo.getPage() == null ? 1 : vo.getPage();
		int pageSize = vo.getPageSize() == null ? 15 : vo.getPageSize();
		int offset = (page - 1) * pageSize;
		vo.setPage(page);
		vo.setPageSize(pageSize);
		vo.setOffset(offset);

		// 현재 페이지 목록
		List<AuditLogResVO> list = auditLogMapper.selectLogLsit(vo);
		// 전체 건수
		int totalCount = auditLogMapper.selectLogCount(vo);
		// 전체 페이지 수
		int totalPage = (int) Math.ceil((double)totalCount/pageSize);

		Map<String, Object> result = new HashMap<>();

		result.put("list",list);
		result.put("totalCount",totalCount);
		result.put("totalPage",totalPage);
		result.put("page",page);
		result.put("pageSize",pageSize);

		return result;
	}

	public List<Map<String, Object>> selectMenuTop5(Map<String, Object> params){
		return auditLogMapper.selectMenuTop5(params);
	}

	public List<Map<String, Object>> selectButtonTop5(Map<String, Object> params){
		return auditLogMapper.selectButtonTop5(params);
	}

	public Map<String, Object> selectAuditSummary(Map<String, Object> params){
		return auditLogMapper.selectAuditSummary(params);
	}

//	public Map<String, Object> selectDeptSummary(Map<String, Object> params){
//		return auditLogMapper.selectDeptSummary(params);
//	}

	public Map<String, Object> selectAuditDetail(Map<String, Object> params){

		int page = params.get("page") == null ? 1 : Integer.parseInt(params.get("page").toString());
		int pageSize = params.get("pageSize") == null ? 15 : Integer.parseInt(params.get("pageSize").toString());
		int offset = (page - 1) * pageSize;
		params.put("page",page);
		params.put("pageSize",pageSize);
		params.put("offset",offset);

		// 현재 페이지 목록
		List<Map<String, Object>> list = auditLogMapper.selectAuditDetail(params);
		// 전체 건수
		int totalCount = auditLogMapper.selectMenuCount(params);
		// 전체 페이지 수
		int totalPage = (int) Math.ceil((double)totalCount/pageSize);

		Map<String, Object> result = new HashMap<>();

		result.put("list",list);
		result.put("totalCount",totalCount);
		result.put("totalPage",totalPage);
		result.put("page",page);
		result.put("pageSize",pageSize);

		return result;
	}

	public List<Map<String, Object>> selectAuditMenuExcel(Map<String, Object> param){
		return auditLogMapper.selectAuditMenuExcel(param);
	}

	public List<Map<String, Object>> selectAuditDeptExcel(Map<String, Object> param){
		return auditLogMapper.selectAuditDeptExcel(param);
	}



	public List<Map<String, Object>> selectTopMenuList(){
		return auditLogMapper.selectTopMenuList();
	}

	@Transactional
	public int insertExcelDownloadHistory(ExcelDownloadVO vo){
		return auditLogMapper.insertExcelDownloadHistory(vo);
	}

	@Transactional
	public int insertAuditLog(Map<String, Object> param){
		int cnt = auditLogMapper.insertAuditLog(param);
		return cnt;
	}

	// 사용자정보 조회
	public List<CommonDeptListVO> searchDeptList(String brCd, String brNm){
		return auditLogMapper.searchDeptList(brCd,brNm);
	}

}
