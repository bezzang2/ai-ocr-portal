package com.agilesoda.module.apply.mapper;

import java.util.List;
import java.util.Map;

import com.agilesoda.module.apply.entity.*;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OcrTaskApplyMapper {
    //	업무 초기 등록(OCUOC_BZWKA_M)
    int registTask(OcuocBzwkaEntity ocuocBzwkaEntity) throws Exception;
    //	업무 서식 등록(OCUOC_BZWKF_L)
    int registMappingTaskForm(OcuocBzwkfEntity ocuocBzwkfEntity) throws Exception;
    //	업무 Rule 등록(OCUOC_BZAFF_L)
    int registRule(OcuocBzaffEntity ocuocBzaffEntity) throws Exception;
    //	업무 Rule Field 등록(OCUOC_BZAFR_L)
    int registRuleFieldMapping(OcuocBzafrEntity ocuocBzwfrEntity) throws Exception;
    //	업무 첨부파일 등록(OCUOC_BZAAF_L)
    int registAttachFile(OcuocBzaafEntity ocuocBzaafEntity) throws Exception;
    //	업무 화면번호 등록(OCUOC_BZSCN_L)
    int registScnNO(OcuocBzscnEntity ocuocBzscnEntity) throws Exception;

    //  업무 전체 count 조회
    int selectTaskCnt(TaskInfoReqEntity taskInfoReqEntity) throws Exception;

    //	업무 목록 조회
    List<TaskInfoResEntity> selectListTask(TaskInfoReqEntity taskInfoReqEntity) throws Exception;
    
    //	업무 목록 조회
    List<TaskListResEntity> selectListTaskWkflow(String keyword) throws Exception;

    //	업무 상세 조회
    TaskInfoResEntity selectTaskDetail(String appcId) throws Exception;
    
    //  업무 workflow_id 등록
    int updateWkflowId(Map<String, Object> param) throws Exception;
}
