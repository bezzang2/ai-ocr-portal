package com.agilesoda.module.ocr.controller;

import com.agilesoda.module.apply.vo.FormApplSearchVO;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;

@Controller
public class OcrReqController {
//    private final OcrReqService ocrReqService;
//    public OcrReqService getOcrReqService(OcrReqService ocrReqService) {
//        this.ocrReqService = ocrReqService;
//    }

    public String list(@ModelAttribute FormApplSearchVO searchVO, Model model, HttpSession sessions) {

        return "module/ocr/ocr-req";
    }
}
