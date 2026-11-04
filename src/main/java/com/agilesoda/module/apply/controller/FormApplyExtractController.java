package com.agilesoda.module.apply.controller;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.agilesoda.module.admin.vo.SsoLoginUserVO;
import com.agilesoda.module.apply.service.FormApplyExtractService;
import com.agilesoda.module.apply.vo.*;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Controller
@RequestMapping("/module/apply")
@RequiredArgsConstructor
@Tag(name = "서식 신청", description = "신청 임시저장·파일·이미지·추출 항목 관리")
public class FormApplyExtractController {
    private final FormApplyExtractService formApplyExtractService;

    /**
     * OCR서식신청 메뉴 링크
     */
    @GetMapping("/extract")
    public String menuLink(@RequestParam(value="applId", required = false) Long applId, Model model, HttpSession session, @RequestParam(value="menuId", defaultValue="") String menuId) throws Exception {
        return formApplyExtract(applId, model, session, menuId);
    }

    /**
     * OCR서식신청 저장
     */
    @Operation(summary = "서식 신청 임시저장")
    @PostMapping(value = "/tempSave", consumes = "multipart/form-data")
    public ResponseEntity<FormApplTempSaveResultVO> tempSaveFormAppl(
            @RequestPart("data") FormApplTempSaveRequestVO request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestPart(value = "formImages", required = false) List<MultipartFile> formImages,
            HttpSession session) {
        SsoLoginUserVO sessionUser = (SsoLoginUserVO) session.getAttribute("ssoLoginUser");
        if (sessionUser == null) {
            return ResponseEntity.status(401).build();
        }

        FormApplTempSaveResultVO result = formApplyExtractService.tempSaveFormAppl(
          request.getApplId(), request.getFormNm(), request.getWriteCntnList(),
          request.getFldList(), request.getGroupList(), files, request.getAttachSrcTypeCdList(), formImages,
          request.getFormImgList(), request.getActiveImgId(), request.getMgrList(), request.isSubmit(),
          sessionUser.getUserId(), null, sessionUser.getUserId());
        return ResponseEntity.ok(result);
    }


    /**
     * OCR서식신청 등록
     */
    private String formApplyExtract(@RequestParam(value="applId", required = false) Long applId, Model model, HttpSession session, @RequestParam(value="menuId", defaultValue="") String menuId) throws Exception {

        model.addAttribute("ruleMatchCdList", formApplyExtractService.getRuleMatchCdList());

        if (applId != null) {
            FormApplDetailVO formAppl = formApplyExtractService.getFormApplDetail(applId);
            AiSuggestPreviewResultVO fldDetail = formApplyExtractService.getFormApplFldDetail(applId);
            List<FormApplWriteCntnVO> writeCntnList = formApplyExtractService.getFormApplWriteCntnList(applId);
            model.addAttribute("applId", applId);
            model.addAttribute("formAppl", formAppl);
            model.addAttribute("fldList", fldDetail.getFldList());
            model.addAttribute("groupList", fldDetail.getGroupList());
            model.addAttribute("descGroupList", fldDetail.getDescGroupList());
            model.addAttribute("descWriteCntn", findWriteCntn(writeCntnList, "DESC"));
            model.addAttribute("itemWriteCntn", findWriteCntn(writeCntnList, "ITEM"));
            List<FormApplFileVO> fileList = formApplyExtractService.getFormApplFileList(applId);
            model.addAttribute("descFileList", filterFileList(fileList, "DESC"));
            model.addAttribute("itemFileList", filterFileList(fileList, "ITEM"));
            model.addAttribute("formImgList", formApplyExtractService.getFormApplImgList(applId));
            model.addAttribute("mgrList", formApplyExtractService.getFormApplMgrList(applId));
        } else {
            model.addAttribute("applId", 0L);
            model.addAttribute("formAppl", null);
            model.addAttribute("fldList", Collections.emptyList());
            model.addAttribute("groupList", Collections.emptyList());
            model.addAttribute("descGroupList", Collections.emptyList());
            model.addAttribute("descWriteCntn", null);
            model.addAttribute("itemWriteCntn", null);
            model.addAttribute("descFileList", Collections.emptyList());
            model.addAttribute("itemFileList", Collections.emptyList());
            model.addAttribute("formImgList", Collections.emptyList());
            model.addAttribute("mgrList", Collections.emptyList());
        }
        return "module/apply/form-apply-extract";
    }

    private String findWriteCntn(List<FormApplWriteCntnVO> writeCntnList, String srcTypeCd) {
        for (FormApplWriteCntnVO writeCntn : writeCntnList) {
            if (srcTypeCd.equals(writeCntn.getSrcTypeCd())) {
                return writeCntn.getWriteCntn();
            }
        }
        return null;
    }

    private List<FormApplFileVO> filterFileList(List<FormApplFileVO> fileList, String srcTypeCd) {
        List<FormApplFileVO> result = new ArrayList<>();
        for (FormApplFileVO file : fileList) {
            String fileSrcTypeCd = file.getSrcTypeCd();
            boolean matches = "ITEM".equals(srcTypeCd)
                    ? (fileSrcTypeCd == null || fileSrcTypeCd.isEmpty() || "ITEM".equals(fileSrcTypeCd))
                    : srcTypeCd.equals(fileSrcTypeCd);
            if (matches) {
                result.add(file);
            }
        }
        return result;
    }

    /**
     * 이미지뷰
     */
    @ApiResponse(responseCode = "200", description = "신청 이미지. 실제 형식에 따라 Content-Type이 달라집니다.",
            content = @Content(mediaType = "application/octet-stream", schema = @Schema(type = "string", format = "binary")))
    @Operation(summary = "신청 이미지 보기")
    @GetMapping("/img/{imgId}/view")
    public ResponseEntity<FileSystemResource> viewFormApplImg(@PathVariable long imgId) throws IOException {
        FormApplImgVO img = formApplyExtractService.getFormApplImg(imgId);
        if (img == null) {
            return ResponseEntity.notFound().build();
        }

        Path filePath = Paths.get(img.getFilePath());
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        String contentType = Files.probeContentType(filePath);
        return ResponseEntity.ok()
                .contentType(contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(filePath));
    }

    /**
     * 첨부파일 다운로드
     */
    @ApiResponse(responseCode = "200", description = "신청 첨부파일",
            content = @Content(mediaType = "application/octet-stream", schema = @Schema(type = "string", format = "binary")))
    @Operation(summary = "신청 첨부파일 다운로드")
    @GetMapping("/file/{fileId}/download")
    public ResponseEntity<FileSystemResource> downloadFormApplFiles(@PathVariable long fileId) throws IOException {
        FormApplFileVO file = formApplyExtractService.getFormApplFile(fileId);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }

        Path filePath = Paths.get(file.getFilePath());
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        String encodedFileNm = URLEncoder.encode(file.getFileNm(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileNm)
                .body(new FileSystemResource(filePath));
    }

    /**
     * 첨부파일 삭제
     */
    @Operation(summary = "신청 첨부파일 삭제")
    @DeleteMapping("/file/{fileId}")
    public ResponseEntity<Void> deleteFormApplFiles(@PathVariable long fileId, @RequestParam long applId,
        HttpSession sessions) {
        SsoLoginUserVO sso = (SsoLoginUserVO) sessions.getAttribute("ssoLoginUser");
        if (sso == null) {
            return ResponseEntity.status(401).build();
        }

        formApplyExtractService.deleteFormApplFile(fileId, applId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "신청 이미지 삭제")
    @DeleteMapping("/img/{imgId}")
    public ResponseEntity<Void> deleteFormApplImg(@PathVariable long imgId, @RequestParam long applId,
                                                    HttpSession sessions) {
        SsoLoginUserVO sso = (SsoLoginUserVO) sessions.getAttribute("ssoLoginUser");
        if (sso == null) {
            return ResponseEntity.status(401).build();
        }

        formApplyExtractService.deleteFormApplImg(imgId, applId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "신청 이미지 목록 삭제")
    @DeleteMapping("/img")
    public ResponseEntity<FormApplImgDeleteResultVO> deleteFormApplImgLists(
            @RequestParam("imgId") List<Long> imgIdList, @RequestParam long applId, HttpSession session) {
        SsoLoginUserVO sso = (SsoLoginUserVO) session.getAttribute("ssoLoginUser");
        if (sso == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(formApplyExtractService.deleteFormApplImgList(imgIdList, applId));
    }

    /**
     * 서식목록에서 선택된 이미지에 연결된 AI 제안 판독 항목만 삭제
     */
    @Operation(summary = "신청 이미지의 추출 필드 삭제")
    @DeleteMapping("/img/{imgId}/fld")
    public ResponseEntity<AiSuggestPreviewResultVO> deleteFormApplFldByImg(@PathVariable long imgId, @RequestParam long applId, HttpSession session) {
        SsoLoginUserVO sso = (SsoLoginUserVO) session.getAttribute("ssoLoginUser");
        if (sso == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(formApplyExtractService.deleteFormApplFldByImg(applId, imgId));
    }

    /**
     * 결재선 지정 팝업
     */
    @Operation(summary = "결재 담당자 검색")
    @GetMapping("/staff/search")
    public ResponseEntity<List<StaffVO>> searchStaffByNm(@RequestParam String keyword) {
        return ResponseEntity.ok(formApplyExtractService.searchStaffByName(keyword));
    }

    /**
     * 신청 상태(APPL)인 건만 취소(CANC)로 변경한다.
     */
    @Operation(summary = "서식 신청 취소")
    @PostMapping("/{applId}/cancel")
    public ResponseEntity<List<Void>> cancelFormAppl(@PathVariable long applId, HttpSession session) {
        SsoLoginUserVO sso = (SsoLoginUserVO) session.getAttribute("ssoLoginUser");
        if (sso == null) {
            return ResponseEntity.status(401).build();
        }

        boolean cancelled = formApplyExtractService.cancelFormAppl(applId, sso.getUserId());
        if (!cancelled) {
            return ResponseEntity.status(409).build();
        }
        return ResponseEntity.ok().build();
    }

    /**
     * 추출설명 규칙 등록 모달 저장
     */
    @Operation(summary = "추출 필드 규칙 저장")
    @PostMapping("/fld/{fldId}/rule")
    public ResponseEntity<Void> saveFormApplFldRule(@PathVariable long fldId, @RequestParam long applId, @RequestBody FormApplFldVO rule, HttpSession session) {
        SsoLoginUserVO sso = (SsoLoginUserVO) session.getAttribute("ssoLoginUser");
        if (sso == null) {
            return ResponseEntity.status(401).build();
        }
        rule.setFldId(fldId);
        if (!formApplyExtractService.saveFormApplFldRule(applId, rule, sso.getUserId())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }

    /**
     * 추출설명 메타 정보 모달 저장
     */
    @Operation(summary = "추출 필드 메타정보 저장")
    @PostMapping("/fld/{fldId}/meta")
    public ResponseEntity<Void> saveFormApplFldMeta(@PathVariable long fldId, @RequestParam long applId, @RequestBody FormApplFldVO meta, HttpSession session) {
        SsoLoginUserVO sso = (SsoLoginUserVO) session.getAttribute("ssoLoginUser");
        if (sso == null) {
            return ResponseEntity.status(401).build();
        }
        meta.setFldId(fldId);
        if (!formApplyExtractService.saveFormApplFldMeta(applId, meta, sso.getUserId())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }

}
