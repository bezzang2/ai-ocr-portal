package com.agilesoda.module.batch.conteroller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.agilesoda.module.batch.service.UserBatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/userBatch")
@Tag(name = "배치", description = "배치 수동 실행")
public class UserBatchConteroller {

    private final UserBatchService userBatchService;

    public UserBatchConteroller(UserBatchService userBatchService){
        this.userBatchService = userBatchService;
    }
    @Scheduled(cron = "0 0 0 * * *",zone = "Asia/Seoul")
    public void batch(){
        log.info("=============================================");
        log.info("인사정보 배치 실행");
        try {
            userBatchService.batch();
        } finally {

        }

        log.info("=============================================");
    }
    @Operation(summary = "인사정보 배치 수동 실행")
    @GetMapping("/run")
    public void run(){
        userBatchService.batch();
    }
}
