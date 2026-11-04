package com.agilesoda.module.apply.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.agilesoda.module.admin.vo.SsoLoginUserVO;
import com.agilesoda.module.apply.service.CrossRuleDefService;
import com.agilesoda.module.apply.service.CrossRuleException;
import com.agilesoda.module.apply.vo.CrossRoleVO;
import com.agilesoda.module.apply.vo.CrossRuleVO;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** 신청관리 > 서식간 규칙 관리 화면과 API. 판정은 Agent 가 하고, 저장한 결과는 통합검색 > 서식간 검증 결과에서 본다. */
@Slf4j
@Controller
@RequestMapping("/module/apply/crossRule")
@RequiredArgsConstructor
@Tag(name = "서식간 검증 규칙", description = "업무별 규칙·역할 정의와 시험 판정")
public class CrossRuleDefController {

    private final CrossRuleDefService service;

    @GetMapping
    public String page(@RequestParam(value = "wkId", required = false) String wkId,
                       @RequestParam(value = "menuId", defaultValue = "") String menuId, Model model, HttpSession session) {
        var workflows = service.workflows();
        String selected = wkId;
        if ((selected == null || selected.isBlank()) && !workflows.isEmpty()) {
            selected = workflows.get(0).getWkId();
        }
        model.addAttribute("workflowList", workflows);
        model.addAttribute("wkId", selected);
        model.addAttribute("menuId", menuId);
        // 결과 화면에서 값을 고칠 때 "내가 맡기"에 쓴다.
        SsoLoginUserVO user = (SsoLoginUserVO) session.getAttribute("ssoLoginUser");
        model.addAttribute("userId", user == null ? "" : user.getUserId());
        return "module/apply/cross-rule-builder";
    }

    @Operation(summary = "규칙 편집을 위한 업무 정보 조회")
    @GetMapping("/context")
    @ResponseBody
    public ResponseEntity<?> context(@RequestParam("wkId") String wkId, HttpSession session) {
        return handle("context", wkId, null, session, () -> service.context(wkId));
    }

    @Operation(summary = "업무의 서식간 규칙 조회")
    @GetMapping("/rules")
    @ResponseBody
    public ResponseEntity<?> rules(@RequestParam("wkId") String wkId, HttpSession session) {
        return handle("list", wkId, null, session, () -> service.list(wkId));
    }

    /** 저장하지 않는 판정(화면 미리보기) */
    @Operation(summary = "서식간 규칙 시험 판정")
    @PostMapping("/judge")
    @ResponseBody
    public ResponseEntity<?> judge(@RequestParam("wkId") String wkId, @RequestParam("transId") String transId,
                                   @RequestBody List<CrossRuleVO> rules, HttpSession session) {
        return handle("judge", wkId, null, session, () -> service.judge(wkId, transId, rules));
    }

    @Operation(summary = "서식간 규칙 저장")
    @PostMapping("/rules")
    @ResponseBody
    public ResponseEntity<?> save(@RequestBody CrossRuleVO rule, HttpSession session) {
        return handle("save", rule.getWkId(), rule.getRuleId(), session, () -> service.save(rule, userId(session)));
    }

    @Operation(summary = "서식간 규칙 사용 여부 변경")
    @PostMapping("/rules/{ruleId}/use")
    @ResponseBody
    public ResponseEntity<?> use(@PathVariable long ruleId, @RequestParam("wkId") String wkId,
                                 @RequestParam("value") boolean value, HttpSession session) {
        return handle("use", wkId, ruleId, session, () -> {
            service.setUse(wkId, ruleId, value, userId(session));
            return Map.of("ruleId", ruleId);
        });
    }

    @Operation(summary = "서식간 규칙 삭제")
    @PostMapping("/rules/{ruleId}/delete")
    @ResponseBody
    public ResponseEntity<?> delete(@PathVariable long ruleId, @RequestParam("wkId") String wkId, HttpSession session) {
        return handle("delete", wkId, ruleId, session, () -> {
            service.delete(wkId, ruleId, userId(session));
            return Map.of("ruleId", ruleId);
        });
    }

    @Operation(summary = "업무의 문서 역할 조회")
    @GetMapping("/roles")
    @ResponseBody
    public ResponseEntity<?> roles(@RequestParam("wkId") String wkId, HttpSession session) {
        return handle("roles", wkId, null, session, () -> service.roles(wkId));
    }

    @Operation(summary = "문서 역할 저장")
    @PostMapping("/roles")
    @ResponseBody
    public ResponseEntity<?> saveRole(@RequestBody CrossRoleVO role, HttpSession session) {
        return handle("saveRole", role.getWkId(), null, session, () -> {
            service.saveRole(role, userId(session));
            return service.roles(role.getWkId());
        });
    }

    @Operation(summary = "문서 역할 삭제")
    @PostMapping("/roles/delete")
    @ResponseBody
    public ResponseEntity<?> deleteRole(@RequestBody CrossRoleVO role, HttpSession session) {
        return handle("deleteRole", role.getWkId(), null, session, () -> {
            service.deleteRole(role.getWkId(), role.getFormId(), role.getRoleNm(), userId(session));
            return service.roles(role.getWkId());
        });
    }

    private static String userId(HttpSession session) {
        SsoLoginUserVO user = (SsoLoginUserVO) session.getAttribute("ssoLoginUser");
        if (user == null || user.getUserId() == null) {
            throw new CrossRuleException(HttpStatus.UNAUTHORIZED, "로그인 정보가 없습니다. 다시 로그인해 주세요.");
        }
        return user.getUserId();
    }

    private ResponseEntity<?> handle(String action, String wkId, Long ruleId, HttpSession session, Supplier<?> work) {
        log.info("[서식간규칙관리] {} 요청 wkId={} ruleId={}", action, wkId, ruleId);
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(work.get());
        } catch (CrossRuleException e) {
            log.warn("[서식간규칙관리] {} 거절 status={} wkId={} ruleId={} reason={}",
                    action, e.getStatus().value(), wkId, ruleId, e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
        } catch (ResponseStatusException e) {
            // Agent 가 거절한 요청(없는 판독 건 등)은 예상 가능한 실패다. 5xx 는 아래 실패 응답으로 보낸다.
            if (e.getStatusCode().is4xxClientError()) {
                log.warn("[서식간규칙관리] {} 거절 status={} wkId={} ruleId={} reason={}",
                        action, e.getStatusCode().value(), wkId, ruleId, e.getReason());
                return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",
                        Objects.requireNonNullElse(e.getReason(), "요청을 확인해 주세요.")));
            }
            log.error("[서식간규칙관리] {} 실패 wkId={} ruleId={}", action, wkId, ruleId, e);
            return ResponseEntity.internalServerError().body(Map.of("message", "처리하지 못했습니다. 잠시 후 다시 시도해 주세요."));
        } catch (Exception e) {
            log.error("[서식간규칙관리] {} 실패 wkId={} ruleId={}", action, wkId, ruleId, e);
            return ResponseEntity.internalServerError().body(Map.of("message", "처리하지 못했습니다. 잠시 후 다시 시도해 주세요."));
        }
    }
}
