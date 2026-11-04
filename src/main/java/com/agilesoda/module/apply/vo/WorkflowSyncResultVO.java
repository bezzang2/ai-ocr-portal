package com.agilesoda.module.apply.vo;

import lombok.Data;

/** 워크플로 동기화 1회 결과. 즉시 동기화 버튼의 응답과 완료 로그에 쓴다. */
@Data
public class WorkflowSyncResultVO {
    /** 엔진에서 가져온 업무 수 */
    private int fetched;
    /** DB에 반영한 업무 수 */
    private int stored;
    /** 저장하다 실패해 건너뛴 업무 수 */
    private int failed;
    /** 상세(분류 클래스)를 못 가져와 기존 연결을 유지한 업무 수 */
    private int detailFailed;
    /** 릴리스가 그대로여서 상세 조회를 건너뛴 업무 수 */
    private int detailSkipped;
    /** 연결이 달라져 다시 만든 업무 수 */
    private int linksChanged;
    /** 분류 클래스 수('기타' 제외) */
    private int classes;
    /** 서식과 연결된 클래스 수 */
    private int linked;
    /** 맞는 서식이 없는 클래스 수 */
    private int unlinked;
    /** 분류 클래스 저장(OCUOC_OCRWC_L)에 실패한 업무 수. 마이그레이션 미적용이면 전체 업무 수와 같다 */
    private int classStoreFailed;
    private long elapsedMs;
}
