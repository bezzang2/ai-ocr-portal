package com.agilesoda.common.menu;

import com.agilesoda.module.common.entity.CommonMenuEntity;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 로그인 세션과 같은 Entity 메뉴로 화면 선택과 권한 기반 대체 링크를 검증한다. */
class ReviewMenuNavigationTest {
    private final ReviewMenuNavigation navigation = new ReviewMenuNavigation();

    private CommonMenuEntity menu(String id, String name, int level, String parent, String url) {
        CommonMenuEntity menu = new CommonMenuEntity();
        menu.setMenuId(id);
        menu.setMenuNm(name);
        menu.setMenuLevel(level);
        menu.setUprnMenuId(parent);
        menu.setMenuUrl(url);
        return menu;
    }

    private ExtendedModelMap apply(List<?> menus, String route) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("menuList", menus);
        ExtendedModelMap model = new ExtendedModelMap();
        navigation.apply(model, session, route);
        return model;
    }

    @Test void selectsCanonicalEntityAndConfiguredParentName() {
        CommonMenuEntity review = menu("R999", "판독결과 검토", 2, "P", "/module/ocr/result");
        review.setUprnMenuNm("통합검색");
        ExtendedModelMap model = apply(List.of(review), ReviewMenuNavigation.REVIEW_URL);
        assertThat(model).containsEntry("menuId", "R999").containsEntry("parentMenuNm", "통합검색")
                .containsEntry("menuNm", "판독결과 검토");
    }

    @Test void findsMissingParentNameFromParentEntity() {
        ExtendedModelMap model = apply(List.of(menu("P", "조회 업무", 1, null, null),
                menu("R", "검토", 2, "P", ReviewMenuNavigation.REVIEW_URL)), ReviewMenuNavigation.REVIEW_URL);
        assertThat(model).containsEntry("parentMenuNm", "조회 업무");
    }

    @Test void searchPermissionProvidesReviewFallback() {
        CommonMenuEntity search = menu("S", "검색", 2, "P", "module/search/search");
        List<CommonMenuEntity> menus = List.of(menu("P", "통합검색", 1, null, null), search);
        assertThat(apply(menus, ReviewMenuNavigation.REVIEW_URL)).containsEntry("menuId", "MENU0301")
                .containsEntry("menuNm", "판독결과 검토");
        assertThat(navigation.fallbackSource(menus)).isSameAs(search);
        assertThat(navigation.showFallback(menus, "P")).isTrue();
        assertThat(navigation.showFallback(menus, "OTHER")).isFalse();
    }

    @Test void fallbackRequiresVisibleParent() {
        List<CommonMenuEntity> menus = List.of(menu("S", "검색", 2, "P", "module/search/search"));
        assertThat(navigation.fallbackSource(menus)).isNull();
        assertThat(apply(menus, ReviewMenuNavigation.REVIEW_URL)).containsEntry("menuId", "");
    }

    @Test void actualReviewMenuSuppressesFallback() {
        List<CommonMenuEntity> menus = List.of(menu("P", "통합검색", 1, null, null),
                menu("S", "검색", 2, "P", "module/search/search"),
                menu("R", "검토", 2, "P", ReviewMenuNavigation.REVIEW_URL));
        assertThat(navigation.fallbackSource(menus)).isNull();
        assertThat(apply(menus, ReviewMenuNavigation.REVIEW_URL)).containsEntry("menuId", "R");
    }

    @Test void crossVerifyUsesItsConfiguredId() {
        ExtendedModelMap model = apply(List.of(menu("C777", "서식간 검증 결과", 2, "P", "/module/ocr/crossVerify")),
                ReviewMenuNavigation.CROSS_VERIFY_URL);
        assertThat(model).containsEntry("menuId", "C777").containsEntry("menuNm", "서식간 검증 결과");
    }

    @Test void searchPermissionDoesNotGrantCrossVerifyMenu() {
        ExtendedModelMap model = apply(List.of(menu("P", "통합검색", 1, null, null),
                menu("S", "검색", 2, "P", "module/search/search")), ReviewMenuNavigation.CROSS_VERIFY_URL);
        assertThat(model).containsEntry("menuId", "").containsEntry("menuNm", "서식간 검증 결과");
    }

    @Test void absentOrUnexpectedSessionMenusLeaveSelectionEmpty() {
        assertThat(apply(List.of("unexpected"), ReviewMenuNavigation.REVIEW_URL)).containsEntry("menuId", "");
        ExtendedModelMap model = new ExtendedModelMap();
        navigation.apply(model, new MockHttpSession(), ReviewMenuNavigation.CROSS_VERIFY_URL);
        assertThat(model).containsEntry("menuId", "").containsEntry("parentMenuNm", "통합검색");
    }

    @Test void otherRoutesAndTopLevelEntriesDoNotMatchReview() {
        assertThat(apply(List.of(menu("R", "검토", 1, null, ReviewMenuNavigation.REVIEW_URL),
                menu("D", "대시보드", 2, "P", "module/dashboard")), ReviewMenuNavigation.REVIEW_URL))
                .containsEntry("menuId", "");
    }
}
