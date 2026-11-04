package com.agilesoda.module.common;

import com.agilesoda.module.admin.vo.SsoLoginUserVO;
import com.agilesoda.module.common.entity.CommonExcelEntity;
import com.agilesoda.module.common.entity.CommonMenuEntity;
import com.agilesoda.module.common.entity.OcuocAprvlEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface CommonMapper {
    List<Map<String, Object>> selectListComCode(String groupCd) throws Exception;

    List<Map<String, Object>> selectListEmpInfo(Map<String, Object> param) throws Exception;

    int registApproval(OcuocAprvlEntity entity) throws Exception;

    List<CommonMenuEntity> commonMenuList(@Param("menuId") String menuId , @Param("userId") String userId);

    List<CommonMenuEntity> commonRoleButtonList(@Param("userId") String userId);

    SsoLoginUserVO selectUserInfo(@Param("userId") String userId);

    int insertExcelDownloadHistory(CommonExcelEntity vo);
}
