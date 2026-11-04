package com.agilesoda.module.apply.controller;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;


/****************************************************
 *   결재처리 화면 호출 서비스를 관리하는 Controller
 *****************************************************/
@Slf4j
@Controller
@RequestMapping("/module/apply")
@RequiredArgsConstructor
public class ApprovalController {


    /****************************************************
     *   결재처리 화면 연결
     *****************************************************/
    @GetMapping("/approval")
    public String approval(@RequestParam(value = "menuId", defaultValue = "") String menuId,
                         Model model) throws Exception {
        log.debug("[approval] 시작");

        return "module/apply/approval";

    }

}

