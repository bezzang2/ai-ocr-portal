package com.agilesoda.sample;

import io.swagger.v3.oas.annotations.Operation;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import com.agilesoda.common.response.entity.RestResponseEntity;
import com.agilesoda.common.rest.RestAgentProvider;
import com.agilesoda.common.util.DateUtil;
import com.agilesoda.common.util.StringUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.sf.json.JSONObject;
import org.apache.tomcat.util.json.JSONParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import com.agilesoda.common.entity.req.MultipartFileEntity;
import com.agilesoda.common.response.BaseResponse;
import com.agilesoda.common.util.CommonUtil;
import com.agilesoda.common.util.FileUtil;
import com.agilesoda.sample.dto.UserResDto;
import com.agilesoda.sample.entity.BoardAttachEntity;
import com.agilesoda.sample.entity.BoardEntity;
import com.agilesoda.sample.request.LoginRequest;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/test")
@Tag(name = "테스트")
public class SampleApiController {
	
	@Value("${aiocr.file.path}")
	private String filePath;

	private final SampleService sampleService;

	private final RestAgentProvider restAgentProvider;
	
	@Operation(summary = "샘플 사용자 로그인")
	@PostMapping("/login")
	public ResponseEntity<?> login(HttpServletRequest request, HttpServletResponse response, @RequestBody @Valid LoginRequest req) throws Exception{
		log.info("[login] 시작");

		log.info("[login] 요청 userId={}", req.getUserId());

		Optional<UserResDto> opUserEntity = sampleService.getUserInfo(req.getUserId());

		if(opUserEntity.isPresent()) {
			UserResDto userDto = opUserEntity.get();
			if(req.getPasswd().equals(userDto.getUserPwd())) {
				request.getSession().setAttribute("user", userDto);
				return BaseResponse.ok("성공", userDto);
			}
			else {
				return BaseResponse.error("비밀번호가 일치하지 않습니다.");
			}
		}

		return BaseResponse.error("사용자 정보가 없습니다.");
    }
	
	@Operation(summary = "테스트 파일 업로드")
	@PostMapping("/upload-file")
    public ResponseEntity<?> upload(MultipartHttpServletRequest request,
    		HttpServletResponse response) throws Exception{
    	log.info("[upload] 시작");
    	
    	List<MultipartFile> files = request.getFiles("attach_file");
    	String subject = request.getParameter("subject");
    	String detail = request.getParameter("detail");
    	
    	log.debug("[upload] 요청 subjectLen={} detailLen={} fileCount={}", subject == null ? 0 : subject.length(), detail == null ? 0 : detail.length(), files == null ? 0 : files.size());
    	
    	UserResDto user = CommonUtil.getUserEntity(request);
    	if( user == null ) {
    		return BaseResponse.error("로그인 후 사용가능합니다.");
    	}
    	BoardEntity boardEntity = new BoardEntity();
    	boardEntity.setSubject(subject);
    	boardEntity.setDetail(detail);
    	boardEntity.setWriter(user.getUserNm());
    	boardEntity.setRegId(user.getUserId());
    	boardEntity.setModId(user.getUserId());
    	long boardSeq = sampleService.registBoard(boardEntity);
    	
    	List<BoardAttachEntity> reqData = new ArrayList<>();
    	try {
    		for(MultipartFile file : files) {
    			MultipartFileEntity attachFile = FileUtil.saveMultipartFile(file, filePath);
    			
    			BoardAttachEntity bae = new BoardAttachEntity();
    			bae.setFileNm(attachFile.getFileNm());
    			bae.setOriginFileNm(attachFile.getOriginFileNm());
    			bae.setExt(attachFile.getExt());
    			bae.setPath(attachFile.getPath());
    			bae.setBoardSeq(boardSeq);
    			bae.setModId(user.getUserId());
    			bae.setRegId(user.getUserId());
    			reqData.add(bae);
    			log.debug("[upload] 파일 저장 boardSeq={} fileNm={} ext={}", boardSeq, attachFile.getFileNm(), attachFile.getExt());
    		}
    		
    		int cnt = sampleService.insertAttachFiles(reqData);
    		log.debug("[upload] 첨부파일 DB 등록 boardSeq={} count={}", boardSeq, cnt);
    	}
		catch(IOException e) {
			log.error("[upload] 첨부파일 저장 실패 boardSeq={} userId={}", boardSeq, user.getUserId(), e);
		}
    	
    	return BaseResponse.ok("성공", "업로드 되었습니다");
    }

	@Operation(summary = "테스트 워크플로 목록 조회")
	@GetMapping("/workflows")
	public ResponseEntity<?> workflows(@RequestParam("status") String status) throws Exception{
		log.info("[workflows] 시작");
		log.debug("status = {}", status);

		if(!"active".equals(status) && !"all".equals(status) && !"draft".equals(status)){
			return BaseResponse.error(HttpStatus.BAD_REQUEST, "허용되지 않는 상태 값입니다.(active, all, draft)");
		}

		JSONObject jobj = new JSONObject();
		jobj.put("status", "active");
		jobj.put("page", 1);
		jobj.put("page_size", 20);

		String uri = "/api/v1/ocr/workflow/list";
		RestResponseEntity<?> resData = restAgentProvider.get(uri, jobj);

		//	성공시
		if(resData.getStatus() == HttpStatus.OK.value()){
			log.debug("res data => {}", resData.getData());
		}

		log.info("[workflows] 종료");
		return BaseResponse.ok("성공", resData);
	}

	@Operation(summary = "테스트 워크플로 상세 조회")
	@GetMapping("/workflows/{workflow_id}")
	public ResponseEntity<?> workflow(@PathVariable("workflow_id") String workflowId) throws Exception{
		log.info("[workflow] 시작");
		log.debug("workflowId = {}", workflowId);

		if(!"019fb63d-1c2a-7f01-9c8e-5b1d0f9a2e44".equals(workflowId)){
			return BaseResponse.error(HttpStatus.NOT_FOUND, "해당 워크 스페이스는 존재하지 않습니다.");
		}

		Map<String, Object> item = new HashMap<>();
		item.put("workflow_id", "019fb63d-1c2a-7f01-9c8e-5b1d0f9a2e44");
		item.put("name", "사업자등록증 추출");
		item.put("description", "신규 거래처 등록용");
		item.put("doc_type", "사업자등록증");
		item.put("status", "active");
		item.put("active_release_id", "019fb63d-2f70-71aa-8d31-6c0b7e2f9a10");
		item.put("activated_at", DateUtil.getCurrentDateTime("yyyy-MM-dd hh:mm:ss"));

		log.info("[workflow] 종료");
		return BaseResponse.ok("성공", item);
	}

	@Operation(summary = "테스트 판독작업 생성")
	@PostMapping("/transactions")
	public ResponseEntity<?> transactions(@RequestBody Map<String, Object> param) throws Exception{
		log.info("[transactions] 시작");
		log.debug("[transactions] 요청 workflowId={}", param.get("workflow_id"));
		List<Map<String, String>> docs = (List<Map<String, String>>) param.get("documents");
		for(Map<String, String> doc : docs){
			log.debug("[transactions] 문서 filename={} format={}", doc.get("filename"), doc.get("format"));
			try(FileOutputStream fos = new FileOutputStream("d:/download/" + doc.get("filename"))){
				List<Map<String, Object>> attaches = (List<Map<String, Object>>)param.get("documents");
				byte[] fileBytes = Base64.getDecoder().decode(StringUtil.nvl(doc.get("data")));
				fos.write(fileBytes);
			}
			catch(Exception e){
				log.error("[transactions] 샘플 문서 저장 실패 filename={}", doc.get("filename"), e);
			}
		}

		Map<String, Object> resData = new HashMap<>();

		UUID randomUUID = UUID.randomUUID();

		resData.put("transaction_id", randomUUID);
		resData.put("status", "running");
		log.info("[transactions] 종료");
		return BaseResponse.ok(HttpStatus.ACCEPTED, "성공", resData);
	}

	@Operation(summary = "테스트 판독작업 상태 조회")
	@GetMapping("/transactions/{txn_id}")
	public ResponseEntity<?> transStatus(@PathVariable("txn_id") String trxId) throws Exception{
		log.info("[transStatus] 시작");
		log.debug("trxId = {}", trxId);

		int status = sampleService.selectTrxStatus(trxId);

		Map<String, Object> resData = new HashMap<>();
		resData.put("transaction_id", trxId);
		switch(status){
			case 1: resData.put("status", "running"); break;
			case 2: resData.put("status", "completed"); break;
			case 3: resData.put("status", "partially_completed"); break;
			case 4: resData.put("status", "failed"); break;
			case 5: resData.put("status", "canceled"); break;
		}

		resData.put("transaction_id", trxId);
		resData.put("workflow_id", "019fb63d-1c2a-7f01-9c8e-5b1d0f9a2e45");
		resData.put("workflow_version_id", "019fb63d-2f70-71aa-8d31-6c0b7e2f9a10");
		resData.put("validation_status", "pass");
		resData.put("file_count", 2);

		Map<String, Object> counts = new HashMap<>();
		counts.put("succeeded", 2);
		counts.put("failed", 0);
		counts.put("canceled", 0);
		counts.put("pass", 2);
		counts.put("needs_review", 0);
		counts.put("rejected", 0);
		counts.put("corrected", 0);
		counts.put("rejected_final", 0);
		resData.put("counts", counts);

		resData.put("plugin_status", "done");
		resData.put("allback_ready", true);
		resData.put("started_at", DateUtil.getCurrentDateTime("yyyy-MM-dd hh:mm:ss"));
		resData.put("finished_at", DateUtil.getCurrentDateTime("yyyy-MM-dd hh:mm:ss"));
		resData.put("created_at", DateUtil.getCurrentDateTime("yyyy-MM-dd hh:mm:ss"));
		resData.put("updated_at", DateUtil.getCurrentDateTime("yyyy-MM-dd hh:mm:ss"));
		resData.put("idempotency_key", null);
		resData.put("webhook_ref", null);

		log.info("[transStatus] 종료");
		return BaseResponse.ok(HttpStatus.ACCEPTED, "성공", resData);
	}

	@Operation(summary = "테스트 판독결과 조회")
	@GetMapping("/transactions/{txn_id}/result")
	public ResponseEntity<?> transResult(@PathVariable("txn_id") String trxId) throws Exception{
		log.info("[transResult] 시작");
		log.debug("trxId = {}", trxId);

		Map<String, Object> resData = null;

		ObjectMapper om = new ObjectMapper();
		ClassPathResource resource = new ClassPathResource("result.json");

		try(InputStream is = resource.getInputStream()){
			resData = om.readValue(is, Map.class);
		}

		log.info("[transResult] 종료");
		return BaseResponse.ok(HttpStatus.ACCEPTED, "성공", resData);
	}
}

