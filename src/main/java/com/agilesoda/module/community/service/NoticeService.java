package com.agilesoda.module.community.service;

import com.agilesoda.module.community.vo.NoticeInqVO;
import com.agilesoda.module.community.vo.NoticeResVO;
import com.agilesoda.module.community.vo.NoticeDeleteVO;
import com.agilesoda.module.community.vo.NoticeAttachFileVO;
import com.agilesoda.module.community.mapper.NoticeMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {

    @Value("${aiocr.file.path}")
    private String filePath;

    private final NoticeMapper noticeMapper;

    public Map<String, Object> selectNotice(NoticeInqVO vo){
        int page = vo.getPage() == null ? 1 : vo.getPage();
        int pageSize = vo.getPageSize() == null ? 15 : vo.getPageSize();
        int offset = (page - 1) * pageSize;
        vo.setPage(page);
        vo.setPageSize(pageSize);
        vo.setOffset(offset);
        if(vo.getNoticeTtl() != null && !vo.getNoticeTtl().isBlank()){
            vo.setNoticeTtl("%"+vo.getNoticeTtl()+"%");
        }

        // 현재 페이지 목록
        List<NoticeResVO> list = noticeMapper.selectNotice(vo);
        // 전체 건수
        int totalCount = noticeMapper.selectNoticeCount(vo);
        // 전체 페이지 수
        int totalPage = (int) Math.ceil((double)totalCount/pageSize);

        Map<String, Object> result = new HashMap<>();

        result.put("list",list);
        result.put("totalCount",totalCount);
        result.put("totalPage",totalPage);
        result.put("page",page);
        result.put("pageSize",pageSize);

        return result;
    }

    @Transactional
    public void insertNotice(NoticeResVO vo, List<MultipartFile> files) throws Exception{

        String atchFileId = null;

        // 1. 첨부파일 있는지 확인
        if(files != null && !files.isEmpty()){
            boolean hasFile = false;
            for(MultipartFile file : files){
                if(file != null && !file.isEmpty()){
                    hasFile = true;
                    break;
                }
            }
            if(hasFile){
                atchFileId = UUID.randomUUID().toString().replace("-","");
                //메인 테이블에 그룹키 넣기
                vo.setAtchFileId(atchFileId);
            }
        }

        // 2. 공지사항 메인 테이블 insert
        noticeMapper.insertNotice(vo);

        // 3. 첨부파일이 없으면 종료
        if(atchFileId != null){
            saveAttachFile(atchFileId,files);
        }
    }

    public Map<String, Object> selectNoticeDetail(Integer noticeSeqno,String noticeTypeCd){
        return noticeMapper.selectNoticeDetail(noticeSeqno,noticeTypeCd);
    }

    public List<NoticeAttachFileVO> selectAttachFileList(String atchFileId) {
        return noticeMapper.selectAttachFileList(atchFileId);
    }

    public void updateDetailNotice(NoticeResVO vo,
                                  List<MultipartFile> files) throws Exception{
        String atchFileId = vo.getAtchFileId();

        if((atchFileId == null || atchFileId.isBlank()) && hasUploadFile(files)){
            atchFileId = UUID.randomUUID().toString().replace("-","");
            vo.setAtchFileId(atchFileId);;
        }

        // 공지사항 내용 수정
        noticeMapper.updateDetailNotice(vo);

//        if(hasUploadFile(files)){
//            saveAttachFile(atchFileId,files);
//        }
    }

    private void saveAttachFile(String atchFileId,
                                List<MultipartFile> files) throws Exception{
        if(files == null || files.isEmpty()){
            return;
        }

        // 실제 물리적인 서버경로
        String dbFilePath = "/community/notice/";
        String realPath = filePath+dbFilePath;

        // 폴더 없으면 생성
        File folder = new File(realPath);
        if(!folder.exists()){
            boolean created = folder.mkdirs();
            if(!created){
                throw new Exception("첨부파일 저장 폴더 생성에 실패했습니다.");
            }
        }

        int fileSeq = 1;
        for(MultipartFile file : files){
            if(file == null || file.isEmpty()){
                continue;
            }
            String originalFileName = file.getOriginalFilename();
            String fileExt = "";
            if(originalFileName != null && originalFileName.contains(".")){
                fileExt = originalFileName.substring(originalFileName.lastIndexOf(".")+1);
            }
            String saveFileName = UUID.randomUUID().toString().replace("-","");
            if(!fileExt.isEmpty()){
                saveFileName += "." + fileExt;
            }
            File saveFile = new File(realPath,saveFileName);
            file.transferTo(saveFile);

            NoticeAttachFileVO fileVO = new NoticeAttachFileVO();
            fileVO.setAtchFileId(atchFileId);
            fileVO.setFileSeq(fileSeq);
            fileVO.setOrgFileNm(originalFileName);
            fileVO.setSaveFileNm(saveFileName);
            fileVO.setFilePath(dbFilePath);
            fileVO.setFileExt(fileExt);
            fileVO.setFileSize(file.getSize());

            int result = noticeMapper.insertAttachFile(fileVO);

            fileSeq++;
        }
    }

    private boolean hasUploadFile(List<MultipartFile> files){
        if(files == null || files.isEmpty()){
            return false;
        }
        for(MultipartFile file : files){
            if(file != null && !file.isEmpty()){
                return true;
            }
        }
        return false;
    }

    public NoticeAttachFileVO selectAttachFile(String atchFileId, int fileSeq){
        return noticeMapper.selectAttachFile(atchFileId,fileSeq);
    }

    public int deleteNotice(List<NoticeDeleteVO> list) throws Exception{
        int deleteCount = 0;

        if(list == null || list.isEmpty()){
            return 0;
        }

        for(NoticeDeleteVO item : list){
            Integer noticeSeqno = item.getNoticeSeqno();
            String noticeTypeCd = item.getNoticeTypeCd();
            // 1. 공지 상세조회
            Map<String, Object> detail =  noticeMapper.selectNoticeDetail(noticeSeqno, noticeTypeCd);
            if(detail == null || detail.isEmpty()){
                continue;
            }
            // 2. 첨부파일 ID 확인
            Object atchFileIdObj = detail.get("atchFileId");
            if(atchFileIdObj != null){
                String atchFileId = String.valueOf(atchFileIdObj);
                if(!atchFileId.isBlank()){
                    // 3. 첨부파일 목록 조회
                    List<NoticeAttachFileVO> fileList = noticeMapper.selectAttachFileList(atchFileId);
                    // 4 실재 파일 삭제
                    if(fileList != null && !fileList.isEmpty()){
                        for(NoticeAttachFileVO fileVO : fileList){
                            String dbPath = fileVO.getFilePath();
                            if(dbPath == null){
                                dbPath = "";
                            }
                            dbPath = dbPath.replace("\\","/").replaceAll("^/+","").replaceAll("/+$","");
                            Path path = Paths.get(filePath,dbPath,fileVO.getSaveFileNm()).normalize();
                            Files.deleteIfExists(path);
                        }
                    }
                    // 5. 첨부파일 DB 삭제
                    noticeMapper.deleteAttachFileList(atchFileId);
                }
            }
            // 6. 공지사항 DB 삭제
            deleteCount += noticeMapper.deleteNoticeSeq(noticeSeqno,noticeTypeCd);
        }

        return deleteCount;
    }

    public int updateNoticeViewCnt(Integer noticeSeqno,String noticeTypeCd){
        return noticeMapper.updateNoticeViewCnt(noticeSeqno, noticeTypeCd);
    }

}
