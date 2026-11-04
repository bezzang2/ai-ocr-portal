package com.agilesoda.module.ocr.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Data
public class OcrResultWkGroupVO {
    private String wkId;
    private String wkNm;
    private String docTypNm;
    /** 이 묶음의 판독작업(업무별 최근 스캔 몇 건 중 1건) 트랜잭션 ID, 처리 상태(OCUOC_OCRWF_L.RSLT_CD), 스캔일시(yyyyMMddHHmmss) */
    private String transId;
    private String rsltCd;
    private String scanDt;
    /** 판독작업 전체(업무 묶음) 검증 담당자(OCUOC_OCRVR_L DOC_ID '*') */
    private String transAsgnUserId;
    private String transAsgnUserNm;
    /** 판독 결과가 있는 서식 */
    private List<OcrResultLefrmVO> lefrmList = new ArrayList<>();
    /** 업무에 연결됐지만 이번 판독작업에 해당 문서가 없는 서식("결과 없음") */
    private List<OcrResultLefrmVO> emptyLefrmList = new ArrayList<>();
    /** 실패·취소. 실패 문서(서식에 맞으면 lefrmId 있음, 맞는 서식이 없으면 null)와 판독작업 실패 시 연결 서식(docId null) */
    private List<OcrResultLefrmVO> failedLefrmList = new ArrayList<>();

    private static final Set<String> PENDING_RSLT_CDS = Set.of("pending", "pendding", "running", "awaiting_hitl", "timeout");
    private static final Set<String> FAILED_RSLT_CDS = Set.of("failed", "canceled");

    /** 업무 정보에 판독작업 1건을 붙인 묶음. 서식 행은 비어 있다. */
    public OcrResultWkGroupVO forTransaction(String transId, String rsltCd, String scanDt) {
        OcrResultWkGroupVO group = new OcrResultWkGroupVO();
        group.setWkId(wkId);
        group.setWkNm(wkNm);
        group.setDocTypNm(docTypNm);
        group.setTransId(transId);
        group.setRsltCd(rsltCd);
        group.setScanDt(scanDt);
        return group;
    }

    /** 묶음 제목에 붙일 스캔일시(yyyy-MM-dd HH:mm). 형식이 다르면 그대로 */
    public String getScanDtLabel() {
        return scanDtLabel(scanDt);
    }

    /** 스캔일시 yyyyMMddHHmm… 를 yyyy-MM-dd HH:mm 으로 바꾼다. 형식이 다르면 그대로 */
    public static String scanDtLabel(String scanDt) {
        if (scanDt == null || scanDt.length() < 12 || !scanDt.chars().allMatch(Character::isDigit)) {
            return scanDt;
        }
        return scanDt.substring(0, 4) + "-" + scanDt.substring(4, 6) + "-" + scanDt.substring(6, 8)
                + " " + scanDt.substring(8, 10) + ":" + scanDt.substring(10, 12);
    }

    /** 결과를 아직 받지 못한 판독작업(폴링 제한 초과·검토 대기). 화면의 "결과 다시 가져오기" 표시 기준. */
    public boolean isPending() {
        return transId != null && PENDING_RSLT_CDS.contains(rsltCd);
    }

    /** 판독작업 전체가 실패·취소돼 결과 문서가 없다. 연결 서식은 모두 "실패" 묶음에 넣는다. */
    public boolean isTransFailed() {
        return transId != null && FAILED_RSLT_CDS.contains(rsltCd);
    }

    /** 서식목록에 표시할 내용(서식 행 또는 대기·실패 상태)이 있다. 판독한 적 없는 업무는 false. */
    public boolean isListed() {
        return transId != null && (getTotalCount() > 0 || isPending() || isTransFailed());
    }

    /** 워크플로 건수: 결과 + 결과 없음 + 실패 */
    public int getTotalCount() {
        return lefrmList.size() + emptyLefrmList.size() + failedLefrmList.size();
    }
}
