package com.agilesoda.batch;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/batch")
@RequiredArgsConstructor
@Tag(name = "배치", description = "배치 수동 실행")
public class BatchController {
    private final BatchService batchService;

    @Operation(summary = "배치 수동 실행")
    @GetMapping("/run")
    public void batchRun() throws Exception{
        log.debug("[batchRun] 시작");
        batchService.selectUserList();
        log.debug("[batchRun] 종료");
    }
}
