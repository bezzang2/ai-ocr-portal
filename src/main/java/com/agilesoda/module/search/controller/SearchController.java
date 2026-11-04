package com.agilesoda.module.search.controller;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;


/****************************************************
 *   고객 통합검색 화면 호출 서비스를 관리하는 Controller
 *****************************************************/
@Slf4j
@Controller
@RequestMapping("/module/search")
@RequiredArgsConstructor
public class SearchController {


    /****************************************************
     *   고객 통합검색 화면 연결
     *****************************************************/
    @GetMapping("/search")
    public String search(@RequestParam(value = "menuId", defaultValue = "") String menuId,
                         Model model) throws Exception {
        log.debug("[search] 시작");

        return "module/search/search";

    }

}

