package com.agilesoda.module.dash.controller;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;


/****************************************************
 *   대시보드 화면 호출 서비스를 관리하는 Controller
 *****************************************************/
@Slf4j
@Controller
@RequestMapping("/module/dash")
@RequiredArgsConstructor
public class DashBoardController {


    /****************************************************
     *   대시보드 화면 연결
     *****************************************************/
    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(value = "menuId", defaultValue = "") String menuId,
                         Model model) throws Exception {
        log.debug("[dashboard] 시작");

        return "module/dash/dashboard";

    }

}

