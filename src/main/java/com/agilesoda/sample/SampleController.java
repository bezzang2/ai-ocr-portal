package com.agilesoda.sample;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.agilesoda.common.util.PagingUtil;
import com.agilesoda.sample.dto.BoardInqDto;
import com.agilesoda.sample.dto.BoardResDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/test")
@RequiredArgsConstructor
public class SampleController {
	
	@Value("${aiocr.file.path}")
	private String filePath;
	
	private final SampleService sampleService;
	
	@GetMapping("")
	public String index() {
		log.debug("[index] 시작");
		return "test/index";
	}

	@GetMapping("login")
	public String login(Model model) {
		model.addAttribute("title", "로그인");
		return "test/login";
	}
	
	@GetMapping("/file-upload")
	public String fileUpload(Model model) {
		model.addAttribute("title", "파일업로드");
		return "test/file-upload";
	}

	@GetMapping("board")
	public String board(@RequestParam(value="subject", defaultValue="") String subject,
			@RequestParam(value="writer", defaultValue="") String writer,
			@RequestParam(value="curPage", defaultValue="1") int curPage,
			@RequestParam(value="pagePerNum", defaultValue="3") int pagePerNum,
			Model model) throws Exception {
    	log.info("[board] 시작");
        log.debug("param => {}, {}, {}, {}", subject, writer, pagePerNum, curPage);

		model.addAttribute("title", "게시판");
		
	    //	조건에 맞는 전체 목록 개수 조회
        BoardInqDto boardInqRequest = BoardInqDto.builder()
        		.subject(subject)
        		.writer(writer)
        		.build();

        int totalCnt = sampleService.selectBoardCnt(boardInqRequest);
        
        //	페이징 처리
        PagingUtil page = new PagingUtil(curPage, totalCnt, pagePerNum, 10);
        log.debug("page => {}", page);
        
        //	mysql에서 LIMIT 처리를 위한 값 세팅
        boardInqRequest.setOffset(page.getStartRowIndex() - 1);
        boardInqRequest.setLimit(page.getPageSize());
        
        //	조건에 맞는 게시물 조회
        List<BoardResDto> list = sampleService.selectBoardList(boardInqRequest);
        log.debug("list => {}", list);
        
        model.addAttribute("page", page);
        model.addAttribute("list", list);
        
		return "test/board";
	}
}
