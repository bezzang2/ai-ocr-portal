package com.agilesoda.module.apply.controller;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.simple.JSONObject;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.agilesoda.common.ComCode;
import com.agilesoda.common.ComCodeManager;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;



/****************************************************
 *   OCR업무신청 화면 호출 서비스를 관리하는 Controller
 *****************************************************/
@Slf4j
@Controller
@RequestMapping("/module/apply")
@RequiredArgsConstructor
public class OcrTaskApplyController {
	
    /****************************************************
     *   OCR업무조회 화면 연결
     *****************************************************/
	@GetMapping("/ocrTaskSearch")
    public String ocrTaskSearch(@RequestParam(name = "status", defaultValue = "") String status,
                                @RequestParam(name = "pageNo", defaultValue = "1") int pageNo,
                                @RequestParam(name = "pageSize", defaultValue = "15") int pageSize,
                                Model model) throws Exception {
        log.info("######################## ocrTaskSearch START ####################");

        //  업무분류 공통 코드 추가
        List<ComCode> bzwkCdCodes = ComCodeManager.getInstance().getCodes("BZWK_CD");

        //  진행상태 공통 코드 추가
        List<ComCode> prgsStCdCodes = ComCodeManager.getInstance().getCodes("PRGS_ST_CD");

        model.addAttribute("bzwkCds", bzwkCdCodes);
        model.addAttribute("prgsStCds", prgsStCdCodes);

        log.info("######################## ocrTaskSearch END ####################");
        return "module/apply/ocr-task-search";
    }
    
    /**
     * OCR업무신청 화면을 연다.
     * 서식·업무 목록은 화면이 열린 뒤 /api/v1/wkflow/list 로 가져온다. 여기서 Agent를 부르지 않아
     * Agent가 꺼져 있어도 화면은 열리고, 목록 실패 안내는 그 API 응답으로 보여 준다.
     *
     * @param model 업무분류 공통 코드(data.bzwkCds)를 담는다
     * @return 업무신청 화면 템플릿 이름
     * @throws Exception 공통 코드 조회 실패
     */
    @GetMapping("/ocrTaskApply")
    public String ocrTaskApply(HttpServletRequest request, HttpServletResponse response, Model model) throws Exception {
        log.debug("[업무신청] 화면 요청");
        Map<String, Object> resData = new HashMap<>();
        resData.put("bzwkCds", ComCodeManager.getInstance().getCodes("BZWK_CD"));
        model.addAttribute("data", new JSONObject(resData));
        return "module/apply/ocr-task-apply";
    }

    /****************************************************
     *   OCR업무사용신청 화면 연결
     *****************************************************/
    @GetMapping("/ocrTaskUsageApply")
    public String ocrTaskUsageApply(@RequestParam(value = "menuId", defaultValue = "") String menuId,
                                    Model model) throws Exception {
        log.debug("######################## ocrTaskUsageApply START ####################");

        return "module/apply/ocr-task-usage-apply";

    }
}

