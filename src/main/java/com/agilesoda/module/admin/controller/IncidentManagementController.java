package com.agilesoda.module.admin.controller;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;


/****************************************************
 *   장애관리 화면 호출 서비스를 관리하는 Controller
 *****************************************************/
@Slf4j
@Controller
@RequestMapping("/module/admin")
@RequiredArgsConstructor
public class IncidentManagementController {


    /****************************************************
     *   장애관리 화면 연결
     *****************************************************/
    @GetMapping("/incidentManagement")
    public String incidentManagement(@RequestParam(value = "menuId", defaultValue = "") String menuId,
                         Model model) throws Exception {
        log.debug("[incidentManagement] 시작");

        return "module/admin/incidentManagement";

    }

}

