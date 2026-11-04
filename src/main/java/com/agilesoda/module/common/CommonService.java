package com.agilesoda.module.common;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agilesoda.common.ComCodeManager;
import com.agilesoda.common.GroupCode;
import com.agilesoda.module.admin.vo.SsoLoginUserVO;
import com.agilesoda.module.common.entity.CommonExcelEntity;
import com.agilesoda.module.common.entity.CommonMenuEntity;
import com.agilesoda.module.common.entity.OcuocAprvlEntity;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommonService {

    private final CommonMapper commonMapper;
    
    @PostConstruct
    public void init() {
    	initialComCode();
    }
    
    public void initialComCode() {
    	try {
    		List<GroupCode> groups = commonMapper.selectAllComCode();
    		ComCodeManager.getInstance().setGroups(groups);
    	}
    	catch(Exception e) {
    		log.warn("[공통코드] 조회중 오류발생 => {}", e.getMessage());
    	}
    }
    
    public List<Map<String, Object>> getListComCode(String groupCd) throws Exception{
        return commonMapper.selectListComCode(groupCd);
    }

    /** limit 이 있으면 이름순 앞에서부터 그 건수만 반환한다(검색 선택 입력칸). */
    public List<Map<String, Object>> getListEmpInfo(int gubun, String keyword, Integer limit) throws Exception{
    	Map<String, Object> param = new HashMap<>();
    	param.put("gubun", gubun);
    	param.put("keyword", keyword);
    	param.put("limit", limit != null && limit > 0 ? Math.min(limit, 100) : null);
        return commonMapper.selectListEmpInfo(param);
    }

    public int registApproval(List<OcuocAprvlEntity> params) throws Exception{
        log.info("[registApproval] 시작");

        for(OcuocAprvlEntity entity : params){
            commonMapper.registApproval(entity);
        }

        log.info("[registApproval] 종료");
        return 0;
    }

    public List<CommonMenuEntity> commonMenuList(String menuId, String userId){
        return commonMapper.commonMenuList(menuId,userId);
    }

    public List<CommonMenuEntity> commonRoleButtonList(String userId){
        return commonMapper.commonRoleButtonList(userId);
    }

    public SsoLoginUserVO selectUserInfo(String userId){
        return commonMapper.selectUserInfo(userId);
    }

    @Transactional
    public int insertExcelDownloadHistory(CommonExcelEntity vo){
        return commonMapper.insertExcelDownloadHistory(vo);
    }
}
