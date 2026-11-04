package com.agilesoda.module.admin.mapper;

import com.agilesoda.module.admin.vo.*;
import com.agilesoda.module.admin.vo.ExcelDownloadVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AuditLogMapper {

    // 감사로그 조회
    List<AuditLogResVO> selectLogLsit(AuditLogInqVO vo);

    // 로그카운터
    int selectLogCount(AuditLogInqVO vo);

    // 메뉴 접근 TOP5
    List<Map<String, Object>> selectMenuTop5(Map<String, Object> params);

    List<Map<String, Object>> selectButtonTop5(Map<String, Object> params);

    Map<String, Object> selectAuditSummary(Map<String, Object> params);

    //Map<String, Object> selectDeptSummary(Map<String, Object> params);

    List<Map<String, Object>> selectAuditDetail(Map<String, Object> params);

    int selectMenuCount(Map<String, Object> params);

    List<Map<String, Object>> selectAuditMenuExcel(Map<String, Object> params);

    List<Map<String, Object>> selectAuditDeptExcel(Map<String, Object> params);

    List<Map<String, Object>> selectTopMenuList();

    int insertExcelDownloadHistory(ExcelDownloadVO vo);

    int insertAuditLog(Map<String, Object> param);

    // 사용자정보 조회
    List<CommonDeptListVO> searchDeptList(@Param("brCd") String brCd, @Param("brNm") String brNm);

}
