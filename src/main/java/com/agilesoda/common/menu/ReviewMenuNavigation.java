package com.agilesoda.common.menu;

import com.agilesoda.module.common.entity.CommonMenuEntity;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.util.List;

/** 로그인 세션의 허용 메뉴로 검토·서식간 검증 화면의 업무 메뉴와 상단 경로를 확정한다. */
@Slf4j
@Component("reviewMenuNavigation")
public class ReviewMenuNavigation {
    public static final String FALLBACK_MENU_ID = "MENU0301";
    public static final String REVIEW_URL = "module/ocr/result";
    public static final String CROSS_VERIFY_URL = "module/ocr/crossVerify";

    /** 앞쪽 슬래시를 정리한 주소가 판독결과 검토 메뉴인지 판별한다. */
    public boolean isReviewUrl(String url) {
        return REVIEW_URL.equals(normalize(url));
    }

    /** 검토 메뉴가 아직 없고 검색 메뉴와 상위 메뉴가 허용된 경우에만 대체 링크의 근거 메뉴를 반환한다. */
    public CommonMenuEntity fallbackSource(Object sessionMenus) {
        if (!(sessionMenus instanceof List<?> menus)) return null;
        if (find(menus, REVIEW_URL) != null) return null;
        CommonMenuEntity search = find(menus, "module/search/search");
        return search != null && parent(menus, search.getUprnMenuId()) != null ? search : null;
    }

    /** 허용된 검색 메뉴의 상위 메뉴 아래에만 검토 대체 링크를 표시한다. */
    public boolean showFallback(Object sessionMenus, String parentId) {
        CommonMenuEntity source = fallbackSource(sessionMenus);
        return source != null && source.getUprnMenuId().equals(parentId);
    }

    /**
     * 현재 화면 주소로 메뉴를 확정해 모델에 적용한다. 요청의 menuId나 저장된 이전 화면 이름은 사용하지 않는다.
     * route는 REVIEW_URL 또는 CROSS_VERIFY_URL이다. 허용 메뉴가 없으면 선택 ID를 비워 두고 화면 기본 이름만 표시한다.
     */
    public void apply(Model model, HttpSession session, String route) {
        String currentRoute = normalize(route);
        boolean review = REVIEW_URL.equals(currentRoute);
        String name = review ? "판독결과 검토" : "서식간 검증 결과";
        String parentName = "통합검색";
        String menuId = "";
        Object sessionMenus = session.getAttribute("menuList");
        List<?> menus = sessionMenus instanceof List<?> list ? list : List.of();
        CommonMenuEntity selected = find(menus, currentRoute);
        if (selected != null) {
            menuId = selected.getMenuId() == null ? "" : selected.getMenuId();
            if (hasText(selected.getMenuNm())) name = selected.getMenuNm();
        } else if (review) {
            selected = fallbackSource(menus);
            if (selected != null) menuId = FALLBACK_MENU_ID;
        }
        if (selected != null) {
            if (hasText(selected.getUprnMenuNm())) parentName = selected.getUprnMenuNm();
            else {
                CommonMenuEntity parent = parent(menus, selected.getUprnMenuId());
                if (parent != null && hasText(parent.getMenuNm())) parentName = parent.getMenuNm();
            }
        }
        model.addAttribute("menuId", menuId);
        model.addAttribute("parentMenuNm", parentName);
        model.addAttribute("menuNm", name);
        if (menuId.isBlank()) log.warn("[업무 메뉴] 허용 메뉴 없음 route={}", currentRoute);
        else log.debug("[업무 메뉴] 현재 화면 확정 route={} menuId={}", currentRoute, menuId);
    }

    private CommonMenuEntity find(List<?> menus, String route) {
        for (Object entry : menus) {
            if (entry instanceof CommonMenuEntity menu && menu.getMenuLevel() == 2
                    && route.equals(normalize(menu.getMenuUrl()))) return menu;
        }
        return null;
    }

    private CommonMenuEntity parent(List<?> menus, String id) {
        if (!hasText(id)) return null;
        for (Object entry : menus) {
            if (entry instanceof CommonMenuEntity menu && menu.getMenuLevel() == 1
                    && id.equals(menu.getMenuId())) return menu;
        }
        return null;
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }

    private String normalize(String url) {
        return url == null ? "" : url.replaceFirst("^/+", "");
    }
}
