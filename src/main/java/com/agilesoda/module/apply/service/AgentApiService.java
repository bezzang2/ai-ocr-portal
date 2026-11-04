package com.agilesoda.module.apply.service;

import com.agilesoda.common.response.entity.RestResponseEntity;
import com.agilesoda.common.rest.RestAgentProvider;
import com.agilesoda.module.apply.common.AiOcrException;
import com.agilesoda.module.apply.mapper.AgentApiMapper;
import com.agilesoda.module.apply.vo.*;
import com.agilesoda.module.ocr.service.OcrReqService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.json.JSONObject;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentApiService {
    private final AgentApiMapper agentApiMapper;
    private final RestAgentProvider restAgentProvider;
    private final FormApplyExtractService formApplyExtractService;

    /** 저장된 분류 클래스 사본과 저장 당시 릴리스. 릴리스가 그대로면 동기화가 상세 조회 대신 이 사본을 쓴다. */
    public record StoredClasses(String releaseId, List<WkflowVO.Classify> classes) {}

    /** 업무별 저장된 분류 클래스. 읽지 못하면(마이그레이션 전 등) 비어 있어 모든 업무의 상세를 다시 받는다. */
    public Map<String, StoredClasses> getStoredClasses() {
        List<OcrWcVO> rows;
        try {
            rows = agentApiMapper.selectStoredClasses();
        } catch (DataAccessException e) {
            log.warn("[워크플로우 동기화] 분류 클래스 사본 조회 실패, 전체 상세 조회로 대체 (OCUOC_OCRWC_L 마이그레이션 확인)", e);
            return Map.of();
        }
        Map<String, StoredClasses> stored = new HashMap<>();
        for (OcrWcVO row : rows) {
            stored.computeIfAbsent(row.getWkId(), id -> new StoredClasses(row.getActiveReleaseId(), new ArrayList<>()))
                    .classes().add(new WkflowVO.Classify(row.getClassName(), row.getDocTypeCode()));
        }
        return stored;
    }

    /**
     * 엔진 워크플로우 목록을 OCUOC_OCRWF_M(·서식 매핑 OCUOC_OCRWL_L·분류 클래스 OCUOC_OCRWC_L)에 반영한다.
     * 업무 정보(동기화 시각 포함)는 매번 쓰고, 연결과 클래스 사본은 달라진 업무만 쓴다.
     * 클래스 기본값이 readOnly(읽기 DB 라우팅)이므로 쓰기 트랜잭션을 명시한다. 한 건 실패는 나머지를 막지 않는다.
     */
    @Transactional
    public WorkflowSyncResultVO syncWorkflowList(WkflowListVO wkflowListVO, Map<String, StoredClasses> stored) {
        WorkflowSyncResultVO result = new WorkflowSyncResultVO();
        List<WkflowVO> items = wkflowListVO.getItems();
        if (items == null) return result;
        List<LefrmMatchVO> forms = agentApiMapper.selectFormsForLink();
        Map<String, List<OcrWllVO>> links = agentApiMapper.selectAllOcrWll().stream()
                .collect(Collectors.groupingBy(OcrWllVO::getWkId));
        for (WkflowVO item : items) {
            try {
                StoredClasses known = stored.get(item.getWorkflowId());
                syncWorkflow(item, forms, links.getOrDefault(item.getWorkflowId(), List.of()),
                        known == null ? null : known.classes(), result);
                result.setStored(result.getStored() + 1);
            } catch (RuntimeException e) {
                result.setFailed(result.getFailed() + 1);
                log.warn("[워크플로우 동기화] 한 건 저장 실패, 건너뜀 wkId={}", item.getWorkflowId(), e);
            }
        }
        log.info("[워크플로우 동기화] DB 반영 stored={} failed={} classes={} linked={} unlinked={} linksChanged={} classStoreFailed={}",
                result.getStored(), result.getFailed(), result.getClasses(), result.getLinked(), result.getUnlinked(),
                result.getLinksChanged(), result.getClassStoreFailed());
        return result;
    }

    /** ISO 8601(Z 또는 +09:00) -> "yyyy-MM-dd HH:mm:ss"(JVM 시간대). 없거나 해석할 수 없으면 null. */
    static String toDbDateTime(String isoDateTime) {
        if (isoDateTime == null || isoDateTime.isBlank()) return null;
        try {
            return OffsetDateTime.parse(isoDateTime).atZoneSameInstant(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (DateTimeParseException e) {
            log.warn("[워크플로우 동기화] activated_at 해석 불가, null로 저장 value={}", isoDateTime);
            return null;
        }
    }

    private void syncWorkflow(WkflowVO item, List<LefrmMatchVO> forms, List<OcrWllVO> currentLinks,
                              List<WkflowVO.Classify> storedClasses, WorkflowSyncResultVO result) {
        OcrWfMVO ocrWfM = new OcrWfMVO();
        ocrWfM.setWkId(item.getWorkflowId());
        ocrWfM.setWkNm(item.getName());
        ocrWfM.setWkDesc(item.getDescription());
        ocrWfM.setDocTypNm(item.getDocType());
        ocrWfM.setWkStatCd(item.getStatus());
        ocrWfM.setActiveReleaseId(item.getActiveReleaseId());
        ocrWfM.setActivatedAt(toDbDateTime(item.getActivatedAt()));
        ocrWfM.setDocTypCd(item.getDocTypeCode());
        ocrWfM.setRgsrId("26W0061");
        agentApiMapper.upsertOcrWfM(ocrWfM);

        // 서식 매핑은 분류 클래스 기준으로 맞춘다. 상세를 못 가져온 워크플로우는 기존 매핑을 유지한다.
        if (item.getClasses() == null) return;
        // 연결이 실패해도 다음 회차가 이 사본으로 다시 맞추도록 사본을 먼저 저장한다.
        if (!item.getClasses().equals(storedClasses)) storeClasses(item, result);

        // 영문코드로만 연결한다. 코드가 없는 클래스는 미연결로 남긴다(업무·서식 연결 관리 화면에서 확인).
        Set<Long> matched = new LinkedHashSet<>();
        int linked = 0;
        for (WkflowVO.Classify cls : item.getClasses()) {
            LefrmMatchVO lefrm = forms.stream()
                    .filter(form -> OcrReqService.sameFormCode(cls.docTypeCode(), form.getDocId()))
                    .findFirst().orElse(null);
            if (lefrm == null) {
                log.debug("[워크플로우 동기화] 분류에 연결된 서식 없음 wkId={} class={} docTypeCode={}", item.getWorkflowId(), cls.className(), cls.docTypeCode());
                continue;
            }
            matched.add(lefrm.getLefrmId());
            linked++;
        }
        if (linksChanged(currentLinks, matched)) {
            agentApiMapper.deleteOcrWllByWkId(item.getWorkflowId());
            for (Long lefrmId : matched) {
                OcrWllVO ocrWll = new OcrWllVO();
                ocrWll.setWkId(item.getWorkflowId());
                ocrWll.setLefrmId(lefrmId);
                ocrWll.setRgsrId("26W0061");
                agentApiMapper.insertOcrWll(ocrWll);
            }
            result.setLinksChanged(result.getLinksChanged() + 1);
            log.info("[워크플로우 동기화] 서식 연결 변경 wkId={} before={} after={}", item.getWorkflowId(), currentLinks.size(), matched.size());
        }
        log.debug("[워크플로우 동기화] 서식 연결 wkId={} classes={} linked={}", item.getWorkflowId(), item.getClasses().size(), linked);
        result.setClasses(result.getClasses() + item.getClasses().size());
        result.setLinked(result.getLinked() + linked);
        result.setUnlinked(result.getUnlinked() + item.getClasses().size() - linked);
    }

    /** 연결된 서식이 같으면 다시 쓸 필요가 없다. 서식명·코드는 서식 마스터에서 읽으므로 비교하지 않는다. */
    private static boolean linksChanged(List<OcrWllVO> current, Set<Long> matched) {
        return !current.stream().map(OcrWllVO::getLefrmId).collect(Collectors.toSet()).equals(matched);
    }

    /**
     * 화면의 연결·미연결 표시와 다음 동기화의 상세 조회 생략에 쓰는 분류 클래스 사본. 실패해도 연결은 유지한다
     * (은행 DB에 OCUOC_OCRWC_L 마이그레이션이 아직 없을 때). 같은 원인이 업무마다 반복되므로 예외는 첫 건만 남긴다.
     */
    private void storeClasses(WkflowVO item, WorkflowSyncResultVO result) {
        try {
            agentApiMapper.deleteOcrWcByWkId(item.getWorkflowId());
            int seq = 0;
            for (WkflowVO.Classify cls : item.getClasses()) {
                agentApiMapper.insertOcrWc(item.getWorkflowId(), ++seq, cls.className(), cls.docTypeCode());
            }
        } catch (DataAccessException e) {
            result.setClassStoreFailed(result.getClassStoreFailed() + 1);
            if (result.getClassStoreFailed() == 1) {
                log.warn("[워크플로우 동기화] 분류 클래스 저장 실패, 서식 연결은 유지 wkId={} (OCUOC_OCRWC_L 마이그레이션 확인)",
                        item.getWorkflowId(), e);
            } else {
                log.debug("[워크플로우 동기화] 분류 클래스 저장 실패 wkId={} reason={}", item.getWorkflowId(), e.getMessage());
            }
            removePartialClasses(item.getWorkflowId());
        }
    }

    /** 일부만 저장된 사본을 믿고 상세 조회를 건너뛰면 빠진 클래스의 연결이 사라지므로, 사본을 비워 다음 회차에 다시 받게 한다. */
    private void removePartialClasses(String wkId) {
        try {
            agentApiMapper.deleteOcrWcByWkId(wkId);
        } catch (DataAccessException e) {
            log.debug("[워크플로우 동기화] 분류 클래스 사본 정리 실패 wkId={} reason={}", wkId, e.getMessage());
        }
    }


    /**
     * OCR서식신청 화면 "AI 제안" 버튼
     */
    public SchemaSuggestResultVO submitSchemaSuggestion(long applId) throws Exception {
        List<FormApplImgVO> imgList = formApplyExtractService.getFormApplImgList(applId);
        log.debug("[스키마추천] 제출 대상 이미지 applId={} imageCount={}", applId, imgList == null ? 0 : imgList.size());
        if (imgList == null || imgList.size() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "스키마 추천을 받으려면 문서가 1건 이상 필요합니다.");
        }
        if (imgList.size() > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "스키마 추천은 문서 10건 까지만 제출할 수 있습니다.");
        }

        List<SchemaSuggestDocumenntVO> documents = new ArrayList<>();
        for (FormApplImgVO img : imgList) {
            documents.add(toSchemaSuggestDocument(img));
        }

        return submitSchemaSuggestion(documents);
    }

    private SchemaSuggestResultVO submitSchemaSuggestion(List<SchemaSuggestDocumenntVO> documents) throws Exception {
        String uri = "/api/v1/ocr/schema/suggestions";

        JSONObject param = new JSONObject();
        param.put("req_guid", "");
        param.put("documents", documents);

        RestResponseEntity<?> result = restAgentProvider.post(uri, param);

        if (result.getStatus() != HttpStatus.OK.value() && result.getStatus() != HttpStatus.ACCEPTED.value()) {
            log.warn("[스키마추천] Agent 제출 거절 agentStatus={} message={}", result.getStatus(), result.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "스키마 제출 에러");
        }

        ObjectMapper om = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        log.debug("[스키마추천] 제출 응답 agentStatus={} documentCount={}", result.getStatus(), documents.size());
        return om.convertValue(result.getData(), SchemaSuggestResultVO.class);
    }

    /**
     * 이미지마다 문서 객체 만듬
     */
    private SchemaSuggestDocumenntVO toSchemaSuggestDocument(FormApplImgVO img) throws IOException {
        Path path = Paths.get(img.getFilePath());
        byte[] bytes = Files.readAllBytes(path);

        SchemaSuggestDocumenntVO document = new SchemaSuggestDocumenntVO();
        document.setFilename(img.getFileNm());
        document.setFormat(resolveFormat(img.getFileNm()));
        document.setData(Base64.getEncoder().encodeToString(bytes));
        return document;
    }

    /**
     * 동일한 확장자 기준 포맷 판별
     */
    private String resolveFormat(String fileNm) {
        String ext = fileNm.substring(fileNm.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        switch (ext) {
            case "pdf":
                return "pdf";
            default:
                return "image";
        }
    }

    /**
     * 스키마 추천 결과 조회
     * suggestion_id로 산출물을 조회만 한다. 저장은 하지 않는다.
     */
    public SchemaSuggestionResultVO getSchemaSuggestionResult(String suggestionId) throws Exception {
        return schemaSuggestionResult(suggestionId);
    }

    private SchemaSuggestionResultVO schemaSuggestionResult(String suggestionId) throws Exception {
        String uri = "/api/v1/ocr/schema/suggestions/"+suggestionId;
        RestResponseEntity<?> result = restAgentProvider.get(uri);

        if (result.getStatus() != HttpStatus.OK.value() && result.getStatus() != HttpStatus.ACCEPTED.value()) {
            log.warn("[스키마추천] Agent 결과 조회 거절 suggestionId={} agentStatus={} message={}", suggestionId, result.getStatus(), result.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "스키마 추천 결과 조회 에러");
        }

        ObjectMapper om = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        return om.convertValue(result.getData(), SchemaSuggestionResultVO.class);
    }

    /**
     * 스키마 추천 결과 조회 후, 추출설명 변환해 저장
     */
    public FormApplTempSaveResultVO applySchemaSuggestionResult(long applId, String suggestionId, String regId) throws Exception {
        SchemaSuggestionResultVO suggestionResult = getSchemaSuggestionResult(suggestionId);

        if (!"succeeded".equals(suggestionResult.getStatus())) {
           throw new AiOcrException(409, "about:blank", "conflict", "SCHEMA_SUGGEST_NOT_READY",
                   "스키마 추천 결과가 아직 준비되지 않았습니다. status=" + suggestionResult.getStatus());
        }

        List<FormApplFldVO> fldList = new ArrayList<>();
        List<FormApplFldGroupVO> groupList = new ArrayList<>();
        if (suggestionResult.getResults() != null) {
            for (SchemaSuggestionItemVO item : suggestionResult.getResults()) {
                if (item.getExtractSchema() == null) {
                    continue;
                }
                ExtractSchemaVO extractSchema = item.getExtractSchema();

                // 최상위 단일 필드는 그룹 묶음 없이 "기본정보" 성격의 평문 필드로 저장한다.
                if (extractSchema.getFields() != null) {
                    for (ExtractFieldVO field : extractSchema.getFields()) {
                        fldList.add(toDescFld(toDescItem(field)));
                    }
                }

                // 그룹은 mode 구분 없이 각각 하나의 그룹으로 저장한다.
                if (extractSchema.getGroups() != null) {
                    for (ExtractGroupVO group : extractSchema.getGroups()) {
                        groupList.add(toDescFldGroup(toDescGroup(group)));
                    }
                }
            }
        }

        FormApplDetailVO formAppl = formApplyExtractService.getFormApplDetail(applId);
        String formNm = formAppl != null ? formAppl.getFormNm() : null;

        List<FormApplImgVO> imgList = formApplyExtractService.getFormApplImgList(applId);
        if (imgList == null || imgList.isEmpty()) {
            throw new AiOcrException(400, "about:blank", "Bad Request", "SCHEMA_SUGGEST_NOT_READY",
                    "스키마 추천 결과를 저장하려면 서식목록에 이미지가 1건 이상 있어야 합니다.");
        }
        Long activeImgId = imgList.get(0).getImgId();

        return formApplyExtractService.tempSaveFormAppl(applId, formNm, null, fldList, groupList, null, null, null, null,
                activeImgId, null, false, null, null, regId);
    }

    /**
     * 추출 스키마의 그룹 하나를 추출설명 탭 그룹으로 변환한다.
     */
    private DescGroupVO toDescGroup(ExtractGroupVO group) {
        DescGroupVO descGroup = new DescGroupVO();
        descGroup.setGroupFldId(0);
        String groupNm = group.getDisplayLabel() != null ? group.getDisplayLabel() : group.getPath();
        descGroup.setGroupNm(groupNm);
        descGroup.setGroupDesc(group.getDescription());

        List<DescItemVO> items = new ArrayList<>();
        if (group.getItems() != null) {
            for (ExtractFieldVO field : group.getItems()) {
                items.add(toDescItem(field));
            }
        }
        descGroup.setItems(items);
        return descGroup;
    }

    private DescItemVO toDescItem(ExtractFieldVO field) {
        DescItemVO item = new DescItemVO();
        item.setFldKey(field.getPath());
        item.setFldNm(field.getDisplayLabel() != null ? field.getDisplayLabel() : field.getPath());
        item.setDescription(field.getDescription());
        item.setReqYn(field.isRequired() ? "Y" : "N");
        return item;
    }

    private FormApplFldVO toDescFld(DescItemVO descItem) {
        FormApplFldVO fld = new FormApplFldVO();
        fld.setSrcTypeCd("DESC");
        fld.setFldNm(descItem.getFldNm());
        fld.setFldDesc(descItem.getDescription());
        fld.setReqYn(descItem.getReqYn());
        return fld;
    }

    private FormApplFldGroupVO toDescFldGroup(DescGroupVO descGroup) {
        FormApplFldGroupVO group = new FormApplFldGroupVO();
        group.setGroupFldId(0);
        group.setGroupNm(descGroup.getGroupNm());
        group.setSrcTypeCd("DESC");
        group.setColumns(new ArrayList<>());

        List<List<FormApplFldVO>> rows = new ArrayList<>();
        if (descGroup.getItems() != null) {
            for (DescItemVO descItem : descGroup.getItems()) {
                FormApplFldVO cell = new FormApplFldVO();
                cell.setRowNo(1);
                cell.setFldTypeCd("GROUP_ITEM");
                cell.setSrcTypeCd("DESC");
                cell.setFldNm(descItem.getFldNm());
                cell.setFldDesc(descItem.getDescription());
                cell.setReqYn(descItem.getReqYn());
                List<FormApplFldVO> row = new ArrayList<>();
                row.add(cell);
                rows.add(row);
            }
        }
        group.setRows(rows);
        return group;
    }
}
