package com.agilesoda.module.apply.controller;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.agilesoda.module.apply.common.FormApplyExcel;
import com.agilesoda.module.apply.vo.FormApplVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.agilesoda.module.apply.service.FormApplyService;
import com.agilesoda.module.apply.vo.FormApplSearchVO;
import com.agilesoda.module.apply.vo.PageInfoVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Controller
@RequestMapping("/module/apply")
@RequiredArgsConstructor
@Tag(name = "서식 신청 목록", description = "서식 신청 내역 파일 다운로드")
public class FormApplyController {

	private final FormApplyService formApplyService;

	/**
	 * OCR서식신청 메뉴 링크
	 */
	@GetMapping("/menuLink")
	public String menuLink(@ModelAttribute FormApplSearchVO searchVO, Model model, HttpSession session, @RequestParam(value="menuId", defaultValue="") String menuId) throws Exception {
		return doList(searchVO, model, session, menuId);
	}

	/**
	 * OCR서식신청 조회
	 */
	@PostMapping("/list")
	public String search(@ModelAttribute FormApplSearchVO searchVO, Model model, HttpSession session, @RequestParam(value="menuId", defaultValue="") String menuId) throws Exception {
		doList(searchVO, model, session, menuId);
		return "module/apply/form-apply :: resultFragment";
	}

	private String doList(@ModelAttribute FormApplSearchVO searchVO, Model model, HttpSession session, @RequestParam(value="menuId", defaultValue="") String menuId) throws Exception {

		//내 글만 보기
		if (searchVO.isOnlyMine()) {
			//***세션값 보완
			String sessionUser = "ADMIN";
			if (sessionUser != null) {
				searchVO.setLoginUserId(sessionUser);
			}
		}

		int totalCount = formApplyService.getFormApplCount(searchVO);

		log.debug("getApplicantNm:" + searchVO.getApplicantNm());

		model.addAttribute("procStatList", formApplyService.getProcStatList());
		model.addAttribute("formList", formApplyService.getFormApplList(searchVO));
		model.addAttribute("totalCount", totalCount);
		model.addAttribute("pageInfo", new PageInfoVO(searchVO.getPageNo(), searchVO.getPageSize(), totalCount));
		model.addAttribute("searchVO", searchVO);
		return "module/apply/form-apply";
	}

	/**
	 * 엑셀다운. 메뉴명+yyyymmddhhmm
	 */
	@ApiResponse(responseCode = "200", description = "서식 신청 목록 엑셀 파일",
	        content = @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", schema = @Schema(type = "string", format = "binary")))
	@Operation(summary = "서식 신청 목록 엑셀 다운로드")
	@GetMapping("/excel")
	public ResponseEntity<byte[]> downloadFormApplListExcel(@ModelAttribute FormApplSearchVO searchVO, HttpSession session) throws IOException {
		//내 글만 보기
		if (searchVO.isOnlyMine()) {
			//***세션값 보완
			String sessionUser = "ADMIN";
			if (sessionUser != null) {
				searchVO.setLoginUserId(sessionUser);
			}
		}

		List<FormApplVO> formList = formApplyService.getFormApplListAll(searchVO);
		byte[] excelBytes = FormApplyExcel.write(formList);
		String encodedFileNm = URLEncoder.encode(FormApplyExcel.buildFileName(), StandardCharsets.UTF_8).replace("+", "%20");

		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileNm)
				.body(excelBytes);
	}
}
