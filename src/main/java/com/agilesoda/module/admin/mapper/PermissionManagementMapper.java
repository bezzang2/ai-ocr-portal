package com.agilesoda.module.admin.mapper;

import com.agilesoda.module.admin.vo.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;


import java.util.List;


@Mapper
public interface PermissionManagementMapper {

    // 역활권한 조회
    List<PermissionManagementResVO> searchRoleAuth(PermissionManagementInqVO vo);

    // 역활권한 저장
    int saveRole(PermissionManagementResVO vo);

    // 역활권한 삭제
    int deleteRole(PermissionManagementResVO vo);

    // 역할의 버튼권한 삭제
    int deleteRoleButtion(@Param("roleId") String roleId);

    // 역할의 메뉴권한 삭제
    int deleteRoleMenu(@Param("roleId") String roleId);

    // 사용자권한 역할ID로 삭제
    int deleteRoleUser(@Param("roleId") String roleId);

    // 역활의 메뉴 권한 조회
    List<PermissionUserMenuVO> getUserMenuList(@Param("roleId") String roleId);

    // 역할권한의 메뉴 전체 삭제
    int deleteMenuParmission(@Param("roleId") String roleId);

    // 사용자권한 메뉴 전체 저장
    int insertMenuParmission(@Param("roleId") String roleId, @Param("menuId") String menuId,@Param("frstRegEmpno") String frstRegEmpno);




    // 사용자권한 조회
    List<PermissionManagementUserResVO> selectUserAuth(@Param("roleId") String roleId);

    // 사용자권한 저장
    int saveUser(PermissionManagementUserResVO vo);

    // 사용자권한 삭제
    int deleteUser(PermissionManagementUserResVO vo);




    // 버튼 리스트 조회
    List<PermissionUserBtnResVO> selectUserBtnLsit(@Param("roleId") String roleId, @Param("menuId") String menuId);

    // 버튼권한 전체 삭제
    void deleteUserBtnParmission(@Param("roleId") String roleId, @Param("menuId") String menuId);

    // 버튼권한 전체 저장
    void insertUserBtnParmission(@Param("roleId") String roleId, @Param("menuId") String menuId,@Param("btnCd") String btnCd,@Param("frstRegEmpno") String frstRegEmpno);


    // 사용자정보 조회
    List<CommonUserListVO> searchUserList(@Param("empNo") String empNo, @Param("empKorFlnm") String empKorFlnm);
}
