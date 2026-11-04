package com.agilesoda.module.apply.mapper;

import com.agilesoda.module.apply.vo.FormLinkClassVO;
import com.agilesoda.module.apply.vo.FormLinkWorkflowVO;
import com.agilesoda.module.apply.vo.FormMasterVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FormLinkMapper {

    List<FormLinkWorkflowVO> selectWorkflowList();

    List<FormLinkClassVO> selectClassList(@Param("wkId") String wkId, @Param("unlinkedOnly") boolean unlinkedOnly);

    List<FormMasterVO> selectFormList();

    FormMasterVO selectForm(@Param("lefrmId") long lefrmId);

    int countDocId(@Param("docId") String docId, @Param("exceptLefrmId") Long exceptLefrmId);

    void insertForm(@Param("form") FormMasterVO form, @Param("userId") String userId);

    int updateForm(@Param("form") FormMasterVO form, @Param("userId") String userId);

    int deleteLinksOfForm(@Param("lefrmId") long lefrmId);

    int insertLinksOfForm(@Param("lefrmId") long lefrmId, @Param("userId") String userId);

    int countMenuPermission(@Param("userId") String userId, @Param("menuUrl") String menuUrl);
}
