package com.agilesoda.module.apply.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.agilesoda.module.apply.entity.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agilesoda.common.entity.req.MultipartFileEntity;
import com.agilesoda.common.response.entity.RestResponseEntity;
import com.agilesoda.common.rest.RestAgentProvider;
import com.agilesoda.common.util.StringUtil;
import com.agilesoda.module.admin.vo.SsoLoginUserVO;
import com.agilesoda.module.apply.mapper.OcrTaskApplyMapper;
import com.agilesoda.module.apply.vo.RuleGroupInfo;
import com.agilesoda.module.apply.vo.RuleGroupWorkflow;
import com.agilesoda.module.apply.vo.TaskReqEntity;
import com.agilesoda.module.common.CommonService;
import com.agilesoda.module.common.entity.OcuocAprvlEntity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcrTaskApplyService {

    @Value("${aiocr.agent.apis.wkflow-list}")
    private String wkflowListUrl;

    @Value("${aiocr.agent.apis.wkflow-dtl}")
    private String wkflowDtlUrl;
    
    @Value("${aiocr.agent.apis.wkflow-merge}")
    private String wkflowMergeUrl;

    private final RestAgentProvider restProvider;
    
    private final OcrTaskApplyMapper ocrTaskApplyMapper;

    private final CommonService commonService;

    public int selectTaskCnt(TaskInfoReqEntity taskInfoReqEntity) throws Exception{
        return ocrTaskApplyMapper.selectTaskCnt(taskInfoReqEntity);
    }

    public List<TaskInfoResEntity> getTaskList(TaskInfoReqEntity taskInfoReqEntity) throws Exception{
        return ocrTaskApplyMapper.selectListTask(taskInfoReqEntity);
    }
    
    public Workbook makeExcel(TaskInfoReqEntity taskInfoReqEntity) throws Exception{
    	List<TaskInfoResEntity> list = ocrTaskApplyMapper.selectListTask(taskInfoReqEntity);
    	
    	//	1. Workbook 생성
    	Workbook workbook = new XSSFWorkbook();
    	Sheet sheet = workbook.createSheet("업무목록");
    	int rowNo = 0;
    	
    	//	2. 헤더 생성
    	Row headerRow = sheet.createRow(rowNo++);
    	headerRow.createCell(0).setCellValue("No.");
    	headerRow.createCell(1).setCellValue("업무명");
    	headerRow.createCell(2).setCellValue("업무ID");
    	headerRow.createCell(3).setCellValue("설명");
    	headerRow.createCell(4).setCellValue("업무분류");
    	headerRow.createCell(5).setCellValue("신청부서");
    	headerRow.createCell(6).setCellValue("처리상태");
    	headerRow.createCell(7).setCellValue("신청일자");
    	headerRow.createCell(8).setCellValue("최종변경일자");
    	
    	CellStyle dateStyle = workbook.createCellStyle();
    	CreationHelper creationHelper = workbook.getCreationHelper();
    	dateStyle.setDataFormat(creationHelper.createDataFormat().getFormat("yyyy-MM-dd HH:mm:ss"));
    	
    	for(TaskInfoResEntity entity : list) {
        	Row row = sheet.createRow(rowNo++);
        	row.createCell(0).setCellValue(entity.getIdx());
        	row.createCell(1).setCellValue(entity.getWkflowNm());
        	row.createCell(2).setCellValue(entity.getWkflowId());
        	row.createCell(3).setCellValue(entity.getWkflowDesc());
        	row.createCell(4).setCellValue(entity.getBzwkNm() + "(" + entity.getBzwkCd() + ")");
        	row.createCell(5).setCellValue(entity.getBrNm());
        	row.createCell(6).setCellValue(entity.getPrgsStNm() + "(" + entity.getPrgsStCd() + ")");
        	Cell regDtCell = row.createCell(7);
        	regDtCell.setCellValue(entity.getRegDt());
        	regDtCell.setCellStyle(dateStyle);
        	
        	Cell modDtCell = row.createCell(8);
        	modDtCell.setCellValue(entity.getModDt());
        	modDtCell.setCellStyle(dateStyle);
    	}
    	
    	return workbook;
    }

    /**
     *
     * @param status 워크플로우 상태값(default: active)
     * @param pageNo 현재 페이지 (default : 1)
     * @param pageSize 페이지 사이즈 (default : 20)
     * @param tag 서식 분류값(업무: 빈값, 서식: FORM)
     * @return
     * @throws Exception
     */
    public RestResponseEntity<?> getWorkflowList(String status, int pageNo, int pageSize, String tag) throws Exception{
        log.info("######################### getWorkflowList START ###################");
        //TODO 향후 서식/업무 등록이 DB에 적재되면 DB 조회로 변경해야 함.
        JSONObject param = new JSONObject();
        param.put("status", "".equals(status) ? "active" : status);
        param.put("page", pageNo == 0 ? 1 : pageNo);
        param.put("page_size", pageSize == 0 ? 20 : pageSize);
        param.put("tag", tag);

        return restProvider.get(wkflowListUrl, param);
    }
    
    /**
     * 서식 상세 조회
     * @param wkflowId
     * @return
     * @throws Exception
     */
    public RestResponseEntity<?> getWorkflow(String wkflowId) throws Exception{
        log.info("######################### getWorkflow START ###################");
        String tempUrl = String.format(wkflowDtlUrl, wkflowId);
        return restProvider.get(tempUrl);
    }

    /**
     * 배포 완료된 업무 목록 조회
     * @return
     * @throws Exception
     */
    public List<TaskListResEntity> selectListTaskWkflow(String keyword) throws Exception{
    	return ocrTaskApplyMapper.selectListTaskWkflow(keyword);
    }

    @SuppressWarnings("unchecked")
	@Transactional
    public int registTaskProc(Map<String, Object> reqData) throws Exception{
        log.info("######################### registTaskProc START ###################");
        
        SsoLoginUserVO userVo = (SsoLoginUserVO) reqData.get("user_info");

        //  1. 메인테이블에 등록한다.(OCUOC_BZWKA_M)
        OcuocBzwkaEntity ocuocBzwkaEntity = new OcuocBzwkaEntity();
        ocuocBzwkaEntity.setWkflowNm(StringUtil.nvl(reqData.get("bzwk_nm")));
        ocuocBzwkaEntity.setBzwkCd(StringUtil.nvl(reqData.get("bzwk_cd")));
        ocuocBzwkaEntity.setWkflowDesc(StringUtil.nvl(reqData.get("desc")));
        ocuocBzwkaEntity.setPrgsStCd(StringUtil.nvl(reqData.get("prgs_st_cd")));
        ocuocBzwkaEntity.setApctDprmCd(userVo.getDeptCode());
        ocuocBzwkaEntity.setUserId(userVo.getUserId());

        //TODO scrNo 설정해야 함.

        ocrTaskApplyMapper.registTask(ocuocBzwkaEntity);

        //  2. 첨부파일 등록
        List<MultipartFileEntity> attaches = (List<MultipartFileEntity>) reqData.get("attaches");
        for(MultipartFileEntity mfe : attaches){
            OcuocBzaafEntity ocuocBzaafEntity = new OcuocBzaafEntity();
            ocuocBzaafEntity.setFileNm(mfe.getFileNm());
            ocuocBzaafEntity.setAppcId(ocuocBzwkaEntity.getAppcId());
            ocuocBzaafEntity.setOrgFileNm(mfe.getOriginFileNm());
            ocuocBzaafEntity.setFilePath(mfe.getPath());
            ocuocBzaafEntity.setFileExt(mfe.getExt());
            ocuocBzaafEntity.setUserId(userVo.getUserId());
            ocrTaskApplyMapper.registAttachFile(ocuocBzaafEntity);
        }

        //  3. 서식 목록 테이블에 등록한다.(OCUOC_BZWKF_L)
        List<RuleGroupWorkflow> wkflowList = (List<RuleGroupWorkflow>)reqData.get("wkflow_list");
        for(RuleGroupWorkflow rgw : wkflowList) {
            OcuocBzwkfEntity ocuocBzwkfEntity = new OcuocBzwkfEntity();
            ocuocBzwkfEntity.setAppcId(ocuocBzwkaEntity.getAppcId());
            ocuocBzwkfEntity.setWkflowId(rgw.getId());
            ocuocBzwkfEntity.setWkflowNm(rgw.getName());
            ocuocBzwkfEntity.setUserId(userVo.getUserId());
			ocrTaskApplyMapper.registMappingTaskForm(ocuocBzwkfEntity);
        }

        //  4. 규칙 마스터 테이블 등록한다.(OCUOC_BZAFF_L)
        List<RuleGroupInfo> ruleGroupList = (List<RuleGroupInfo>)reqData.get("rule_groups");
        log.debug("ruleGroupList => {}", ruleGroupList);
        for(RuleGroupInfo rgi : ruleGroupList){
            OcuocBzaffEntity ocuocBzaffEntity = new OcuocBzaffEntity();
            ocuocBzaffEntity.setAppcId(ocuocBzwkaEntity.getAppcId());
            ocuocBzaffEntity.setRuleNm(rgi.getName());
            ocuocBzaffEntity.setRuleCd(rgi.getType());
            ocuocBzaffEntity.setUserId(userVo.getUserId());

            ocrTaskApplyMapper.registRule(ocuocBzaffEntity);

            //  4-1. 비교항목을 등록한다.(OCUOC_BZAFR_L)
            List<RuleGroupWorkflow> ruleGroupWkflows = rgi.getWkflows();
            for(RuleGroupWorkflow rgiw : ruleGroupWkflows){

                OcuocBzafrEntity ocuocBzafrEntity = new OcuocBzafrEntity();
                ocuocBzafrEntity.setRuleId(ocuocBzaffEntity.getRuleId());
                ocuocBzafrEntity.setWkflowId(StringUtil.nvl(rgiw.getId()));
                ocuocBzafrEntity.setWkflowNm(StringUtil.nvl(rgiw.getName()));
                ocuocBzafrEntity.setCmprField(StringUtil.nvl(rgiw.getValue()));
                ocuocBzafrEntity.setUserId(userVo.getUserId());

                ocrTaskApplyMapper.registRuleFieldMapping(ocuocBzafrEntity);
            }
        }
        //  5. 결재선 등록
        List<OcuocAprvlEntity> apprvList = new ArrayList<>();
        JSONArray apprvJarr = JSONArray.fromObject(StringUtil.nvl(reqData.get("approval_list")));
        for(int i=0 ; i<apprvJarr.size() ; i++){
            JSONObject temp = (JSONObject) apprvJarr.get(i);
            OcuocAprvlEntity entity = new OcuocAprvlEntity();
            entity.setAppcId(ocuocBzwkaEntity.getAppcId());
            entity.setAppcTypCd("TASK");
            entity.setAprvlSeqno(StringUtil.nvlInt(temp.get("seq"), 0));
            entity.setApofId(StringUtil.nvl(temp.get("empno")));
            entity.setUserId(userVo.getUserId());

            apprvList.add(entity);
        }
        commonService.registApproval(apprvList);

        //  6. 화면 번호 등록
        if(!"".equals(StringUtil.nvl(reqData.get("scn_no")))) {
            ocrTaskApplyMapper.registScnNO(OcuocBzscnEntity.builder()
                    .appcId(ocuocBzwkaEntity.getAppcId())
                    .wkflowId("temp-workflow-id")
                    .scnNo(StringUtil.nvl(reqData.get("scn_no")))
                    .userId(userVo.getUserId())
                    .build());
        }
        log.info("######################### registTaskProc END ###################");
        return 0;
    }
    
    @SuppressWarnings("unchecked")
	@Transactional
    public int registTaskProcTemp(Map<String, Object> reqData) throws Exception{
        log.info("######################### registTaskProcTemp START ###################");
        
        SsoLoginUserVO userVo = (SsoLoginUserVO) reqData.get("user_info");

        //  1. 메인테이블에 등록한다.(OCUOC_BZWKA_M)
        OcuocBzwkaEntity ocuocBzwkaEntity = new OcuocBzwkaEntity();
        ocuocBzwkaEntity.setWkflowNm(StringUtil.nvl(reqData.get("bzwk_nm")));
        ocuocBzwkaEntity.setBzwkCd(StringUtil.nvl(reqData.get("bzwk_cd")));
        ocuocBzwkaEntity.setWkflowDesc(StringUtil.nvl(reqData.get("desc")));
        ocuocBzwkaEntity.setApctDprmCd(userVo.getDeptCode());
        ocuocBzwkaEntity.setUserId(userVo.getUserId());

        ocrTaskApplyMapper.registTask(ocuocBzwkaEntity);

        //  rule_groups에서 workflow 추출
        List<RuleGroupInfo> ruleGroupList = (List<RuleGroupInfo>)reqData.get("rule_groups");
        log.debug("ruleGroupList => {}", ruleGroupList);
        
        //  3. 서식 목록 테이블에 등록한다.(OCUOC_BZWKF_L)
        List<String> sourceWorkflowIds = new ArrayList<>();
        for(RuleGroupInfo rgi : ruleGroupList) {
        	for(RuleGroupWorkflow rgw : rgi.getWkflows()) {
        		if(!sourceWorkflowIds.contains(rgw.getId())) {
        			sourceWorkflowIds.add(rgw.getId());
                    OcuocBzwkfEntity ocuocBzwkfEntity = new OcuocBzwkfEntity();
                    ocuocBzwkfEntity.setAppcId(ocuocBzwkaEntity.getAppcId());
                    ocuocBzwkfEntity.setWkflowId(rgw.getId());
                    ocuocBzwkfEntity.setWkflowNm(rgw.getName());
                    ocuocBzwkfEntity.setUserId(userVo.getUserId());
        			ocrTaskApplyMapper.registMappingTaskForm(ocuocBzwkfEntity);
                }
        	}
        }

        //  4. 규칙 마스터 테이블 등록한다.(OCUOC_BZAFF_L)
        for(RuleGroupInfo rgi : ruleGroupList){
            OcuocBzaffEntity ocuocBzaffEntity = new OcuocBzaffEntity();
            ocuocBzaffEntity.setAppcId(ocuocBzwkaEntity.getAppcId());
            ocuocBzaffEntity.setRuleNm(rgi.getName());
            ocuocBzaffEntity.setRuleCd(rgi.getType());
            ocuocBzaffEntity.setUserId(userVo.getUserId());

            ocrTaskApplyMapper.registRule(ocuocBzaffEntity);

            //  4-1. 비교항목을 등록한다.(OCUOC_BZAFR_L)
            List<RuleGroupWorkflow> ruleGroupWkflows = rgi.getWkflows();
            for(RuleGroupWorkflow rgiw : ruleGroupWkflows){

                OcuocBzafrEntity ocuocBzafrEntity = new OcuocBzafrEntity();
                ocuocBzafrEntity.setRuleId(ocuocBzaffEntity.getRuleId());
                ocuocBzafrEntity.setWkflowId(StringUtil.nvl(rgiw.getId()));
                ocuocBzafrEntity.setWkflowNm(StringUtil.nvl(rgiw.getName()));
                ocuocBzafrEntity.setCmprField(StringUtil.nvl(rgiw.getValue()));
                ocuocBzafrEntity.setUserId(userVo.getUserId());

                ocrTaskApplyMapper.registRuleFieldMapping(ocuocBzafrEntity);
            }
        }
        
        log.info("######################### registTaskProcTemp END ###################");
        return 0;
    }

    public TaskInfoResEntity selectTaskDetail(String appcId) throws Exception{
        log.info("######################### selectTaskDetail START ###################");

        return ocrTaskApplyMapper.selectTaskDetail(appcId);
    }

    public RestResponseEntity<?> requestMerge(TaskReqEntity tre) throws Exception{
        log.info("######################### requestMerge START ###################");
        JSONObject jobj = new JSONObject();
        jobj.put("name", tre.getName());
        jobj.put("project_id", tre.getProjectId());
        jobj.put("source_workflow_ids", JSONArray.fromObject(tre.getSourceWorkflowIds()));
        jobj.put("description", tre.getDescription());
        jobj.put("tag", tre.getTag());
        jobj.put("documents", JSONArray.fromObject(Arrays.asList()));
        log.debug("jobj => {}", jobj);
        return restProvider.post(wkflowMergeUrl, jobj);
    }

    public int updateWkflowId(String appcId, String userId, String workflowId) throws Exception{
        log.info("######################### updateWkflowId START ###################");
        Map<String, Object> reqData = Map.of(
                "appcId", appcId,
                "userId", userId,
                "wkflowId", workflowId
        );
        return ocrTaskApplyMapper.updateWkflowId(reqData);
    }
}
