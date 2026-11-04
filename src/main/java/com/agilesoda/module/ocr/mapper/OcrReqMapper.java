package com.agilesoda.module.ocr.mapper;

import com.agilesoda.module.ocr.vo.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 판독결과 화면의 업무·서식 설정(Portal 데이터). OCR 판독 데이터는 OcrAgentDataClient 로 받는다. */
@Mapper
public interface OcrReqMapper {
    List<OcrResultWkGroupVO> selectWkGroupList(@Param("wkId") String wkId);

    List<OcrResultLefrmVO> selectWkFormList(@Param("wkId") String wkId);

    OcrResultLefrmVO selectWkForm(@Param("lefrmId") long lefrmId, @Param("wkId") String wkId);
}
