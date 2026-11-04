package com.agilesoda.module.admin.service;

import com.agilesoda.module.admin.mapper.PermissionManagementMapper;
import com.agilesoda.module.admin.vo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionManagementService {
	
	private final PermissionManagementMapper permissionManagementMapper;

	// 역활권한 조회
	public Map<String, Object> searchRoleAuth(PermissionManagementInqVO vo){

		// 현재 페이지 목록
		List<PermissionManagementResVO> list = permissionManagementMapper.searchRoleAuth(vo);
		Map<String, Object> result = new HashMap<>();
		result.put("list",list);
		return result;
	}

	// 사용자권한 조회
	public Map<String, Object> selectUserAuth(String roleId){

		List<PermissionManagementUserResVO> userList = permissionManagementMapper.selectUserAuth(roleId);
		Map<String, Object> result = new HashMap<>();
		result.put("userList",userList);
		return result;
	}

	// 역활권한 저장
	@Transactional
	public void saveRole(List<PermissionManagementResVO> roleList){
		for(PermissionManagementResVO vo : roleList){
			permissionManagementMapper.saveRole(vo);
		}
	}

	// 역활권한 삭제
	@Transactional
	public void deleteRole(PermissionManagementResVO vo){

		String roleId = vo.getRoleId();

		// 1. 해당 역할 사용자의 버튼권한 삭제
		permissionManagementMapper.deleteRoleButtion(roleId);
		// 2. 해당 역할 사용자의 메뉴권한 삭제
		permissionManagementMapper.deleteRoleMenu(roleId);
		// 3. 해당 역할 사용자권한 삭제
		permissionManagementMapper.deleteRoleUser(roleId);
		// 4. 역할 삭제
		permissionManagementMapper.deleteRole(vo);
	}

	// 사용자권한 저장
	@Transactional
	public void saveUser(List<PermissionManagementUserResVO> userList){
		for(PermissionManagementUserResVO vo : userList){
			permissionManagementMapper.saveUser(vo);
		}
	}

	// 사용자권한 삭제
	@Transactional
	public void deleteUser(PermissionManagementUserResVO vo){
		permissionManagementMapper.deleteUser(vo);
	}

	// 역활의 메뉴 권한 조회
	public List<PermissionUserMenuVO> getUserMenuList(String roleId){
		return permissionManagementMapper.getUserMenuList(roleId);
	}

	@Transactional
	public void saveMenuParmission(PermissionUserMenuListVO request){
		String roleId = request.getRoleId();
		List<String> menuIds = request.getMenuIds();
		String frstRegEmpno = request.getFrstRegEmpno();
		// 기존 메뉴 권한 전체 삭제
		permissionManagementMapper.deleteMenuParmission(roleId);
		// 체크된 메뉴가 있으면 저장
		if(menuIds != null && !menuIds.isEmpty()){
			for(String menuId : menuIds){
				permissionManagementMapper.insertMenuParmission(roleId,menuId,frstRegEmpno);
			}
		}
	}

	public List<PermissionUserBtnResVO> selectUserBtnLsit(String roleId, String menuId){
		return permissionManagementMapper.selectUserBtnLsit(roleId,menuId);
	}

	@Transactional
	public void saveMenuBtnParmission(String roleId, String menuId, String frstRegEmpno, List<String> btnList){

		// 기존 메뉴 권한 전체 삭제
		permissionManagementMapper.deleteUserBtnParmission(roleId,menuId);
		// 체크된 메뉴가 있으면 저장
		if(btnList != null && !btnList.isEmpty()){
			for(String btnCd : btnList){
				permissionManagementMapper.insertUserBtnParmission(roleId,menuId,btnCd,frstRegEmpno);
			}
		}
	}

	// 사용자정보 조회
	public List<CommonUserListVO> searchUserList(String empNo, String empKorFlnm){
		return permissionManagementMapper.searchUserList(empNo,empKorFlnm);
	}
}
