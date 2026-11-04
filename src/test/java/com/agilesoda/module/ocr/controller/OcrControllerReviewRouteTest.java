package com.agilesoda.module.ocr.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agilesoda.common.menu.ReviewMenuNavigation;
import com.agilesoda.module.common.entity.CommonMenuEntity;
import com.agilesoda.module.ocr.service.OcrReqService;
import com.agilesoda.module.ocr.service.OcrReviewService;
import com.agilesoda.module.ocr.vo.OcrReviewTransactionVO;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ExtendedModelMap;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/** 판독결과 검토 주소 분기: transId·scrn_no 가 없으면 목록, 있으면 상세다. */
class OcrControllerReviewRouteTest {
    private OcrReqService req;
    private OcrReviewService review;
    private OcrController controller;
    private ExtendedModelMap model;

    private static OcrReviewTransactionVO row(String transId) {
        OcrReviewTransactionVO vo = new OcrReviewTransactionVO();
        vo.setTransId(transId);
        return vo;
    }

    @BeforeEach
    void setUp() {
        req = mock(OcrReqService.class);
        review = mock(OcrReviewService.class);
        controller = new OcrController(req, new ReviewMenuNavigation(), null, review);
        model = new ExtendedModelMap();
    }

    private String open(String wkId, String transId, String scrnNo, String list) {
        return controller.list(wkId, transId, scrnNo, "MENU0301", null, false, "2026-10-26", "2026-11-01", null, null, null, list,
                model, new MockHttpSession());
    }

    @Test
    void listSelectsEntityMenuDespiteDifferentRequestedId() {
        MockHttpSession session = reviewMenuSession();
        assertThat(controller.list(null, null, null, "M430", null, false,
                "2026-10-26", "2026-11-01", null, null, null, null, model, session))
                .isEqualTo("module/ocr/ocr-result-list");
        assertThat(model).containsEntry("menuId", "R777").containsEntry("parentMenuNm", "통합검색");
    }

    @Test
    void directDetailSelectsEntityMenuWithoutRequestedId() {
        assertThat(controller.list("wk", "fake-trans", null, "", null, false,
                "2026-10-26", "2026-11-01", null, null, null, null, model, reviewMenuSession()))
                .isEqualTo("module/ocr/ocr-result");
        assertThat(model).containsEntry("menuId", "R777").containsEntry("menuNm", "판독결과 검토");
    }

    private MockHttpSession reviewMenuSession() {
        CommonMenuEntity menu = new CommonMenuEntity();
        menu.setMenuId("R777");
        menu.setMenuNm("판독결과 검토");
        menu.setUprnMenuNm("통합검색");
        menu.setMenuLevel(2);
        menu.setMenuUrl("module/ocr/result");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("menuList", List.of(menu));
        return session;
    }

    @Test
    void noTransactionOpensList() {
        when(review.reviewTransactions(any(), isNull(), isNull(), isNull(), eq("20261026"), eq("20261101")))
                .thenReturn(List.of(row("t1")));

        assertThat(open(null, null, null, null)).isEqualTo("module/ocr/ocr-result-list");
        assertThat((List<?>) model.get("reviewTransactions")).hasSize(1);
        assertThat(model.get("listQuery")).isEqualTo("from=2026-10-26&to=2026-11-01&view=trans&page=1&pageSize=15");
        verify(req, never()).getResultWkGroupList(any(), any());
    }

    @Test
    void workflowOnlyStillOpensList() {
        assertThat(open("wk", null, null, null)).isEqualTo("module/ocr/ocr-result-list");
    }

    @Test
    void scanWebviewKeepsDetailWithoutSummary() {
        assertThat(open("wk", null, "VMM072000SC00", null)).isEqualTo("module/ocr/ocr-result");
        verify(req).getResultWkGroupList("wk", null);
        verify(review, never()).reviewTransactions(any(), any(), any(), any(), any(), any());
    }

    @Test
    void transactionOpensDetailWithNeighbours() {
        when(review.reviewTransactions(isNull(), eq("t2"), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(row("t2")));
        when(review.reviewTransactions(isNull(), isNull(), isNull(), isNull(), eq("20261026"), eq("20261101")))
                .thenReturn(List.of(row("t1"), row("t2"), row("t3")));

        assertThat(open("wk", "t2", null, "from=2026-10-26&to=2026-11-01&view=wk")).isEqualTo("module/ocr/ocr-result");

        assertThat(((OcrReviewTransactionVO) model.get("reviewTransaction")).getTransId()).isEqualTo("t2");
        assertThat(((OcrReviewTransactionVO) model.get("prevTransaction")).getTransId()).isEqualTo("t1");
        assertThat(((OcrReviewTransactionVO) model.get("nextTransaction")).getTransId()).isEqualTo("t3");
        assertThat(model.get("listQuery")).isEqualTo("from=2026-10-26&to=2026-11-01&view=wk&page=1&pageSize=15");
    }

    @Test
    void summaryFailureStillOpensDetail() {
        when(review.reviewTransactions(any(), eq("t2"), any(), any(), any(), any())).thenThrow(new IllegalStateException("Agent down"));

        assertThat(open("wk", "t2", null, null)).isEqualTo("module/ocr/ocr-result");
        assertThat(model.get("reviewTransaction")).isNull();
    }

    @Test
    void listFailureOpensListWithGuidance() {
        when(req.getWorkflowList()).thenThrow(new IllegalStateException("DB down"));

        assertThat(open(null, null, null, null)).isEqualTo("module/ocr/ocr-result-list");
        assertThat(model.get("loadError")).isEqualTo("판독 결과를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }

    @Test
    void listQueryIgnoresUnknownValuesAndKeepsEncodedValues() {
        ReviewListQuery query = ReviewListQuery.parse("wkId=a%26b&reviewStatus=HACK&mine=true&from=bad&to=2026-11-01&view=x&evil=1");

        assertThat(query.wkId()).isEqualTo("a&b");
        assertThat(query.reviewStatus()).isNull();
        assertThat(query.mine()).isTrue();
        assertThat(query.view()).isEqualTo("trans");
        assertThat(query.toQueryString()).startsWith("wkId=a%26b&mine=true&from=").doesNotContain("evil");
        assertThat(ReviewListQuery.parse(query.toQueryString())).isEqualTo(query);
    }

    @Test
    void listQueryPreservesPaginationAndNormalizesInvalidValues() {
        ReviewListQuery query = ReviewListQuery.parse("page=3&pageSize=30");
        assertThat(query.page()).isEqualTo(3);
        assertThat(query.pageSize()).isEqualTo(30);
        assertThat(ReviewListQuery.parse(query.toQueryString())).isEqualTo(query);
        ReviewListQuery invalid = ReviewListQuery.parse("page=999999999999&pageSize=999");
        assertThat(invalid.page()).isEqualTo(1);
        assertThat(invalid.pageSize()).isEqualTo(15);
    }

    /** 사용하지 않는 신청조회 모델이 pageSize를 숫자로 먼저 바인딩하면 기본값 보정 전에 400이 된다. */
    @Test
    void listRequestNormalizesInvalidPaginationBeforeRendering() throws Exception {
        when(review.reviewTransactions(any(), isNull(), isNull(), isNull(), any(), any())).thenReturn(List.of());
        var result = MockMvcBuilders.standaloneSetup(controller).build()
                .perform(get("/module/ocr/result").param("page", "잘못된값").param("pageSize", "잘못된값"))
                .andExpect(status().isOk()).andReturn();
        assertThat(result.getModelAndView().getModel()).containsEntry("page", 1).containsEntry("pageSize", 15);
    }

    /** 목록 템플릿이 모델 값으로 렌더링되고 행 링크에 목록 복귀 조건이 인코딩되어 붙는지 확인한다. 공통 레이아웃은 씌우지 않는다. */
    @Test
    void listTemplateRendersModel() throws Exception {
        OcrReviewTransactionVO vo = row("t1");
        vo.setWkId("wk");
        vo.setWkNm("가짜업무");
        vo.setScanDt("20261031181300");
        vo.setReviewStatus("UNASSIGNED");
        vo.setFormCount(5);
        vo.setVerifiedCount(2);
        when(review.reviewTransactions(any(), isNull(), isNull(), isNull(), any(), any())).thenReturn(List.of(vo));

        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        ThymeleafViewResolver views = new ThymeleafViewResolver();
        views.setTemplateEngine(engine);
        views.setCharacterEncoding("UTF-8");
        String html = MockMvcBuilders.standaloneSetup(controller).setViewResolvers(views).build()
                .perform(get("/module/ocr/result").param("from", "2026-10-26").param("to", "2026-11-01"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("가짜업무", "2 / 5", "미지정", "2026-10-31 18:13");
        assertThat(html).contains("transId=t1").contains("list=from%3D2026-10-26%26to%3D2026-11-01%26view%3Dtrans");
    }
}
