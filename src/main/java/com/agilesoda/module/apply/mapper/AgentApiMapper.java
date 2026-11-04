package com.agilesoda.module.apply.mapper;

import com.agilesoda.module.apply.vo.LefrmMatchVO;
import com.agilesoda.module.apply.vo.OcrWcVO;
import com.agilesoda.module.apply.vo.OcrWfMVO;
import com.agilesoda.module.apply.vo.OcrWllVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AgentApiMapper {

    void upsertOcrWfM(OcrWfMVO ocrWfM);

    List<LefrmMatchVO> selectFormsForLink();

    List<OcrWllVO> selectAllOcrWll();

    List<OcrWcVO> selectStoredClasses();

    void deleteOcrWllByWkId(@Param("wkId") String wkId);

    void insertOcrWll(OcrWllVO ocrWll);

    void deleteOcrWcByWkId(@Param("wkId") String wkId);

    void insertOcrWc(@Param("wkId") String wkId, @Param("classSeq") int classSeq,
                     @Param("className") String className, @Param("docTypeCode") String docTypeCode);
}
