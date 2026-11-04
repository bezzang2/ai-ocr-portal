package com.agilesoda.module.community.mapper;


import com.agilesoda.module.community.vo.NoticeDeleteVO;
import com.agilesoda.module.community.vo.NoticeInqVO;
import com.agilesoda.module.community.vo.NoticeResVO;
import com.agilesoda.module.community.vo.NoticeAttachFileVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface NoticeMapper {
    // 공지사항 조회
    List<NoticeResVO> selectNotice(NoticeInqVO vo);

    // 공지사항 총 카운터
    int selectNoticeCount(NoticeInqVO vo);

    // 공지사항 등록
    int insertNotice(NoticeResVO vo);

    // 공지사항 메인 여러건 삭제
    int deleteNoticeSeq(@Param("noticeSeqno") Integer noticeSeqno,@Param("noticeTypeCd") String noticeTypeCd );

    // 첨부파일 저장
    int insertAttachFile(NoticeAttachFileVO vo);


    // 첨부파일 조회
    List<NoticeAttachFileVO> selectAttachFileList(String atchFileId);

    int deleteAttachFileList(String atchFileId);


    // 공지사항 상세조회
    Map<String, Object> selectNoticeDetail(@Param("noticeSeqno") Integer noticeSeqno,@Param("noticeTypeCd") String noticeTypeCd );

    // 공지사항 상세수정
    int updateDetailNotice(NoticeResVO vo);

    // 첨브파일 1건 조회
    NoticeAttachFileVO selectAttachFile(@Param("atchFileId") String atchFileId,@Param("fileSeq") int fileSeq );

    // 공지사항 조회수 증가
    int updateNoticeViewCnt(@Param("noticeSeqno") Integer noticeSeqno,@Param("noticeTypeCd") String noticeTypeCd);


}
