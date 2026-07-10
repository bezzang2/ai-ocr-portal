package com.agilesoda.test.api;

import com.agilesoda.common.response.BaseResponse;
import com.agilesoda.test.entity.LoginRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/test")
@Tag(name = "테스트")
public class TestApiController {

    @PostMapping("/signin")
    public ResponseEntity<?> signin(@RequestBody @Valid LoginRequest req) throws Exception{
        log.info("################# signin START");

        log.info("req => {}", req);

        return BaseResponse.ok("성공", req);
    }
}
