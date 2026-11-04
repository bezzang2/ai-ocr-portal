package com.agilesoda.module.ocr.mapper;

import com.agilesoda.module.ocr.vo.OcrReviewerVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/** 판독결과 검토의 사용자·역할(Portal 데이터). 검토 상태·확인값은 Agent 가 소유한다. */
@Mapper
public interface OcrReviewMapper {
    String selectUserNm(@Param("userId") String userId);

    /** 사번별 이름(userId·userNm 만 채운다) */
    List<OcrReviewerVO> selectUserNmList(@Param("userIds") Collection<String> userIds);

    /** 관리자 역할(roles) 또는 팀장 직위(positionCodes)면 1 이상 */
    int countAssignAuthority(@Param("userId") String userId, @Param("roles") Collection<String> roles,
                             @Param("positionCodes") Collection<String> positionCodes);
}
