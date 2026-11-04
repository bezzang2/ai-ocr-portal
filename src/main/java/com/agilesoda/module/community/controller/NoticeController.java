package com.agilesoda.module.community.controller;



import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.agilesoda.module.admin.vo.SsoLoginUserVO;
import com.agilesoda.module.community.service.NoticeService;
import com.agilesoda.module.community.vo.NoticeAttachFileVO;
import com.agilesoda.module.community.vo.NoticeInqVO;
import com.agilesoda.module.community.vo.NoticeResVO;
import com.agilesoda.module.community.vo.NoticeDeleteVO;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;
import java.nio.file.Paths;

/****************************************************
 *   notice / board /faq 화면 호출 서비스를 관리하는 Controller
 *****************************************************/
@Slf4j
@Controller
@RequestMapping("/module/community")
@RequiredArgsConstructor
@Tag(name = "공지사항", description = "공지 조회·저장 및 첨부파일 다운로드")
public class NoticeController {

    @Value("${aiocr.file.path}")
    private String filePath;

    private final NoticeService noticeService;

    /****************************************************
     *   notice 공지사항 화면 연결
     *****************************************************/
    @GetMapping("/notice")
    public String notice(@RequestParam(value = "menuId", defaultValue = "") String menuId,
                         Model model) throws Exception {
        log.debug("[notice] 시작");

        model.addAttribute("menuId", menuId);
        return "module/community/notice";

    }

    /****************************************************
     *   notice 리스트  조회
     *****************************************************/
    @Operation(summary = "공지사항 목록 조회")
    @PostMapping("/selectNotice")
    @ResponseBody
    public Map<String, Object> selectNotice(@RequestBody NoticeInqVO vo) {
        log.debug("[공지사항 조회] 시작");

        return noticeService.selectNotice(vo);
    }

    /****************************************************
     *   notice 등록 화면 연결
     *****************************************************/
    @GetMapping("/noticeRegister")
    public String noticeRegister(@RequestParam(value = "mode", defaultValue = "insert") String mode,
                                 @RequestParam(value = "menuId", defaultValue = "") String menuId,
                                 Model model) {
        log.debug("[공지사항 등록] 시작");

        model.addAttribute("mode", mode);
        model.addAttribute("menuId", menuId);

        return "/module/community/noticeRegister";
    }

    /****************************************************
     *   notice 등록 저장
     *****************************************************/
    @Operation(summary = "공지사항 등록")
    @PostMapping("/insertNotice")
    @ResponseBody
    public Map<String, Object> insertNotice(@RequestParam("noticeTtl") String noticeTtl,
                                            @RequestParam("noticeCn") String noticeCn,
                                            @RequestParam("useYn") String useYn,
                                            @RequestParam("noticeMsgDvcd") String noticeMsgDvcd,
                                            @RequestParam("stdd") String stdd,
                                            @RequestParam("endt") String endt,
                                            @RequestParam("noticeTypeCd") String noticeTypeCd,
                                            @RequestParam("faqAnsmer") String faqAnsmer,
                                            @RequestParam(value = "files", required = false) List<MultipartFile> files,
                                            HttpSession session) {
        log.info("[공지사항 등록] 시작 noticeTypeCd={} userId={} fileCount={}", noticeTypeCd, SsoLoginUserVO.userIdOf(session), files == null ? 0 : files.size());
        SsoLoginUserVO ssoLoginUserVO = (SsoLoginUserVO) session.getAttribute("ssoLoginUser");
        Map<String, Object> result = new HashMap<>();
        try {
            NoticeResVO vo = new NoticeResVO();
            vo.setNoticeTtl(noticeTtl);
            vo.setNoticeCn(noticeCn);
            vo.setUseYn(useYn);
            vo.setNoticeMsgDvcd(noticeMsgDvcd);
            vo.setStdd(stdd);
            vo.setEndt(endt);
            vo.setNoticeTypeCd(noticeTypeCd);
            vo.setFaqAnsmer(faqAnsmer);
            vo.setLastChngEmpno(ssoLoginUserVO.getUserId());

            noticeService.insertNotice(vo, files);
            result.put("result", "SUCCESS");
            log.info("[공지사항 등록] 완료 noticeTypeCd={} userId={}", noticeTypeCd, vo.getLastChngEmpno());
        } catch (Exception e) {
            log.error("[공지사항 등록] 실패 noticeTypeCd={} userId={}", noticeTypeCd, SsoLoginUserVO.userIdOf(session), e);
            result.put("result", "FAIL");
            result.put("message", "처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return result;
    }

    /****************************************************
     *   notice 화면 삭제
     *****************************************************/
    @Operation(summary = "공지사항 삭제")
    @PostMapping("/deleteNotice")
    @ResponseBody
    public Map<String, Object> deleteNotice(@RequestBody List<NoticeDeleteVO> list) throws Exception{
        log.info("[공지사항 삭제] 시작 requestCount={}", list == null ? 0 : list.size());
        int deleteCount = noticeService.deleteNotice(list);
        log.info("[공지사항 삭제] 완료 requestCount={} deleteCount={}", list == null ? 0 : list.size(), deleteCount);
        Map<String, Object> result = new HashMap<>();
        result.put("result",deleteCount);
        return result;
    }

    /****************************************************
     *   notice 상세 화면 연결
     *****************************************************/
    @GetMapping("/noticeDetail")
    public String noticeDetail(@RequestParam(value = "mode", defaultValue = "update") String mode,
                               @RequestParam(value = "menuId", defaultValue = "") String menuId,
                               @RequestParam(value = "noticeSeqno", defaultValue = "") Integer noticeSeqno,
                               @RequestParam(value = "noticeTypeCd", defaultValue = "") String noticeTypeCd,
                               Model model) {
        log.debug("[공지사항 상세] 시작");
        if ("update".equals(mode) && noticeSeqno != null) {
            // 조회수 증가
            noticeService.updateNoticeViewCnt(noticeSeqno,noticeTypeCd);

            // 공시사항 상세 조회
            Map<String, Object> detail = noticeService.selectNoticeDetail(noticeSeqno,noticeTypeCd);
            model.addAttribute("detail", detail);

            // 첨부파일 조회
            if (detail != null) {
                String atchFileId = null;
                Object atchFileIdObj = detail.get("atchFileId");
                log.debug("[공지사항 상세] 첨부파일 ID 확인 noticeSeqno={} hasAtchFileId={}", noticeSeqno, atchFileIdObj != null);
                if (atchFileIdObj != null) {
                    atchFileId = String.valueOf(atchFileIdObj);
                }
                if (atchFileId != null && !atchFileId.isBlank()) {
                    List<NoticeAttachFileVO> fileList = noticeService.selectAttachFileList(atchFileId);
                    model.addAttribute("fileList", fileList);
                }
            }
        }
        model.addAttribute("mode",mode);
        model.addAttribute("menuId",menuId);
        model.addAttribute("noticeId",noticeSeqno);


        if("NOTICE".equals(noticeTypeCd)){
            return "/module/community/noticeDetail";
        }
        if("BOARD".equals(noticeTypeCd)){
            return "/module/community/boardDetail";
        }
        if("FAQ".equals(noticeTypeCd)){
            return "/module/community/faqDetail";
        }
        return "/module/community/noticeDetail";
    }

    /****************************************************
     *   notice 상세 (수정)
     *****************************************************/
    @Operation(summary = "공지사항 수정")
    @PostMapping("/updateDetailNotice")
    @ResponseBody
    public Map<String, Object> updateDetailNotice(NoticeResVO vo,
                                                  @RequestParam(value = "files", required = false) List<MultipartFile> files,
                                                  HttpSession session) {
        log.info("[공지사항 수정] 시작 noticeSeqno={} userId={}", vo.getNoticeSeqno(), SsoLoginUserVO.userIdOf(session));
        Map<String, Object> result = new HashMap<>();
        SsoLoginUserVO ssoLoginUserVO = (SsoLoginUserVO) session.getAttribute("ssoLoginUser");
        try {
            vo.setLastChngEmpno(ssoLoginUserVO.getUserId());
            noticeService.updateDetailNotice(vo,files);
            result.put("result","SUCCESS");
            log.info("[공지사항 수정] 완료 noticeSeqno={} userId={}", vo.getNoticeSeqno(), vo.getLastChngEmpno());
        }catch(Exception e){
            log.error("[공지사항 수정] 실패 noticeSeqno={} userId={}", vo.getNoticeSeqno(), SsoLoginUserVO.userIdOf(session), e);
            result.put("result","FAIL");
            result.put("message","처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return result;
    }

    /****************************************************
     *   notice 상세 (파일다운로드)
     *****************************************************/
    @ApiResponse(responseCode = "200", description = "공지사항 첨부파일",
            content = @Content(mediaType = "application/octet-stream", schema = @Schema(type = "string", format = "binary")))
    @Operation(summary = "공지사항 첨부파일 다운로드")
    @GetMapping("/fileDownload")
    @ResponseBody
    public ResponseEntity<Resource> fileDownload(@RequestParam("atchFileId") String atchFileId,
                                                 @RequestParam("fileSeq") int fileSeq) throws Exception{
        log.debug("[첨부파일 다운로드] 시작 atchFileId={} fileSeq={}", atchFileId, fileSeq);
        // DB에서 첨부파일 정보 조회
        NoticeAttachFileVO fileVO = noticeService.selectAttachFile(atchFileId,fileSeq);

        if(fileVO == null){
            log.warn("[첨부파일 다운로드] 첨부파일 정보 없음 atchFileId={} fileSeq={}", atchFileId, fileSeq);
            return ResponseEntity.notFound().build();
        }
        // 실제 파일 경로
        String dbFilePath = fileVO.getFilePath();
        if(dbFilePath.startsWith("/")){
            dbFilePath = dbFilePath.substring(1);
        }
        Path path = Paths.get(filePath,dbFilePath,fileVO.getSaveFileNm()).normalize();
        if(!Files.exists(path)){
            log.warn("[첨부파일 다운로드] 저장 파일 없음 atchFileId={} fileSeq={}", atchFileId, fileSeq);
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(path);
        String orgFileNm = fileVO.getOrgFileNm();
        ContentDisposition disposition = ContentDisposition.attachment().filename(orgFileNm,StandardCharsets.UTF_8).build();

        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,disposition.toString()).contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(Files.size(path)).body(resource);

    }

    /****************************************************
     *   board 등록 화면 연결
     *****************************************************/
    @GetMapping("/boardRegister")
    public String boardRegister(@RequestParam(value = "mode", defaultValue = "insert") String mode,
                                @RequestParam(value = "menuId", defaultValue = "") String menuId,
                                Model model) {
        log.debug("[계시판 등록] 시작");

        model.addAttribute("mode", mode);
        model.addAttribute("menuId", menuId);

        return "/module/community/boardRegister";
    }

    /****************************************************
     *   board 계시판  화면 연결
     *****************************************************/
    @GetMapping("/board")
    public String board(@RequestParam(value = "menuId", defaultValue = "") String menuId,
                        Model model) throws Exception {
        log.debug("[board] 시작");

        model.addAttribute("menuId",menuId);
        return "module/community/board";

    }

    /****************************************************
     *   faq 화면 연결
     *****************************************************/
    @GetMapping("/faq")
    public String faq(@RequestParam(value = "menuId", defaultValue = "") String menuId,
                      Model model) throws Exception {
        log.debug("[faq] 시작");

        model.addAttribute("menuId",menuId);
        return "module/community/faq";

    }

    /****************************************************
     *   board 등록 화면 연결
     *****************************************************/
    @GetMapping("/faqRegister")
    public String faqRegister(@RequestParam(value = "mode", defaultValue = "insert") String mode,
                              @RequestParam(value = "menuId", defaultValue = "") String menuId,
                              Model model) {
        log.debug("[FAQ 등록] 시작");

        model.addAttribute("mode", mode);
        model.addAttribute("menuId", menuId);

        return "/module/community/faqRegister";
    }


}

