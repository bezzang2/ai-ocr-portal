package com.agilesoda.module.admin.mapper;

import com.agilesoda.module.admin.vo.CodeManagementDetailResVO;
import com.agilesoda.module.admin.vo.CodeManagementInqVO;
import com.agilesoda.module.admin.vo.CodeManagementResVO;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface CodeManagementMapper {

    // 그룹코드 조회ㅑ
    List<CodeManagementResVO> searchGropCode(CodeManagementInqVO vo);

    // 상세코드 조회
    List<CodeManagementDetailResVO> selectCodeDetail(@Param("cdGroup") String cdGroup);

    // 그룹코드 등록
    int insertGroupCode(CodeManagementResVO vo);

    int insertGroupDetail(CodeManagementDetailResVO vo);

    // 그룹에속한 상세코드 삭제
    int deleteCodeDetailByGroup(@Param("cdGroup") String cdGroup);

    // 그룹코드 삭제
    int deleteGroupCode(@Param("cdGroup") String cdGroup);

    // 코드상세 삭제
    int deleteCodeDetail(Map<String, Object> param);

    int groupCnt(CodeManagementResVO vo);

}
