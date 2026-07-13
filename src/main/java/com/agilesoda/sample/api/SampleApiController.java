package com.agilesoda.sample.api;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.agilesoda.common.response.BaseResponse;
import com.agilesoda.sample.SampleService;
import com.agilesoda.sample.entity.LoginRequest;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/test")
@Tag(name = "테스트")
public class SampleApiController {
	
	private final SampleService sampleService;
	
    @PostMapping("/signin")
    public ResponseEntity<?> signin(@RequestBody @Valid LoginRequest req) throws Exception{
        log.info("################# signin START");

        log.info("req => {}", req);
        
        Map<String, Object> resData = sampleService.selectUser(req.getUserId());

        return BaseResponse.ok("성공", resData);
    }
}
