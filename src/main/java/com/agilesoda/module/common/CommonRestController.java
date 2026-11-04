package com.agilesoda.module.common;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.agilesoda.common.response.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/common")
@RequiredArgsConstructor
@Tag(name = "공통", description = "직원 검색")
public class CommonRestController {

    private final CommonService commonService;

    @Operation(summary = "직원 검색 API",
            description = "직원을 검색한다.")
    @GetMapping("/emp-list")
    public ResponseEntity<?> getListEmpInfo(@RequestParam(name = "gubun", defaultValue = "1") int gubun,
    										@RequestParam(name = "keyword", defaultValue = "") String keyword,
    										@RequestParam(name = "limit", required = false) Integer limit) throws Exception{
        log.info("[getListEmpInfo] 시작");
        log.debug("[직원검색] 요청 keywordLen={}", keyword == null ? 0 : keyword.length());
        List<Map<String, Object>> empList = commonService.getListEmpInfo(gubun, keyword, limit);

        log.info("[getListEmpInfo] 종료");
        return BaseResponse.ok("성공", empList);
    }
}
