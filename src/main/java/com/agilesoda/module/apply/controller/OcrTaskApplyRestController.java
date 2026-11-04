package com.agilesoda.module.apply.controller;


import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.agilesoda.common.entity.req.MultipartFileEntity;
import com.agilesoda.common.response.BaseResponse;
import com.agilesoda.common.response.entity.RestResponseEntity;
import com.agilesoda.common.util.FileUtil;
import com.agilesoda.common.util.PagingUtil;
import com.agilesoda.common.util.StringUtil;
import com.agilesoda.module.admin.vo.SsoLoginUserVO;
import com.agilesoda.module.apply.entity.OcuocBzwkfEntity;
import com.agilesoda.module.apply.entity.TaskInfoReqEntity;
import com.agilesoda.module.apply.entity.TaskInfoResEntity;
import com.agilesoda.module.apply.entity.TaskListResEntity;
import com.agilesoda.module.apply.service.OcrTaskApplyService;
import com.agilesoda.module.apply.vo.FieldInfo;
import com.agilesoda.module.apply.vo.RuleGroupInfo;
import com.agilesoda.module.apply.vo.RuleGroupWorkflow;
import com.agilesoda.module.apply.vo.TaskReqEntity;
import com.agilesoda.module.apply.vo.WorkflowDtlInfo;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.SchemaProperty;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


/****************************************************
 *   OCR업무신청 화면 호출 서비스를 관리하는 Controller
 *****************************************************/
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "업무 신청", description = "업무 목록·워크플로 신청과 반영")
public class OcrTaskApplyRestController {
	
	@Value("${aiocr.project-id}")
	private String projectId;
	@Value("${aiocr.file.path}")
	private String filePath;
	
    private final OcrTaskApplyService ocrTaskApplyService;

    @Operation(summary = "업무 목록 조회 API", description = "업무목록을 조회한다.")
    @PostMapping("/task/list")
    public ResponseEntity<?> ocrTaskList(HttpServletRequest request, HttpServletResponse response, @RequestBody TaskInfoReqEntity taskInfoReqEntity) throws Exception{
        log.info("######################## ocrTaskList START ####################");
        log.debug("taskInfoReqEntity => {}", taskInfoReqEntity);
        int totCnt = ocrTaskApplyService.selectTaskCnt(taskInfoReqEntity);

        if(totCnt <= taskInfoReqEntity.getPageSize()){
            taskInfoReqEntity.setStPos(0);
            taskInfoReqEntity.setEndPos(totCnt);
        }
        else{
            taskInfoReqEntity.setStPos( ( taskInfoReqEntity.getPageNo() - 1) * taskInfoReqEntity.getPageSize());
            taskInfoReqEntity.setEndPos(taskInfoReqEntity.getStPos() + taskInfoReqEntity.getPageSize());
        }

        List<TaskInfoResEntity> taskList = ocrTaskApplyService.getTaskList(taskInfoReqEntity);
        log.debug("resData => {}", taskList);

        PagingUtil page = new PagingUtil(taskInfoReqEntity.getPageNo(), totCnt, taskInfoReqEntity.getPageSize(), 10);

        Map<String, Object> resData = new HashMap<>();
        resData.put("data", taskList);
        resData.put("page", page);
        resData.put("param", taskInfoReqEntity);

        return BaseResponse.ok("성공", resData);
    }
    
    @Operation(summary = "업무 목록 excel 다운로드 API", description = "현재 구현은 엑셀 workbook을 생성한 뒤 JSON 성공 응답을 반환합니다. 파일 다운로드 응답은 아직 구현되지 않았습니다.")
    @PostMapping("/task/excel")
    public ResponseEntity<?> ocrTaskExcel(HttpServletRequest request, HttpServletResponse response, @RequestBody TaskInfoReqEntity taskInfoReqEntity) throws Exception{
        log.info("######################## ocrTaskExcel START ####################");
        log.debug("taskInfoReqEntity => {}", taskInfoReqEntity);
        int totCnt = ocrTaskApplyService.selectTaskCnt(taskInfoReqEntity);

        if(totCnt <= taskInfoReqEntity.getPageSize()){
            taskInfoReqEntity.setStPos(0);
            taskInfoReqEntity.setEndPos(totCnt);
        }
        else{
            taskInfoReqEntity.setStPos( ( taskInfoReqEntity.getPageNo() - 1) * taskInfoReqEntity.getPageSize());
            taskInfoReqEntity.setEndPos(taskInfoReqEntity.getStPos() + taskInfoReqEntity.getPageSize());
        }

        List<TaskInfoResEntity> taskList = ocrTaskApplyService.getTaskList(taskInfoReqEntity);
        log.debug("resData => {}", taskList);

        Workbook workbook = ocrTaskApplyService.makeExcel(taskInfoReqEntity);

        /*
        StreamingResponseBody responseBody = outputStream -> {
            workbook.write(outputStream);
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachement", new String("task.xlsx".getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1));
        log.info("################# resultDownload end ##########################");
        return new ResponseEntity<>(workbook.ResponseEntity.ok()
                .contentType()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"task.xlsx\"")
                .body(responseBody);
         */
        return BaseResponse.ok("성공", "");
    }

    @SuppressWarnings("unchecked")
	@Operation(summary = "서식/업무 목록 조회 API", description = "업무 신청 화면에서 서식 및 업무 목록을 조회할 때 사용한다.")
    @GetMapping("/wkflow/list")
    public ResponseEntity<?> ocrWkflowList(@RequestParam(name="tag", defaultValue="") String tag,
    									@RequestParam(name="keyword", defaultValue="") String keyword) throws Exception{
    	log.info("######################## ocrWkflowList START ####################");
    	log.debug("tag => {}, keyword => {}", tag, keyword);


        Map<String, Object> resData = new HashMap<>();

        if("FORM".equalsIgnoreCase(tag)) {
            RestResponseEntity<?> totList = ocrTaskApplyService.getWorkflowList("active", 1, 100, tag);
            log.debug("resData => {}", totList);

            if(totList.getStatus() != HttpStatus.OK.value()){
                return BaseResponse.error(HttpStatus.valueOf(totList.getStatus()), totList.getMessage());
            }

            //	서식/업무 목록 조회
            Map<String, Object> data = (Map<String, Object>)totList.getData();
            List<Map<String, Object>> items = (List<Map<String, Object>>)data.get("items");

	        List<Map<String, Object>> list = items.stream()
	        		.filter((item) -> "FORM".equals(StringUtil.nvl(item.get("tag"))))
	        		.toList();
	        
	        List<Map<String, Object>> forms = list.stream()
	        		.filter((item) -> StringUtil.nvl(item.get("name")).contains(keyword))
	        		.toList();
	        
	        resData.put("forms", forms);
        }
        else if("TASK".equalsIgnoreCase(tag)) {
	        List<TaskListResEntity> tasks = ocrTaskApplyService.selectListTaskWkflow(keyword);
	    	
	        resData.put("tasks", tasks);
        }
        else {
            RestResponseEntity<?> totList = ocrTaskApplyService.getWorkflowList("active", 1, 100, "FORM");
            log.debug("resData => {}", totList);

            if(totList.getStatus() != HttpStatus.OK.value()){
                return BaseResponse.error(HttpStatus.valueOf(totList.getStatus()), totList.getMessage());
            }

            //	서식/업무 목록 조회
            Map<String, Object> data = (Map<String, Object>)totList.getData();
            List<Map<String, Object>> items = (List<Map<String, Object>>)data.get("items");

	        List<Map<String, Object>> forms = items.stream()
	        		.filter((item) -> "FORM".equals(StringUtil.nvl(item.get("tag"))))
	        		.toList();
	        
	        resData.put("forms", forms);
	
	        List<TaskListResEntity> tasks = ocrTaskApplyService.selectListTaskWkflow(keyword);
	
	        resData.put("tasks", tasks);
        }
        return BaseResponse.ok("성공", resData);
    }

    @SuppressWarnings("unchecked")
	@Operation(summary = "AgenticOCR 엔진의 서식/업무 상세조회 요청 API", description = "AgenticOCR 엔진의 서식/업무 상세조회를 요청한다.")
	@GetMapping("/wkflow/{wkflowId}")
    public ResponseEntity<?> ocrWkflowDetail(@PathVariable("wkflowId") String wkflowId) throws Exception {
        log.info("######################## ocrWkflowDetail START ####################");
        
        log.debug("wkflowId = {}", wkflowId);
        
        //	1. 연계 에이전트의 /api/v1/ocr/workflow/{workflow_id} API 호출
        RestResponseEntity<?> resData = ocrTaskApplyService.getWorkflow(wkflowId);
        log.debug("result => {}", resData);
        if(resData.getStatus() != HttpStatus.OK.value()){
            return BaseResponse.error(HttpStatus.valueOf(resData.getStatus()), resData.getMessage());
        }

        log.debug("resData.getData => {}", resData.getData());
        
        //	2. 워크플로우 상세 정보 파싱
        Map<String, Object> data = (Map<String, Object>) resData.getData();
        
        //	3. 스텝 정보 조회
        List<Map<String, Object>> steps = (List<Map<String, Object>>)((Map<String, Object>)data.get("workflow_ir")).get("steps");
        
        log.debug("steps => {}", steps);
        
        //	4. extract step 필터링
        List<Map<String, Object>> extracts = steps.stream()
        		.filter( x -> StringUtil.nvl(x.get("step_id")).indexOf("extract") > -1)
        		.toList();

        log.debug("extracts => {}", extracts);
        
        //	5. 필드 정보 추가
        //  5-1. config 추출
        Map<String, Object> config = (Map<String, Object>)extracts.get(0).get("config");
        //  5-2. fields 추출
        List<Map<String, Object>> fields = (List<Map<String, Object>>)config.get("fields");

        List<FieldInfo> fieldInfos = new ArrayList<>();
        for(Map<String, Object> field : fields){
            FieldInfo fieldInfo = FieldInfo.builder()
                    .displayLabel(StringUtil.nvl(field.get("display_label")))
                    .path(StringUtil.nvl(field.get("path")))
                    .dtype(StringUtil.nvl(field.get("dtype")))
                    .required(StringUtil.nvlBool(field.get("required"), false))
                    .length(StringUtil.nvlInt(field.get("length"), -1))
                    .masking(StringUtil.nvlBool(field.get("masking"), false))
                    .maskingPattern(StringUtil.nvl(field.get("maskingPattern")))
                    .build();

            fieldInfos.add(fieldInfo);
        }

        WorkflowDtlInfo workflowDtlInfo = WorkflowDtlInfo.builder()
                .workflowId(StringUtil.nvl(data.get("workflow_id")))
                .workflowNm(StringUtil.nvl(data.get("name")))
                .tag(StringUtil.nvl(data.get("tag")))
                .fields(fieldInfos)
                .build();
        
        log.info("######################## ocrWkflowDetail END ####################");
        return BaseResponse.ok("성공", workflowDtlInfo);
    }
	
    @Operation(summary = "OCR 업무 신청",
            description = "SSO 로그인 세션으로 업무를 신청합니다. rule_groups·approval_list·wkflow_list는 JSON 문자열이며 첨부파일은 attach_file로 반복 전송합니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "multipart/form-data", schema = @Schema(type = "object"),
                            schemaProperties = {
                                    @SchemaProperty(name = "bzwk_nm", schema = @Schema(type = "string", description = "업무명")),
                                    @SchemaProperty(name = "bzwk_cd", schema = @Schema(type = "string", description = "신청부서 코드")),
                                    @SchemaProperty(name = "description", schema = @Schema(type = "string", description = "업무 설명")),
                                    @SchemaProperty(name = "prgs_st_cd", schema = @Schema(type = "string", description = "신청 진행 상태 코드")),
                                    @SchemaProperty(name = "rule_groups", schema = @Schema(type = "string", description = "규칙 그룹 JSON 배열 문자열", example = "[]")),
                                    @SchemaProperty(name = "approval_list", schema = @Schema(type = "string", description = "결재선 JSON 문자열", example = "[]")),
                                    @SchemaProperty(name = "wkflow_list", schema = @Schema(type = "string", description = "원본 워크플로 JSON 배열 문자열", example = "[]")),
                                    @SchemaProperty(name = "attach_file", array = @ArraySchema(schema = @Schema(type = "string", format = "binary")))
                            })))
	@PostMapping("/wkflow/apply")
    public ResponseEntity<?> registWkflow(MultipartHttpServletRequest request, HttpServletResponse response ) throws Exception{
    	log.info("######################## registWkflow START ####################");

    	String bzwkNm = request.getParameter("bzwk_nm");
    	String bzwkCd = request.getParameter("bzwk_cd");
        String scnNo = request.getParameter("scn_no");
    	String desc = request.getParameter("description");
        String prgsStCd = request.getParameter("prgs_st_cd");
    	String ruleGroups = request.getParameter("rule_groups");
        String approvalList = request.getParameter("approval_list");
        String wkflowList = request.getParameter("wkflow_list");   	
    	List<MultipartFile> files = request.getFiles("attach_file");

        List<MultipartFileEntity> attaches = new ArrayList<>();
    	if(files.size() > 0 ) {
            for (MultipartFile file : files) {
            	MultipartFileEntity attachFile = FileUtil.saveMultipartFile(file, filePath);
                String base64 = FileUtil.encodeBase64(attachFile, filePath);

                String fullPath = filePath + attachFile.getPath() + File.separator + attachFile.getFileNm() + "." + attachFile.getExt();
                String format = FileUtil.getFormat(new File(fullPath));

                if ("".equals(format)) {
                    log.error("포맷이 image, pdf가 아닙니다. [{}]", FileUtil.getMimeType(new File(fullPath)));
                    continue;
                }
                //attachFile.setFormat(format);
                log.debug("format = {}", format);

                //log.debug("recv file info => {}", attachFile);
                attaches.add(attachFile);
            }
    	}
    	
    	ObjectMapper om = new ObjectMapper();
    	
    	List<RuleGroupWorkflow> srcWkflowList = new ArrayList<>();
    	List<Map<String, Object>> tempList = om.readValue(wkflowList, List.class);
    	for(Map<String, Object> map : tempList) {
    		RuleGroupWorkflow rgi = om.convertValue(map, RuleGroupWorkflow.class);
        	srcWkflowList.add(rgi);
    	}
        SsoLoginUserVO ssoLoginUser = (SsoLoginUserVO) request.getSession().getAttribute("ssoLoginUser");
        
        List<RuleGroupInfo> ruleGroupList = new ArrayList<>();
        
        tempList = om.readValue(ruleGroups, List.class);
        for(Map<String, Object> map : tempList) {
        	RuleGroupInfo rgi = om.convertValue(map, RuleGroupInfo.class);
        	ruleGroupList.add(rgi);
        }

        Map<String, Object> reqData = new HashMap<>();
        reqData.put("bzwk_nm", bzwkNm);
        reqData.put("bzwk_cd", bzwkCd);
        reqData.put("scn_no", scnNo);
        reqData.put("desc", desc);
        reqData.put("prgs_st_cd", prgsStCd);
        reqData.put("rule_groups", ruleGroupList);
        reqData.put("approval_list", approvalList);
        reqData.put("wkflow_list", srcWkflowList);
        reqData.put("attach_files", files);
        reqData.put("user_info", ssoLoginUser);
        reqData.put("attaches", attaches);

        log.debug("reqData = > {}", reqData);

        ocrTaskApplyService.registTaskProc(reqData);

        //  rule_groups에서 workflows_ids 추출
        List<String> sourceWorkflowIds = new ArrayList<>();
        for(RuleGroupWorkflow rgw : srcWkflowList) {
        	sourceWorkflowIds.add(rgw.getId());
        }

        TaskReqEntity tre = TaskReqEntity.builder()
    			.name(bzwkNm)
    			.projectId(projectId)
    			.sourceWorkflowIds(sourceWorkflowIds)
    			.description(desc)
    			.tag("")
    			.build();
    	log.info("######################## registWkflow END ####################");
    	//return BaseResponse.ok("성공", tre);	
    	return BaseResponse.ok("성공", "");
    }
    
	@Operation(summary = "신청된 업무를 AgenticOCR로 merge 요청 API", description = "신청된 업무를 AgenticOCR로 merge 요청한다.")
	@GetMapping("/task/{appc_id}")
    public ResponseEntity<?> ocrRequestMerge(HttpServletRequest request, HttpServletResponse response, @PathVariable("appc_id") String appcId) throws Exception {
        log.info("######################## ocrRequestMerge START ####################");
        log.debug("appcId = {}", appcId);

        TaskInfoResEntity taskInfoEntity = ocrTaskApplyService.selectTaskDetail(appcId);
        log.debug("resData => {}", taskInfoEntity);
        
        List<String> wkflows = new ArrayList<>();

        for(OcuocBzwkfEntity ocuocBzwkfEntity : taskInfoEntity.getWkflows()){
            if(!wkflows.contains(ocuocBzwkfEntity.getWkflowId())){
                wkflows.add(ocuocBzwkfEntity.getWkflowId());
            }
        }

        TaskReqEntity tre = TaskReqEntity.builder()
                .name(StringUtil.nvl(taskInfoEntity.getWkflowNm()))
                .projectId(projectId)
                .sourceWorkflowIds(wkflows)
                .description(StringUtil.nvl(taskInfoEntity.getWkflowDesc()))
                .tag("")
                .build();

        SsoLoginUserVO ssoLoginUser = (SsoLoginUserVO) request.getSession().getAttribute("ssoLoginUser");

        RestResponseEntity<?> resData = ocrTaskApplyService.requestMerge(tre);
        log.debug("result => {}", resData);
        if(resData.getStatus() != HttpStatus.OK.value()){
            return BaseResponse.error(HttpStatus.valueOf(resData.getStatus()), resData.getMessage());
        }

        log.debug("resData.getData => {}", resData.getData());

        Map<String, Object> data = (Map<String, Object>) resData.getData();
        //ocrTaskApplyService.updateWkflowId(appcId, ssoLoginUser.getUserId(), StringUtil.nvl(data.get("workflow_id")));
        ocrTaskApplyService.updateWkflowId(appcId, "26W0068", StringUtil.nvl(data.get("workflow_id")));
        log.info("######################## ocrRequestMerge END ####################");
        return BaseResponse.ok("성공", taskInfoEntity);
    }
}

