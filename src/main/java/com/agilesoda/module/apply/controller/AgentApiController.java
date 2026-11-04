package com.agilesoda.module.apply.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.agilesoda.common.response.BaseResponse;
import com.agilesoda.common.response.entity.RestResponseEntity;
import com.agilesoda.common.rest.RestAgentProvider;
import com.agilesoda.common.util.DateUtil;
import com.agilesoda.module.admin.vo.SsoLoginUserVO;
import com.agilesoda.module.apply.common.AiOcrException;
import com.agilesoda.module.apply.service.AgentApiService;
import com.agilesoda.module.apply.vo.*;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
@Tag(name = "서식 추출 제안", description = "엔진의 스키마 추출 제안 요청·조회·반영")
public class AgentApiController {

    private final RestAgentProvider restAgentProvider;
    private final AgentApiService agentApiService;

    @Operation(summary = "서식 추출 제안 목록 조회")
    @GetMapping("/suggestions")
    public ResponseEntity<?> suggestions(@RequestParam("status") String status) throws Exception {
        log.info("[suggestions] 시작");

        JSONObject jobj = new JSONObject();
        jobj.put("status", "active");
        jobj.put("page", 1);
        jobj.put("page_size", 20);

        String uri = "/api/v1/ocr/workflow/list";
        RestResponseEntity<?> resData = restAgentProvider.get(uri, jobj);

        log.debug("[suggestions] Agent 응답 status={} requestedStatus={}", resData.getStatus(), status);

        log.info("[suggestions] 종료");
        return BaseResponse.ok("성공", resData);
    }

    private RestResponseEntity<WkflowListVO> buildMockWkflowList() {
        String regDt = DateUtil.getCurrentDateTime("yyyy-MM-dd hh:mm:ss");

        WkflowVO wkflow1 = new WkflowVO();
        wkflow1.setWorkflowId("01a0f0f9-24be-71a9-97a3-d86b6f106c3c");
        wkflow1.setName("test_0930");
        wkflow1.setDescription("");
        wkflow1.setDocType("DOC001");
        wkflow1.setStatus("active");
        wkflow1.setActiveReleaseId("01a0f0f9-4f5e-7de3-bd41-c6baf659ef50");
        wkflow1.setActivatedAt(""); //

        List<WkflowVO> items = new ArrayList<>();
        items.add(wkflow1);

        WkflowListVO wkflowList = new WkflowListVO();
        wkflowList.setItems(items);
        wkflowList.setPage(1);
        wkflowList.setPageSize(20);
        wkflowList.setTotal(1);

        RestResponseEntity<WkflowListVO> restData = new RestResponseEntity<>();
        restData.setData(wkflowList);
        return restData;
    }

    /**
     * OCR서식신청 화면 "AI 제안" 버튼
     */
    @Operation(summary = "서식 추출 제안 요청")
    @PostMapping("/schema-suggestions")
    public ResponseEntity<?> submitSchemaSuggestion(@RequestParam(name = "applId") long applId) {
        log.info("[submitSchemaSuggestion] 시작 applId={}", applId);

        SchemaSuggestResultVO result;
        try {
            result = agentApiService.submitSchemaSuggestion(applId);
        }
        catch (Exception e) {
            log.error("[submitSchemaSuggestion] 스키마 추천 제출 실패 applId={}", applId, e);
            return BaseResponse.error("스키마 추천 제출 중 오류가 발생했습니다. 다시 시도해 주세요.");
        }

        log.info("[submitSchemaSuggestion] 종료 applId={}", applId);

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(BaseResponse.getSuccessBody(HttpStatus.ACCEPTED, "접수되었습니다.", result));
    }

    @Operation(summary = "서식 추출 제안 결과 조회")
    @GetMapping("/schema-suggestions/{suggestionId}")
    public ResponseEntity<?> getSchemaSuggestionResult(@PathVariable(name = "suggestionId") String  suggestionId) {
        log.debug("[getSchemaSuggestionResult] 조회 시작 suggestionId={}", suggestionId);
        SchemaSuggestionResultVO result;
        try {
            result = agentApiService.getSchemaSuggestionResult(suggestionId);
        }
        catch(Exception e) {
            log.error("[getSchemaSuggestionResult] 스키마 추천 결과 조회 실패 suggestionId={}", suggestionId, e);
            return BaseResponse.error("스키마 추천 결과 조회 중 오류가 발생했습니다. 다시 시도해 주세요.");
        }

        log.debug("[getSchemaSuggestionResult] 조회 완료 suggestionId={} status={}", suggestionId, result == null ? null : result.getStatus());
        return BaseResponse.ok("성공", result);
    }

    /**
     * 스키마 추천 결과를 추출설명 탭 형식으로 파싱한 뒤, 신청건의 서식 필드로 저장한다.
     */
    @Operation(summary = "서식 추출 제안 결과 반영")
    @PostMapping("/schema-suggestions/{suggestionId}/apply")
    public ResponseEntity<?> applySchemaSuggestionResult(@PathVariable(name = "suggestionId") String suggestionId,
                                                         @RequestParam(name = "applId") long applId, HttpSession session) {

        SsoLoginUserVO sessionUser = (SsoLoginUserVO) session.getAttribute("ssoLoginUser");
        if (sessionUser == null) {
            log.warn("[applySchemaSuggestionResult] 로그인 정보 없음 applId={} suggestionId={}", applId, suggestionId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        log.info("[applySchemaSuggestionResult] 시작 applId={} suggestionId={} userId={}", applId, suggestionId, sessionUser.getUserId());

        FormApplTempSaveResultVO result;
        try {
            result = agentApiService.applySchemaSuggestionResult(applId, suggestionId, sessionUser.getUserId());
        }
        catch (AiOcrException e) {
            if (e.getStatus() >= 400 && e.getStatus() < 500) {
                log.warn("[applySchemaSuggestionResult] 업무 실패 status={} applId={} suggestionId={} userId={} detail={}",
                        e.getStatus(), applId, suggestionId, sessionUser.getUserId(), e.getDetail());
            } else {
                log.error("[applySchemaSuggestionResult] 처리 실패 status={} applId={} suggestionId={} userId={}",
                        e.getStatus(), applId, suggestionId, sessionUser.getUserId(), e);
            }
            return BaseResponse.error(HttpStatus.valueOf(e.getStatus()), e.getDetail());
        }
        catch (Exception e) {
            log.error("[applySchemaSuggestionResult] 스키마 추천 결과 저장 실패 applId={} suggestionId={} userId={}",
                    applId, suggestionId, sessionUser.getUserId(), e);
            return BaseResponse.error("스키마 추천 결과 저장 중 오류가 발생했습니다. 다시 시도해 주세요.");
        }

        log.info("[applySchemaSuggestionResult] 완료 applId={} suggestionId={} userId={}", applId, suggestionId, sessionUser.getUserId());
        return BaseResponse.ok("성공", result);
    }
}