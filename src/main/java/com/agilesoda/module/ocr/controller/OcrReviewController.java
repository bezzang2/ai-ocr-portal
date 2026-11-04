package com.agilesoda.module.ocr.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.agilesoda.module.admin.vo.SsoLoginUserVO;
import com.agilesoda.module.ocr.service.OcrReviewException;
import com.agilesoda.module.apply.service.CrossRuleDefService;
import com.agilesoda.module.ocr.service.OcrReviewService;
import com.agilesoda.module.ocr.vo.OcrAssignRequestVO;
import com.agilesoda.module.ocr.vo.OcrReviewStateVO;
import com.agilesoda.module.ocr.vo.OcrReviewerVO;
import com.agilesoda.module.ocr.vo.OcrVerifyRequestVO;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 판독결과 검토 API: 검증 담당자 지정, 확인값 저장, 검증완료와 그 취소.
 * 권한은 서버에서 로그인 세션 기준으로 판단한다. 업무 거절(4xx)은 WARN, 그 밖의 실패는 ERROR 로 이곳에서 한 번만 남긴다.
 */
@Slf4j
@RestController
@RequestMapping("/module/ocr/result")
@RequiredArgsConstructor
@Tag(name = "판독결과 검토", description = "담당자·확인값·문서 역할과 검증완료 관리. 변경 권한은 로그인 세션으로 확인한다.")
public class OcrReviewController {

    private static final Pattern TRANS_ID = Pattern.compile("[A-Za-z0-9-]{1,50}");

    private final OcrReviewService ocrReviewService;
    private final CrossRuleDefService crossRuleDefService;

    /** 선택한 서식 문서의 검토 상태(담당자·검증완료·저장 시각) */
    @Operation(summary = "문서 검토 상태 조회")
    @GetMapping("/lefrm/{wkId}/{lefrmId}/review")
    public ResponseEntity<?> reviewState(@PathVariable String wkId, @PathVariable long lefrmId,
                                         @RequestParam(value = "transId", required = false) String transId,
                                         @RequestParam(value = "docId", required = false) String docId) {
        return handle("state", wkId, lefrmId, transId, null,
                () -> ocrReviewService.documentState(wkId, lefrmId, transId, docId));
    }

    /** 판독작업 담당자 지정·해제. 본인 지정은 "내가 맡기"다. */
    @Operation(summary = "판독작업 담당자 지정 또는 해제")
    @PostMapping("/wk/{wkId}/trans/{transId}/assignee")
    public ResponseEntity<?> assignTransaction(@PathVariable String wkId, @PathVariable String transId,
                                               @RequestBody OcrAssignRequestVO request, HttpSession session) {
        if (!TRANS_ID.matcher(transId).matches()) {
            log.warn("[검토] 담당자 지정 거절, transId 형식 오류 wkId={}", wkId);
            return ResponseEntity.badRequest().body(Map.of("message", "잘못된 판독작업입니다."));
        }
        OcrReviewerVO reviewer = reviewer(session);
        return handle("assign-trans", wkId, null, transId, reviewer,
                () -> ocrReviewService.assignTransaction(wkId, transId, request.getUserId(), reviewer));
    }

    /** 확인값 임시저장 */
    @Operation(summary = "문서 확인값 임시저장")
    @PostMapping("/lefrm/{wkId}/{lefrmId}/save")
    public ResponseEntity<?> save(@PathVariable String wkId, @PathVariable long lefrmId,
                                  @RequestParam(value = "transId", required = false) String transId,
                                  @RequestParam(value = "docId", required = false) String docId,
                                  @RequestBody OcrVerifyRequestVO request, HttpSession session) {
        OcrReviewerVO reviewer = reviewer(session);
        return handle("save", wkId, lefrmId, transId, reviewer,
                () -> ocrReviewService.saveConfirmValues(wkId, lefrmId, transId, docId, request, reviewer));
    }

    /** "이 서식 검증완료": 확인값 저장 + 검증완료 상태 + 결과 파일 */
    @Operation(summary = "문서 검증완료")
    @PostMapping("/lefrm/{wkId}/{lefrmId}/verify")
    public ResponseEntity<?> verify(@PathVariable String wkId, @PathVariable long lefrmId,
                                    @RequestParam(value = "transId", required = false) String transId,
                                    @RequestParam(value = "docId", required = false) String docId,
                                    @RequestBody OcrVerifyRequestVO request, HttpSession session) {
        OcrReviewerVO reviewer = reviewer(session);
        return handle("verify", wkId, lefrmId, transId, reviewer,
                () -> ocrReviewService.verify(wkId, lefrmId, transId, docId, request, reviewer));
    }

    /** 문서 역할 지정. 역할이 비면 지정을 지운다. */
    @Operation(summary = "문서 역할 지정 또는 해제")
    @PostMapping("/lefrm/{wkId}/{lefrmId}/role")
    public ResponseEntity<?> setRole(@PathVariable String wkId, @PathVariable long lefrmId,
                                     @RequestParam(value = "transId", required = false) String transId,
                                     @RequestParam(value = "docId", required = false) String docId,
                                     @RequestBody Map<String, String> request, HttpSession session) {
        OcrReviewerVO reviewer = reviewer(session);
        return handle("role", wkId, lefrmId, transId, reviewer,
                () -> ocrReviewService.setRole(wkId, lefrmId, transId, docId, request.get("role"), reviewer));
    }

    /** 이 서식에 설정한 역할 이름. 역할 선택칸의 보기로 쓴다 */
    @Operation(summary = "문서 역할 선택 목록 조회")
    @GetMapping("/lefrm/{wkId}/{lefrmId}/role-options")
    public ResponseEntity<List<String>> roleOptions(@PathVariable String wkId, @PathVariable long lefrmId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(crossRuleDefService.roleOptions(wkId, lefrmId));
    }

    /** 검증완료 취소(재검토). 검증한 사람이나 관리자·팀장만 한다. */
    @Operation(summary = "문서 검증완료 취소")
    @PostMapping("/lefrm/{wkId}/{lefrmId}/verify/cancel")
    public ResponseEntity<?> cancelVerify(@PathVariable String wkId, @PathVariable long lefrmId,
                                          @RequestParam(value = "transId", required = false) String transId,
                                          @RequestParam(value = "docId", required = false) String docId,
                                          HttpSession session) {
        OcrReviewerVO reviewer = reviewer(session);
        return handle("verify-cancel", wkId, lefrmId, transId, reviewer,
                () -> ocrReviewService.cancelVerify(wkId, lefrmId, transId, docId, reviewer));
    }

    private OcrReviewerVO reviewer(HttpSession session) {
        return ocrReviewService.reviewer((SsoLoginUserVO) session.getAttribute("ssoLoginUser"));
    }

    private ResponseEntity<?> handle(String action, String wkId, Long lefrmId, String transId, OcrReviewerVO reviewer,
                                     Supplier<OcrReviewStateVO> work) {
        String userId = reviewer != null ? reviewer.getUserId() : null;
        long started = System.nanoTime();
        log.info("[검토] 요청 수신 action={} wkId={} lefrmId={} transId={} userId={}", action, wkId, lefrmId, transId, userId);
        try {
            OcrReviewStateVO state = work.get();
            log.info("[검토] 처리 완료 action={} wkId={} lefrmId={} transId={} userId={} verifyStatus={} elapsedMs={}",
                    action, wkId, lefrmId, transId, userId, state == null ? null : state.getVrfyStatCd(),
                    (System.nanoTime() - started) / 1_000_000);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(state);
        } catch (OcrReviewException e) {
            log.warn("[검토] 업무 거절 action={} status={} wkId={} lefrmId={} transId={} userId={} reason={}",
                    action, e.getStatus().value(), wkId, lefrmId, transId, userId, e.getMessage());
            return ResponseEntity.status(e.getStatus()).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            log.error("[검토] 처리 실패 action={} wkId={} lefrmId={} transId={} userId={}", action, wkId, lefrmId, transId, userId, e);
            return ResponseEntity.internalServerError().body(Map.of("message", "처리하지 못했습니다. 잠시 후 다시 시도해 주세요."));
        }
    }
}
