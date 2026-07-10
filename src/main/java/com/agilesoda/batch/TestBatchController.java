package com.agilesoda.batch;

import com.agilesoda.common.util.DateUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@Tag(name = "배치")
public class TestBatchController {
    /**
     * cron 표현식
     * 초 분 시 일 월 요일
     * ex) * 0/10 * * * *
     */
    @Scheduled(cron = "0 0/10 * * * *")
    public void testBatch(){
        log.info("################# runTestBatch START ###########");

        log.info("batch run time => {}", DateUtil.getCurrentDateTime());
    }
}
