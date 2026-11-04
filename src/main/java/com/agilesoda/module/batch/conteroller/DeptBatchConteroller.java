package com.agilesoda.module.batch.conteroller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.agilesoda.module.batch.service.DeptBatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/DeptBatch")
@Tag(name = "배치", description = "배치 수동 실행")
public class DeptBatchConteroller {

    private final DeptBatchService deptBatchService;

    public DeptBatchConteroller(DeptBatchService deptBatchService){
        this.deptBatchService = deptBatchService;
    }

    @Scheduled(cron = "0 0 1 * * *",zone = "Asia/Seoul")
    public void batch(){
        log.info("부서정보 배치 실행");
        try {
            deptBatchService.readBranchFile();
            log.info("부서정보 배치 정상 종료");
        } catch (Exception e){
            log.error("부서정보 배치 오류",e);
        }
    }

    @Operation(summary = "부서정보 배치 수동 실행")
    @GetMapping("/DeptBatchTest")
    public String deptBatch() throws Exception{
        deptBatchService.readBranchFile();
        return "OK";
    }
}
