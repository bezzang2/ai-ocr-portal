package com.agilesoda.module.batch.service;

import com.agilesoda.common.route.DataSourceRoutingContext;
import com.agilesoda.module.batch.mapper.UserBatchMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserBatchService {

    private final UserBatchMapper userBatchMapper;

    public void batch(){
        log.info("============= 인사정보 Service 시작 =============");
        try{
            // PHRMS DB로 변경
            DataSourceRoutingContext.setDataSourceKey("phrms");
            // 인사정보 조회
            List<Map<String, Object>> list = userBatchMapper.selectUserList();
            // 저장할 DB로 변경
            DataSourceRoutingContext.setDataSourceKey("bpr");

            log.info("인사정보 조회 건수 :{}",list.size());
            for(Map<String, Object> user : list){
                Object retmDt = user.get("retmDt");
                // 퇴직자이면 삭제
                if(retmDt != null && !retmDt.toString().isBlank()){
                    userBatchMapper.deleteUser(user);
                    continue;
                }
                // 재직자이면 저장
                int mergeCnt = userBatchMapper.mergeUser(user);
            }

        }finally {
            DataSourceRoutingContext.clearDataSourceKey();
        }
        log.info("============= 인사정보 Service 종료 =============");

    }
}
