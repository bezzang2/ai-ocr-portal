package com.agilesoda.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agilesoda.common.response.BaseResponse;
import com.agilesoda.link.TestLinkService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/biz")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "업무")
public class TestController {
	private final TestLinkService tls;
	
    @GetMapping("/list")
    public ResponseEntity<?> selectList() throws Exception{
        log.info("################# selectList START");

        
        return BaseResponse.ok("성공", "123");
    }
    
    @GetMapping("/call")
    public ResponseEntity<?> call() throws Exception{
        log.info("################# call START");

        String res = tls.callLink();
        
        return BaseResponse.ok("성공", res);
    }
}
