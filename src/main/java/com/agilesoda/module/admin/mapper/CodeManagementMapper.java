package com.agilesoda.module.admin.mapper;

import com.agilesoda.module.admin.entity.ComCodeEntity;
import com.agilesoda.module.admin.entity.GroupCodeEntity;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface CodeManagementMapper {

    // 그룹코드 등록
    int insertGroupCode(GroupCodeEntity entity) throws Exception;

    int insertComCode(ComCodeEntity entity) throws Exception;

    // 그룹에속한 상세코드 삭제
    int deleteCodeDetailByGroup(String cdGroup) throws Exception;

    // 그룹코드 삭제
    int deleteGroupCode(String cdGroup) throws Exception;

    // 코드상세 삭제
    int deleteCodeDetail(ComCodeEntity entity) throws Exception;

    int groupCnt(String groupCd) throws Exception;

}
