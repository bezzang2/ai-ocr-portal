package com.agilesoda.module.batch.service;

import com.agilesoda.common.route.DataSourceRoutingContext;
import com.agilesoda.module.batch.mapper.DeptBatchMapper;
import com.agilesoda.module.batch.vo.DeptInqVO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeptBatchService {

    private final DeptBatchMapper deptBatchMapper;

    public void readBranchFile() throws Exception{
        String filePath = "/Data/aiocr/batch/branch/BRLIST_20261012.dat";
        //String filePath = "c:/batch/branch/BRLIST_20261012.dat";
        Path path = Paths.get(filePath);

        // 파일 존재 확인
        if(!Files.exists(path)){
            log.warn("[부서배치] 파일 없음 path={}", filePath);
            return;
        }


        // 파일 읽기
        try(BufferedReader br = Files.newBufferedReader(path, Charset.forName("MS949"))){
            String line;
            int count = 0;
            int successCount = 0;
            int failCount = 0;
            int skipCount = 0;

            while((line = br.readLine()) != null){
                count++;
                byte[] bytes = line.getBytes(Charset.forName("MS949"));
                log.debug("[부서배치] 처리 순번 line={} length={}",count,bytes.length);

                if(line.isBlank()){
                    continue;
                }

                // 최소 필수 길이 확인
                if(bytes.length < 1340){
                    skipCount++;
                    log.warn("[부서배치] 데이터 길이 부족 line={} length={}",count,bytes.length);
                    continue;
                }
                try {
                    DeptInqVO vo = new DeptInqVO();
                    vo.setBrCd(getField(bytes,0,4)); // 부서코드
                    vo.setBrNm(getField(bytes,55,100)); // 부서명
                    // 부서유형 변환
                    //String sTemp1 = line.substring(730,732).trim();
                    //String sTemp2 = line.substring(725,726).trim();
                    //vo.setBrTpCd(getBrnDvCd(vo.getBrCd(),sTemp1,sTemp2));
                    //brTpCd; //부서유형코드
                    vo.setPosiMobrCd(getField(bytes,726,4)); // 관련 부서코드
                    vo.setSidoDscd(getField(bytes,732,2)); // 시도코드
                    vo.setTelNo(getField(bytes,641,20)); // 전화번호
                    vo.setFaxNo(getField(bytes,661,20)); // 펙스번호
                    vo.setBrAd(getField(bytes,255,200)); // 부서주소
                    vo.setBrOpenDt(getField(bytes,15,8)); // 개설일
                    vo.setBrShutDt(getField(bytes,23,8)); // 페쇄일
                    vo.setItgbrCd(getField(bytes,883,4)); // 통합부서코드
                    if ("0000".equals(vo.getItgbrCd())) {
                        vo.setItgbrCd(vo.getBrCd());
                    }
                    if (vo.getBrShutDt() == null || vo.getBrShutDt().trim().isEmpty()) {
                        vo.setItgbrCd(vo.getBrCd());
                    }
                    vo.setDelYn("N"); // 삭제여부
                    vo.setFstRegSdH(getField(bytes,1310,8)); // 최초등록 관련일자
                    vo.setFstRegPeNo(getField(bytes,1318,7)); // 최초등록 관련코드
                    vo.setLstChgDh(getField(bytes,1325,8)); // 최종변경 관련일자
                    vo.setLstChgPeNo(getField(bytes,1333,7)); // 최종변경 관련코드

                    int result = deptBatchMapper.mergeDept(vo);
                    successCount++;

                    // 데이터 내용 대신 길이만 출력
                    log.debug("[부서배치] 부서 저장 brCd={} mergeResult={}", vo.getBrCd(), result);
                }catch (Exception e){
                    failCount++;
                    log.error("[부서배치] 데이터 처리 오류 line={}", count, e);
                }
            }
            log.info("[부서배치] 완료 total={} success={} skip={} fail={}",count,successCount,skipCount,failCount);
        }
    }

    private String getField(byte[] bytes, int start, int length){
        return new String(bytes, start, length,Charset.forName("MS949")).trim();
    }
}
