package com.agilesoda.module.ocr.controller;

import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import com.agilesoda.module.ocr.vo.OcrReviewTransactionVO;

/**
 * 판독결과 검토 목록의 조회 조건. 상세에 다녀와도 조건을 유지하도록 쿼리 문자열로 주고받는다.
 * 쿼리 문자열은 사용자가 바꿀 수 있으므로 아는 키와 값만 다시 만들어 쓴다.
 *
 * @param wkId         업무 ID. 비면 null
 * @param reviewStatus 검토상태 코드. 모르는 값이면 null(전체)
 * @param mine         내 담당만
 * @param from         스캔 시작일. 기본은 최근 7일(CrossVerifyController.period)
 * @param to           스캔 종료일
 * @param view         목록 보기 단위: trans 판독작업별(기본), wk 업무별
 * @param page         목록 복귀 페이지. 1부터 시작
 * @param pageSize     페이지당 판독작업 수. 15(기본)·30·50
 */
record ReviewListQuery(String wkId, String reviewStatus, boolean mine, LocalDate from, LocalDate to, String view, int page, int pageSize) {
    static final String VIEW_TRANSACTION = "trans";
    static final String VIEW_WORKFLOW = "wk";

    /** 페이지는 양수, 페이지 크기는 공통 선택값(15·30·50)만 허용한다. */
    static ReviewListQuery of(String wkId, String reviewStatus, boolean mine, String from, String to, String view,
                              String page, String pageSize) {
        LocalDate[] period = CrossVerifyController.period(from, to);
        return new ReviewListQuery(wkId == null || wkId.isBlank() ? null : wkId.trim(),
                OcrReviewTransactionVO.STATUS_LABELS.containsKey(reviewStatus) ? reviewStatus : null,
                mine, period[0], period[1], VIEW_WORKFLOW.equals(view) ? VIEW_WORKFLOW : VIEW_TRANSACTION, positivePage(page), allowedPageSize(pageSize));
    }

    /** 상세가 받은 list 파라미터(toQueryString 결과)를 읽는다. */
    static ReviewListQuery parse(String queryString) {
        MultiValueMap<String, String> params = UriComponentsBuilder.newInstance().query(queryString).build()
                .getQueryParams();
        return of(decoded(params.getFirst("wkId")), decoded(params.getFirst("reviewStatus")),
                "true".equals(params.getFirst("mine")), decoded(params.getFirst("from")), decoded(params.getFirst("to")),
                decoded(params.getFirst("view")), params.getFirst("page"), params.getFirst("pageSize"));
    }

    /** 값을 인코딩한 쿼리 문자열. 목록 복귀 주소 뒤에 그대로 붙인다. 예: from=2026-10-26&to=2026-11-01&view=trans */
    String toQueryString() {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
        if (wkId != null) builder.queryParam("wkId", wkId);
        if (reviewStatus != null) builder.queryParam("reviewStatus", reviewStatus);
        if (mine) builder.queryParam("mine", true);
        builder.queryParam("from", from).queryParam("to", to).queryParam("view", view);
        builder.queryParam("page", page).queryParam("pageSize", pageSize);
        return builder.encode(StandardCharsets.UTF_8).build().getQuery();
    }

    private static int positivePage(String value) {
        try { return Math.max(1, Integer.parseInt(value)); }
        catch (NumberFormatException e) { return 1; }
    }

    private static int allowedPageSize(String value) {
        return "30".equals(value) ? 30 : "50".equals(value) ? 50 : 15;
    }

    private static String decoded(String value) {
        return value == null ? null : java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
