package com.agilesoda.module.admin.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.agilesoda.module.admin.mapper.MenuManagementMapper;
import com.agilesoda.module.admin.vo.MenuManagementBtnVO;
import com.agilesoda.module.admin.vo.MenuManagementVO;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuManagementService {
	
	private final MenuManagementMapper menuManagementMapper;


	public List<MenuManagementVO> getMenuList(String menuId){
		return menuManagementMapper.getMenuList(menuId);
	}

	public void insertMenu(MenuManagementVO vo){
		menuManagementMapper.insertMenu(vo);
	}

	@Transactional
	public void deleteMenu(MenuManagementVO vo){
		menuManagementMapper.deleteMenu(vo);
	}

	public List<MenuManagementBtnVO> selectBtnList(String menuId){
		return menuManagementMapper.selectBtnList(menuId);
	}

	@Transactional
	public void insertBtnList(List<MenuManagementBtnVO> btnList){
		for(MenuManagementBtnVO vo : btnList){
			menuManagementMapper.insertBtnList(vo);
		}
	}
	@Transactional
	public void deleteBtnList(MenuManagementBtnVO vo){
		// 역할권한의 버튼권한 삭제
		menuManagementMapper.deleteRoleBtn(vo);
		// 메뉴 버튼 정의 삭제
		menuManagementMapper.deleteBtnList(vo);
	}
}
