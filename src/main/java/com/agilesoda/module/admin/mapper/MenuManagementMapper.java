package com.agilesoda.module.admin.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.agilesoda.module.admin.vo.MenuManagementBtnVO;
import com.agilesoda.module.admin.vo.MenuManagementVO;

@Mapper
public interface MenuManagementMapper {

    List<MenuManagementVO> getMenuList(String menuId);

    int insertMenu(MenuManagementVO vo);

    int deleteMenu(MenuManagementVO vo);

    List<MenuManagementBtnVO> selectBtnList(@Param("menuId") String MenuId);

    int insertBtnList(MenuManagementBtnVO vo);

    int deleteBtnList(MenuManagementBtnVO vo);

    int deleteRoleBtn(MenuManagementBtnVO vo);

}
