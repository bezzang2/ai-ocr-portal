package com.agilesoda.module.apply.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.agilesoda.module.admin.vo.SsoLoginUserVO;
import com.agilesoda.module.apply.service.FormLinkService;
import com.agilesoda.module.apply.service.WorkflowSyncService;
import com.agilesoda.module.apply.vo.FormMasterVO;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * 관리자 > 업무·서식 연결 관리. 업무별 분류 클래스의 서식 연결 상태(연결·연결 대기·미연결)를 보여 주고,
 * 서식 마스터 등록·수정과 워크플로 즉시 동기화를 한다. 데이터는 이 경로 아래 JSON API 로 받는다.
 */
@Slf4j
@Controller
@RequestMapping("/module/admin/formLink")
@RequiredArgsConstructor
@Tag(name = "서식 연결 관리", description = "업무·서식 연결 조회와 엔진 동기화")
public class FormLinkController {

    private final FormLinkService formLinkService;
    private final WorkflowSyncService workflowSyncService;

    @GetMapping
    public String page(@RequestParam(value = "menuId", defaultValue = "") String menuId, Model model) {
        model.addAttribute("menuId", menuId);
        return "module/admin/formLink";
    }

    @Operation(summary = "연결할 업무 목록 조회")
    @GetMapping("/workflows")
    @ResponseBody
    public ResponseEntity<?> workflows(HttpSession session) {
        return handle("workflows", null, session, formLinkService::getWorkflowList);
    }

    @Operation(summary = "업무의 서식 분류 조회")
    @GetMapping("/classes")
    @ResponseBody
    public ResponseEntity<?> classes(@RequestParam(value = "wkId", required = false) String wkId,
                                     @RequestParam(value = "unlinkedOnly", defaultValue = "false") boolean unlinkedOnly,
                                     HttpSession session) {
        return handle("classes", wkId, session, () -> formLinkService.getClassList(wkId, unlinkedOnly));
    }

    @Operation(summary = "서식 목록 조회")
    @GetMapping("/forms")
    @ResponseBody
    public ResponseEntity<?> forms(HttpSession session) {
        return handle("forms", null, session, formLinkService::getFormList);
    }

    @Operation(summary = "서식 연결 저장")
    @PostMapping("/forms")
    @ResponseBody
    public ResponseEntity<?> saveForm(@RequestBody FormMasterVO form, HttpSession session) {
        String target = form.getLefrmId() == null ? "new" : String.valueOf(form.getLefrmId());
        return handle("saveForm", target, session, () -> {
            String userId = userId(session);
            formLinkService.requireManager(userId);
            return formLinkService.saveForm(form, userId);
        });
    }

    @Operation(summary = "엔진 업무와 서식 연결 동기화")
    @PostMapping("/sync")
    @ResponseBody
    public ResponseEntity<?> sync(HttpSession session) {
        return handle("sync", null, session, () -> {
            String userId = userId(session);
            formLinkService.requireManager(userId);
            return workflowSyncService.sync(userId);
        });
    }

    private static String userId(HttpSession session) {
        SsoLoginUserVO user = (SsoLoginUserVO) session.getAttribute("ssoLoginUser");
        return user != null ? user.getUserId() : null;
    }

    private ResponseEntity<?> handle(String action, String target, HttpSession session, Callable<?> work) {
        String userId = userId(session);
        log.info("[업무·서식 연결] {} 요청 target={} userId={}", action, target, userId);
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(work.call());
        } catch (ResponseStatusException e) {
            log.warn("[업무·서식 연결] {} 거절 status={} target={} userId={} reason={}",
                    action, e.getStatusCode().value(), target, userId, e.getReason());
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason()));
        } catch (Exception e) {
            log.error("[업무·서식 연결] {} 실패 target={} userId={}", action, target, userId, e);
            return ResponseEntity.internalServerError().body(Map.of("message", "처리하지 못했습니다. 잠시 후 다시 시도해 주세요."));
        }
    }
}
