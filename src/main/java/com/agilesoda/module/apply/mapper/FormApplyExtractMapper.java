package com.agilesoda.module.apply.mapper;

import com.agilesoda.module.apply.vo.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FormApplyExtractMapper {

    FormApplDetailVO selectFormApplDetail(@Param("applId") long applId);

    List<FormApplFldVO> selectFormApplFldList(@Param("applId") long applId);

    void updateFormApplFld(FormApplFldVO fld);

    long selectNextFldId();

    void insertFormAppl(FormApplInsertVO formAppl);

    void updateFormAppl(@Param("applId") long applId, @Param("formNm") String formNm,
            @Param("modId") String modId, @Param("modDt") String modDt);

    void upsertFormApplWriteCntn(@Param("applId") long applId, @Param("srcTypeCd") String srcTypeCd,
            @Param("writeCntn") String writeCntn, @Param("regId") String regId, @Param("regDt") String regDt);

    List<FormApplWriteCntnVO> selectFormApplWriteCntnList(@Param("applId") long applId);

    int submitFormAppl(@Param("applId") long applId, @Param("modId") String modId, @Param("modDt") String modDt);

    void insertFormApplFldAiSuggest(@Param("applId") long applId, @Param("fldList") List<FormApplFldVO> fldList,
            @Param("regId") String regId, @Param("regDt") String regDt);

    void insertFormApplImg(FormApplImgVO img);

    List<FormApplImgVO> selectFormApplImgList(@Param("applId") long applId);

    FormApplImgVO selectFormApplImg(@Param("imgId") long imgId);

    void insertFormApplFile(FormApplFileVO file);

    List<FormApplFileVO> selectFormApplFileList(@Param("applId") long applId);

    int deleteFormApplMgrByApplId(@Param("applId") long applId);

    void insertFormApplMgrList(@Param("applId") long applId, @Param("mgrList") List<FormApplMgrVO> mgrList,
                                    @Param("regId") String regId, @Param("regDt") String regDt);

    List<FormApplMgrVO> selectFormApplMgrList(@Param("applId") long applId);

    int deleteApprvlLineByApplId(@Param("applId") long applId);

    void insertApprvlLineList(@Param("applId") long applId, @Param("apprvlLineList") List<ApprvlLineVO> apprvlLineList,
                               @Param("regId") String regId, @Param("regDt") String regDt);


    FormApplFileVO selectFormApplFile(@Param("fileId") long fileId);
    int deleteFormApplFile(@Param("fileId") long fileId, @Param("applId") long applId);
    int deleteFormApplImg(@Param("imgId") long imgId, @Param("applId") long applId);

    int deleteFormApplFldByImgId(@Param("imgId") long imgId, @Param("applId") long applId);
    int selectFormApplImgCount(@Param("applId") long applId);
    int deleteFormApplFldByApplId(@Param("applId") long applId);
    int deleteFormApplImgByApplId(@Param("applId") long applId);
    int deleteFormApplFileByApplId(@Param("applId") long applId);
    int deleteFormApplWriteCntnByApplId(@Param("applId") long applId);
    int deleteFormAppl(@Param("applId") long applId);

    List<StaffVO> selectStaffListByName(@Param("keyword") String keyword);
    int cancelFormAppl(@Param("applId") long applId, @Param("modId") String modId);

    int updateFormApplFldRule(@Param("applId") long applId, @Param("fld") FormApplFldVO fld, @Param("modId") String modId);
    int updateFormApplFldMeta(@Param("applId") long applId, @Param("fld") FormApplFldVO fld, @Param("modId") String modId);
    void insertFormApplFldMatchList(@Param("fldId") long fldId, @Param("matchFldIdList") List<String> matchFldIdList, @Param("regId") String regId);
    int deleteFormApplFldMatchByFldId(@Param("fldId") long fldId);
    List<FormApplFldMatchVO> selectFormApplFldMatchList(@Param("applId") long applId);
    List<CodeDtlVO> selectRuleMatchCdList();

    int deleteFormApplFldMatchByImgId(@Param("imgId") long imgId, @Param("applId") long applId);
}
