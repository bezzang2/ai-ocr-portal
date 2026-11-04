package com.agilesoda.module.apply.service;

import com.agilesoda.common.util.DateUtil;
import com.agilesoda.module.apply.mapper.FormApplyExtractMapper;
import com.agilesoda.module.apply.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FormApplyExtractService {

    private final FormApplyExtractMapper formApplyExtractMapper;

    @Value("${aiocr.file.path}")
    private String fileBasePath;

    public FormApplDetailVO getFormApplDetail(long applId) {
        return formApplyExtractMapper.selectFormApplDetail(applId);
    }

    public AiSuggestPreviewResultVO getFormApplFldDetail(long applId) {
        List<FormApplFldVO> allFlds = formApplyExtractMapper.selectFormApplFldList(applId);

        List<FormApplFldVO> fldList = new ArrayList<>();
        List<FormApplFldGroupVO> groupList = new ArrayList<>();
        List<DescItemVO> descItems = new ArrayList<>();
        Map<Long, List<FormApplFldVO>> childrenByUpFldId = new LinkedHashMap<>();

        for (FormApplFldVO fld : allFlds) {
            if (fld.getUpFldId() != null) {
                childrenByUpFldId.computeIfAbsent(fld.getUpFldId(), k -> new ArrayList<>()).add(fld);
            }
        }

        // 일치 Input 항목을 항목ID별로 묶어 둔다
        Map<Long, List<String>> matchFldIdsByFldId = new LinkedHashMap<>();
        for (FormApplFldMatchVO match : formApplyExtractMapper.selectFormApplFldMatchList(applId)) {
            matchFldIdsByFldId.computeIfAbsent(match.getFldId(), k -> new ArrayList<>()).add(match.getMatchFldId());
        }

        List<DescGroupVO> descGroupList = new ArrayList<>();
        for (FormApplFldVO fld : allFlds) {
            if ("GROUP".equals(fld.getFldTypeCd())) {
                if ("DESC".equals(fld.getSrcTypeCd())) {
                    descGroupList.add(toDescGroupFromDb(fld, childrenByUpFldId.get(fld.getFldId()), matchFldIdsByFldId));
                } else {
                    groupList.add(toFldGroupFromDb(fld, childrenByUpFldId.get(fld.getFldId())));
                }
            } else if (fld.getUpFldId() == null) {
                if ("DESC".equals(fld.getSrcTypeCd())) {
                    descItems.add(toDescItemFromDb(fld, matchFldIdsByFldId));
                } else {
                    fldList.add(fld);
                }
            }
        }
        
        if (!descItems.isEmpty()) {
            DescGroupVO descGroup = new DescGroupVO();
            descGroup.setGroupNm("기본정보");
            descGroup.setItems(descItems);
            descGroupList.add(0, descGroup);
        }

        AiSuggestPreviewResultVO result = new AiSuggestPreviewResultVO();
        result.setFldList(fldList);
        result.setGroupList(groupList);
        result.setDescGroupList(descGroupList);
        return result;
    }


    private FormApplFldGroupVO toFldGroupFromDb(FormApplFldVO groupFld, List<FormApplFldVO> children) {
        FormApplFldGroupVO group = new FormApplFldGroupVO();
        group.setGroupFldId(groupFld.getFldId());
        group.setGroupNm(groupFld.getFldNm());

        if (children == null || children.isEmpty()) {
            group.setColumns(new ArrayList<>());
            group.setRows(new ArrayList<>());
            return group;
        }

        Map<Integer, List<FormApplFldVO>> byRowNo = new LinkedHashMap<>();
        List<String> columns = new ArrayList<>();
        for (FormApplFldVO child : children) {
            if (!columns.contains(child.getFldNm())) {
                columns.add(child.getFldNm());
            }
            byRowNo.computeIfAbsent(child.getRowNo(), k -> new ArrayList<>()).add(child);
        }

        List<List<FormApplFldVO>> rows = new ArrayList<>(byRowNo.values());
        group.setColumns(columns);
        group.setRows(rows);
        return group;
    }

    private DescItemVO toDescItemFromDb(FormApplFldVO fld, Map<Long, List<String>> matchFldIdsByFldId) {
        DescItemVO item = new DescItemVO();
        item.setFldId(fld.getFldId());
        item.setFldKey(fld.getFldCd());
        item.setFldNm(fld.getFldNm());
        item.setDescription(fld.getFldDesc());
        item.setReqYn(fld.getReqYn());
        item.setDataTypeCd(fld.getDataTypeCd());
        item.setDataLen(fld.getDataLen());
        item.setMaskYn(fld.getMaskYn());
        item.setMaskRange(fld.getMaskRange());
        item.setDataCmprRule(fld.getDataCmprRule());
        item.setMatchFldIdList(matchFldIdsByFldId.getOrDefault(fld.getFldId(), new ArrayList<>()));
        item.setMetaClss(fld.getMetaClss());
        item.setMetaType(fld.getMetaType());
        item.setMetaWord(fld.getMetaWord());
        item.setMetaLnm(fld.getMetaLnm());
        item.setMetaPnm(fld.getMetaPnm());
        return item;
    }

    // 추출설명 탭의 그룹...
    private DescGroupVO toDescGroupFromDb(FormApplFldVO groupFld, List<FormApplFldVO> children,
            Map<Long, List<String>> matchFldIdsByFldId) {
        DescGroupVO group = new DescGroupVO();
        group.setGroupFldId(groupFld.getFldId());
        group.setGroupNm(groupFld.getFldNm());
        group.setGroupDesc(groupFld.getFldDesc());

        List<DescItemVO> items = new ArrayList<>();
        if (children != null) {
            for (FormApplFldVO child : children) {
                items.add(toDescItemFromDb(child, matchFldIdsByFldId));
            }
        }
        group.setItems(items);
        return group;
    }

    public FormApplImgVO getFormApplImg(long imgId) {
        return formApplyExtractMapper.selectFormApplImg(imgId);
    }

    public List<FormApplImgVO> getFormApplImgList(long applId) {
        return formApplyExtractMapper.selectFormApplImgList(applId);
    }

    public List<FormApplFileVO> getFormApplFileList(long applId) {
        return formApplyExtractMapper.selectFormApplFileList(applId);
    }

    /**
     * OCR서식신청 저장 (신청:APPL, 임시:DRAFT)
     */
    public FormApplTempSaveResultVO tempSaveFormAppl(long applId, String formNm,
            List<FormApplWriteCntnVO> writeCntList, List<FormApplFldVO> fldList,
            List<FormApplFldGroupVO> groupList, List<MultipartFile> attachFiles, List<String> attachSrcTypeCdLis,
            List<MultipartFile> formImgFiles, List<FormApplImgMetaVO> formImgMetaList, Long activeImgId,
            List<FormApplMgrVO> mgrList, boolean submit, String applicantUserId, String applDeptCd, String regId) {
        String regDt = DateUtil.getCurrentDateTime("yyyy-MM-dd hh:mm:ss");
        boolean justSubmitted = false;

        if (applId > 0) {
            formApplyExtractMapper.updateFormAppl(applId, formNm, regId, regDt);
            if (submit) {
                justSubmitted = formApplyExtractMapper.submitFormAppl(applId, regId, regDt) > 0;
            }
        } else {
            FormApplInsertVO formAppl = new FormApplInsertVO();
            formAppl.setFormNm(formNm);
            formAppl.setApplicantUserId(applicantUserId);
            formAppl.setApplDeptCd(applDeptCd);
            formAppl.setProcStatCd(submit ? "APPL" : "DRAFT");
            formAppl.setRegId(regId);
            formAppl.setRegDt(regDt);
            formApplyExtractMapper.insertFormAppl(formAppl);
            applId = formAppl.getApplId();
            justSubmitted = submit;
        }

        if (writeCntList != null && !writeCntList.isEmpty()) {
            for (FormApplWriteCntnVO wrieCntnItem : writeCntList) {
                formApplyExtractMapper.upsertFormApplWriteCntn(applId, wrieCntnItem.getSrcTypeCd(),
                        wrieCntnItem.getWriteCntn(), regId, regDt);
            }
        }

        if (attachFiles != null && !attachFiles.isEmpty()) {
            saveAttachFiles(applId, attachFiles, attachSrcTypeCdLis, regId, regDt);
        }

        if (formImgFiles != null && !formImgFiles.isEmpty()) {
            Long newActiveImgId = saveFormImgFiles(applId, formImgFiles, formImgMetaList, regId, regDt);
            if (newActiveImgId != null) {
                activeImgId = newActiveImgId;
            }
        }

        if (fldList != null && !fldList.isEmpty()) {
            List<FormApplFldVO> newFldList = new ArrayList<>();
            for (FormApplFldVO fld : fldList) {
                if (fld.getSrcTypeCd() == null || fld.getSrcTypeCd().isEmpty()) {
                    fld.setSrcTypeCd("ITEM");
                }
                if (fld.getImgId() == null && activeImgId != null) {
                    fld.setImgId(activeImgId);
                }
                if(fld.getReqYn() == null || fld.getReqYn().isEmpty()) {
                    fld.setReqYn("N");
                }
                if (fld.getFldTypeCd() == null || fld.getFldTypeCd().isEmpty()) {
                    fld.setFldTypeCd("NORMAL");
                }
                if (fld.getFldId() > 0) {
                    fld.setApplId(applId);
                    fld.setModId(regId);
                    fld.setModDt(regDt);
                    formApplyExtractMapper.updateFormApplFld(fld);
                } else {
                    newFldList.add(fld);
                }
            }
            if (!newFldList.isEmpty()) {
                long nextFldId = formApplyExtractMapper.selectNextFldId();
                for (FormApplFldVO fld : newFldList) {
                    fld.setFldId(nextFldId);
                    fld.setFldCd("f" + String.format("%06d", nextFldId));
                    nextFldId++;
                }
                formApplyExtractMapper.insertFormApplFldAiSuggest(applId, newFldList, regId, regDt);
            }
        }

        if (fldList != null) {
            for (FormApplFldVO fld : fldList) {
                fld.setApplId(applId);
            }
        }

        if (groupList != null && !groupList.isEmpty()) {
            saveFldGroupList(applId, groupList, regId, regDt, activeImgId);
        }

        if (mgrList != null) {
            saveFormApplMgrList(applId, mgrList, regId, regDt);
        }

        if (justSubmitted) {
            saveApprvlLineFromMgrList(applId, regId, regDt);
        }

        AiSuggestPreviewResultVO fldDetail = getFormApplFldDetail(applId);

        FormApplTempSaveResultVO result = new FormApplTempSaveResultVO();
        result.setApplId(applId);
        result.setFldList(fldDetail.getFldList());
        result.setGroupList(fldDetail.getGroupList());
        result.setDescGroupList(fldDetail.getDescGroupList());
        result.setWriteCntnList(formApplyExtractMapper.selectFormApplWriteCntnList(applId));
        result.setFileList(formApplyExtractMapper.selectFormApplFileList(applId));
        result.setFormImgList(formApplyExtractMapper.selectFormApplImgList(applId));
        result.setActiveImgId(activeImgId);
        result.setMgrList(formApplyExtractMapper.selectFormApplMgrList(applId));
        FormApplDetailVO formApplDetail = formApplyExtractMapper.selectFormApplDetail(applId);
        result.setProcStatCd(formApplDetail != null ? formApplDetail.getProcStatCd() : null);
        return result;
    }

    // 담당자(직원선택 결과)는 매번 전체 교체한다:...
    private void saveFormApplMgrList(long applId, List<FormApplMgrVO> mgrList, String regId, String regDt) {
        formApplyExtractMapper.deleteFormApplMgrByApplId(applId);
        if (mgrList.isEmpty()) {
            return;
        }
        formApplyExtractMapper.insertFormApplMgrList(applId, mgrList, regId, regDt);
    }

    // '신청' 확정 시점
    private void saveApprvlLineFromMgrList(long applId, String regid, String regDt) {
        formApplyExtractMapper.deleteApprvlLineByApplId(applId);

        List<FormApplMgrVO> savedMgrList = formApplyExtractMapper.selectFormApplMgrList(applId);
        if (savedMgrList.isEmpty()) {
            return;
        }

        List<ApprvlLineVO> apprvlLineList = new ArrayList<>();
        for (FormApplMgrVO mgr : savedMgrList) {
            ApprvlLineVO line = new ApprvlLineVO();
            line.setApplTypeCd("FORM");
            line.setApprvlSeq(mgr.getMgrSeq());
            line.setApprvrUserId(mgr.getMgrUserId());
            line.setApprvlStatCd("WAIT");
            apprvlLineList.add(line);
        }
        formApplyExtractMapper.insertApprvlLineList(applId, apprvlLineList, regid, regDt);
    }

    public List<FormApplMgrVO> getFormApplMgrList(long applId) {
        return formApplyExtractMapper.selectFormApplMgrList(applId);
    }

    public List<FormApplWriteCntnVO> getFormApplWriteCntnList(long applId) {
        return formApplyExtractMapper.selectFormApplWriteCntnList(applId);
    }

    private void saveFldGroupList(long applId, List<FormApplFldGroupVO> groupList, String regId, String regDt,
            Long activeImgId) {
        for (FormApplFldGroupVO group : groupList) {
            long groupFldId = group.getGroupFldId();
            String srcTypeCd = (group.getSrcTypeCd() == null || group.getSrcTypeCd().isEmpty())
                    ? "ITEM" : group.getSrcTypeCd();

            if (groupFldId <= 0) {
                groupFldId = formApplyExtractMapper.selectNextFldId();
                FormApplFldVO groupHeader = new FormApplFldVO();
                groupHeader.setApplId(applId);
                groupHeader.setFldId(groupFldId);
                groupHeader.setFldCd("f" + String.format("%06d", groupFldId));
                groupHeader.setImgId(activeImgId);
                groupHeader.setFldTypeCd("GROUP");
                groupHeader.setSrcTypeCd(srcTypeCd);
                groupHeader.setFldNm(group.getGroupNm());
                groupHeader.setReqYn("N");
                List<FormApplFldVO> headerOnly = new ArrayList<>();
                headerOnly.add(groupHeader);
                formApplyExtractMapper.insertFormApplFldAiSuggest(applId, headerOnly, regId, regDt);
            }

            List<FormApplFldVO> newCells = new ArrayList<>();
            for (List<FormApplFldVO> row : group.getRows()) {
                for (FormApplFldVO cell : row) {
                    if (cell.getImgId() == null && activeImgId != null) {
                        cell.setImgId(activeImgId);
                    }
                    cell.setApplId(applId);
                    cell.setUpFldId(groupFldId);
                    cell.setFldTypeCd("GROUP_ITEM");
                    cell.setSrcTypeCd(srcTypeCd);
                    if (cell.getReqYn() == null || cell.getReqYn().isEmpty()) {
                        cell.setReqYn("N");
                    }
                    if (cell.getFldId() > 0) {
                        cell.setModId(regId);
                        cell.setModDt(regDt);
                        formApplyExtractMapper.updateFormApplFld(cell);
                    } else {
                        newCells.add(cell);
                    }
                }
            }

            if (!newCells.isEmpty()) {
                long nextFld = formApplyExtractMapper.selectNextFldId();
                for (FormApplFldVO cell : newCells) {
                    cell.setFldId(nextFld);
                    cell.setFldCd("f" + String.format("%06d", nextFld));
                    nextFld++;
                }
                formApplyExtractMapper.insertFormApplFldAiSuggest(applId, newCells, regId, regDt);
            }
        }
    }

    /**
     * 탭 구분값(ITEM/DESC) 목록
     */
    private void saveAttachFiles(long applId, List<MultipartFile> attachFiles, List<String> attachSrcTypeCdList,
            String regId, String regDt) {

        Path attachDir = Paths.get(fileBasePath, DateUtil.getCurrentDate());

        try {
            Files.createDirectories(attachDir);
        } catch (IOException e) {
            throw new IllegalStateException("첨부파일 저장 경로를 생성할 수 없습니다: " + attachDir, e);
        }

        for (int i = 0; i < attachFiles.size(); i++) {
            MultipartFile file = attachFiles.get(i);
            if (file.isEmpty()) {
                continue;
            }

            String originalNm = Paths.get(file.getOriginalFilename()).getFileName().toString();
            String saveNm = UUID.randomUUID().toString();
            Path targetPath = attachDir.resolve(saveNm);

            try {
                file.transferTo(targetPath);
            } catch (IOException e) {
                throw new IllegalStateException("첨부파일 저장에 실패했습니다: " + originalNm, e);
            }

            String srcTypeCd = (attachSrcTypeCdList != null && i < attachSrcTypeCdList.size()
                && attachSrcTypeCdList.get(i) != null && !attachSrcTypeCdList.get(i).isEmpty())
                ? attachSrcTypeCdList.get(i) : "ITEM";

            FormApplFileVO fileVo = new FormApplFileVO();
            fileVo.setApplId(applId);
            fileVo.setSrcTypeCd(srcTypeCd);
            fileVo.setFileNm(originalNm);
            fileVo.setFilePath(attachDir.resolve(saveNm).toString());
            fileVo.setFileSize(file.getSize());
            fileVo.setRegId(regId);
            fileVo.setRegDt(regDt);
            formApplyExtractMapper.insertFormApplFile(fileVo);
        }
    }

    private Long saveFormImgFiles(long applId, List<MultipartFile> formImgFiles, List<FormApplImgMetaVO> metaList,
        String regId, String regDt) {

        Path attachDir = Paths.get(fileBasePath, DateUtil.getCurrentDate());

        try {
            Files.createDirectories(attachDir);
        } catch (IOException e) {
            throw new IllegalStateException("서식목록 이미지 저장 경로를 생성할 수 없습니다: " + attachDir, e);
        }

        Long activeImgId = null;
        for (int i = 0; i < formImgFiles.size(); i++) {
            MultipartFile file = formImgFiles.get(i);
            if (file.isEmpty()) {
                continue;
            }

            FormApplImgMetaVO meta = (metaList != null && metaList.size() > i) ? metaList.get(i) : null;
            String imgTypeCd = (meta != null && meta.getImgTypeCd() != null && !meta.getImgTypeCd().isEmpty())
                    ? meta.getImgTypeCd()
                    : (i == 0 ? "ORIGIN" : "SIMILAR");
            int sortOrd = meta != null ? meta.getSortOrd() : i;

            String originalNm = Paths.get(file.getOriginalFilename()).getFileName().toString();
            String saveNm = UUID.randomUUID().toString();
            Path targetPath = attachDir.resolve(saveNm);

            try {
                file.transferTo(targetPath);
            } catch (IOException e) {
                throw new IllegalStateException("시식목록 이미지 저장에 실패했습니다: " + originalNm, e);
            }

            FormApplImgVO img = new FormApplImgVO();
            img.setApplId(applId);
            img.setFileNm(originalNm);
            img.setFilePath(attachDir.resolve(saveNm).toString());
            img.setImgTypeCd(imgTypeCd);
            img.setSortOrd(sortOrd);
            img.setRegId(regId);
            img.setRegDt(regDt);
            formApplyExtractMapper.insertFormApplImg(img);

            if (meta != null && meta.isActive()) {
                activeImgId = img.getImgId();
            }
        }
        return activeImgId;
    }

    public FormApplFileVO getFormApplFile(long fileId) {
        return formApplyExtractMapper.selectFormApplFile(fileId);
    }

    /**
     * 첨부파일삭제
     */
    public void deleteFormApplFile(long fileId, long applId) {
        FormApplFileVO file = formApplyExtractMapper.selectFormApplFile(fileId);
        if (file == null || file.getApplId() != applId) {
            return;
        }

        int deletedCount = formApplyExtractMapper.deleteFormApplFile(fileId, applId);
        if (deletedCount == 0) {
            return;
        }

        try {
            Files.deleteIfExists(Paths.get(file.getFilePath()));
        } catch (IOException e) {
            throw new IllegalStateException("첨부파일 삭제에 실패했습니다: " + file.getFilePath(), e);
        }
    }

    public void deleteFormApplImg(long imgId, long applId) {
        FormApplImgVO img = formApplyExtractMapper.selectFormApplImg(imgId);
        if (img == null || img.getApplId() != applId) {
            return;
        }

        int deletedCount = formApplyExtractMapper.deleteFormApplImg(imgId, applId);
        if (deletedCount == 0) {
            return;
        }

        try {
            Files.deleteIfExists(Paths.get(img.getFilePath()));
        } catch (IOException e) {
            throw new IllegalStateException("서식목록 이미지 삭제에 ㄴ실패했습니다: " + img.getFilePath(), e);
        }
    }

    /**
     * 서식목록 삭제
     */
    public FormApplImgDeleteResultVO deleteFormApplImgList(List<Long> imgIdList, long applId) {
        List<FormApplImgVO> imgsToDelete = new ArrayList<>();
        for (Long imgId : imgIdList) {
            FormApplImgVO img = formApplyExtractMapper.selectFormApplImg(imgId);
            if (img == null || img.getApplId() != applId) {
                continue;
            }
            imgsToDelete.add(img);
        }

        for (FormApplImgVO img : imgsToDelete) {
            formApplyExtractMapper.deleteFormApplFldByImgId(img.getImgId(), applId);
            formApplyExtractMapper.deleteFormApplImg(img.getImgId(), applId);
        }

        try {
            for (FormApplImgVO img : imgsToDelete) {
                Files.deleteIfExists(Paths.get(img.getFilePath()));
            }
        } catch (IOException e) {
            throw new IllegalStateException("서식목록 이미지 삭제에 실패했습니다: " + applId, e);
        }

        FormApplImgDeleteResultVO result = new FormApplImgDeleteResultVO();
        if (formApplyExtractMapper.selectFormApplImgCount(applId) == 0) {
            deleteFormApplAll(applId);
            result.setApplDeleted(true);
            return result;
        }

        AiSuggestPreviewResultVO fldDetail = getFormApplFldDetail(applId);
        result.setApplDeleted(false);
        result.setFormImgList(formApplyExtractMapper.selectFormApplImgList(applId));
        result.setFldList(fldDetail.getFldList());
        result.setGroupList(fldDetail.getGroupList());
        return result;
    }

    public void deleteFormApplAll(long applId) {
        List<FormApplImgVO> imgList = formApplyExtractMapper.selectFormApplImgList(applId);
        List<FormApplFileVO> fileList = formApplyExtractMapper.selectFormApplFileList(applId);

        formApplyExtractMapper.deleteFormApplFldByApplId(applId);
        formApplyExtractMapper.deleteFormApplImgByApplId(applId);
        formApplyExtractMapper.deleteFormApplFileByApplId(applId);
        formApplyExtractMapper.deleteFormApplWriteCntnByApplId(applId);
        formApplyExtractMapper.deleteApprvlLineByApplId(applId);
        formApplyExtractMapper.deleteFormAppl(applId);

        try {
            for (FormApplImgVO img : imgList) {
                Files.deleteIfExists(Paths.get(img.getFilePath()));
            }
            for (FormApplFileVO file : fileList) {
                Files.deleteIfExists(Paths.get(file.getFilePath()));
            }
        } catch (IOException e) {
            throw new IllegalStateException("서식신청 파일 삭제 실패했습니다: " + applId, e);
        }
    }

    public List<StaffVO> searchStaffByName(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of();
        }
        return formApplyExtractMapper.selectStaffListByName(keyword.trim());
    }

    /**
     * 신청 상태(APPL)인 건만 취소(CANC)로 변경한다.
     */
    public boolean cancelFormAppl(long applId, String regId) {
        return formApplyExtractMapper.cancelFormAppl(applId, regId) > 0;
    }

    /**
     * 추출설명 규칙 등록 모달 저장
     */
    public  boolean saveFormApplFldRule(long applId, FormApplFldVO rule, String regId) {
        if (!"Y".equals(rule.getReqYn())) {
            rule.setReqYn("N");
        }
        if (formApplyExtractMapper.updateFormApplFldRule(applId, rule, regId) == 0) {
            return false;
        }
        formApplyExtractMapper.deleteFormApplFldMatchByFldId(rule.getFldId());
        saveFldMatchList(rule, regId);
        return true;
    }

    /**
     * 추출설명 메타 정보 모달 저장
     */
    public  boolean saveFormApplFldMeta(long applId, FormApplFldVO meta, String regId) {
        return formApplyExtractMapper.updateFormApplFldMeta(applId, meta, regId) > 0;
    }

    /**
     * 일치 Input 항목을 목록 순서대로 저장
     */
    private void saveFldMatchList(FormApplFldVO fld, String regId) {
        List<String> matchFldIdList = fld.getMatchFldIdList();
        if (matchFldIdList == null || matchFldIdList.isEmpty()) {
            return;
        }
        formApplyExtractMapper.insertFormApplFldMatchList(fld.getFldId(), matchFldIdList, regId);
    }

    /**
     * 추출설명 규칙 정보 - 일치 Input 항목 조회
     */
    public List<CodeDtlVO> getRuleMatchCdList() {
        return formApplyExtractMapper.selectRuleMatchCdList();
    }

    public AiSuggestPreviewResultVO deleteFormApplFldByImg(long applId, long imgId) {
        formApplyExtractMapper.deleteFormApplFldMatchByImgId(imgId, applId);
        formApplyExtractMapper.deleteFormApplFldByImgId(imgId, applId);
        return  getFormApplFldDetail(applId);
    }
}
