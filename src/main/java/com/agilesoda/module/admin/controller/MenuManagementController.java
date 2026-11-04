package com.agilesoda.module.admin.controller;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.agilesoda.module.admin.vo.MenuManagementVO;
import com.agilesoda.module.admin.vo.MenuManagementBtnVO;
import com.agilesoda.module.admin.service.MenuManagementService;
import com.agilesoda.module.admin.vo.SsoLoginUserVO;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/****************************************************
 *   메뉴관리 화면에 대한 컨트롤
 *****************************************************/

@Slf4j
@Controller
@RequestMapping("/module/admin")
@RequiredArgsConstructor
@Tag(name = "메뉴 관리", description = "메뉴와 버튼 등록·삭제")
public class MenuManagementController {
	
	
	private final MenuManagementService menuManagementService;

	/****************************************************
	 *   getMenuList 메뉴 리스트 조회
	 *****************************************************/
	@GetMapping("/menuManagement")
	public String getMenuList(@RequestParam(value="menuId", defaultValue="") String menuId,
							  Model model) throws Exception {
		log.debug("[menuManagement] 시작");

		// 모든 메뉴 조회
		List<MenuManagementVO> list = menuManagementService.getMenuList(menuId);
		model.addAttribute("list", list);

		return "module/admin/menuManagement";
	}

	/****************************************************
	 *   insertMenu 메뉴 리스트 저장
	 *****************************************************/
	@Operation(summary = "메뉴 저장")
	@PostMapping("/insert")
	@ResponseBody
	public Map<String, Object> insertMenu(@RequestBody MenuManagementVO vo,
										  HttpSession session){
		log.info("[관리자] 메뉴저장 시작 userId={}", SsoLoginUserVO.userIdOf(session));
		Map<String, Object> result = new HashMap<>();
		SsoLoginUserVO ssoLoginUserVO = (SsoLoginUserVO) session.getAttribute("ssoLoginUser");
		try {
			vo.setLastChngEmpno(ssoLoginUserVO.getUserId());
			menuManagementService.insertMenu(vo);
			result.put("result","SUCCESS");
		}catch(Exception e){
			log.error("[관리자] insertMenu 실패 userId={}", SsoLoginUserVO.userIdOf(session), e);
			result.put("result","FAIL");
			result.put("message","처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
		}
		return result;
	}

	/****************************************************
	 *   deleteMenu 메뉴 리스트 삭제
	 *****************************************************/
	@Operation(summary = "메뉴 삭제")
	@PostMapping("/delete")
	@ResponseBody
	public Map<String, Object> deleteMenu(@RequestBody MenuManagementVO vo){
		log.debug("[메뉴삭제] 시작");
		Map<String, Object> result = new HashMap<>();
		try {
			menuManagementService.deleteMenu(vo);
			result.put("result","SUCCESS");
		}catch(Exception e){
			log.error("[관리자] deleteMenu 실패", e);
			result.put("result","FAIL");
			result.put("message","처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
		}
		return result;
	}

	/****************************************************
	 *   selectBtnList 메뉴에 해당하는 버튼정의 리스트 조회
	 *****************************************************/
	@Operation(summary = "메뉴 버튼 목록 조회")
	@PostMapping("/selectBtnList")
	@ResponseBody
	public List<MenuManagementBtnVO> selectBtnList(@RequestBody MenuManagementBtnVO vo) {
		log.debug("[버튼정의 조회] 시작");

		return menuManagementService.selectBtnList(vo.getMenuId());
	}


	/****************************************************
	 *   insertBtnList 메뉴에 해당하는 버튼정의 리스트 저장
	 *****************************************************/
	@Operation(summary = "메뉴 버튼 저장")
	@PostMapping("/insertBtnList")
	@ResponseBody
	public Map<String, Object> insertBtnList(@RequestBody List<MenuManagementBtnVO> btnList,
											 HttpSession session){
		log.info("[관리자] 버튼정의 저장 시작 userId={}", SsoLoginUserVO.userIdOf(session));
		log.debug("[관리자] 버튼정의 저장 요청 count={}", btnList == null ? 0 : btnList.size());
		Map<String, Object> result = new HashMap<>();
		try{
			SsoLoginUserVO ssoLoginUserVO = (SsoLoginUserVO) session.getAttribute("ssoLoginUser");
			if(ssoLoginUserVO == null){
				throw new IllegalStateException("SSO 로그인 정보가 없습니다.");
			}
			for(MenuManagementBtnVO vo : btnList){
				vo.setLastChngEmpno(ssoLoginUserVO.getUserId());
			}
			menuManagementService.insertBtnList(btnList);
			result.put("result","SUCCESS");
		}catch(Exception e){
			log.error("[관리자] insertBtnList 실패 userId={}", SsoLoginUserVO.userIdOf(session), e);
			result.put("result","FAIL");
			result.put("message","처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
		}
		return result;
	}

	/****************************************************
	 *   deleteBtnList 메뉴에 해당하는 버튼정의 리스트 삭제
	 *****************************************************/
	@Operation(summary = "메뉴 버튼 삭제")
	@PostMapping("/deleteBtnList")
	@ResponseBody
	public Map<String, Object> deleteBtnList(@RequestBody MenuManagementBtnVO vo){
		log.debug("[버튼정의 삭제] 시작");
		Map<String, Object> result = new HashMap<>();
		try {
			menuManagementService.deleteBtnList(vo);
			result.put("result","SUCCESS");
		}catch(Exception e){
			log.error("[관리자] deleteBtnList 실패", e);
			result.put("result","FAIL");
			result.put("message","처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
		}
		return result;
	}

	
}