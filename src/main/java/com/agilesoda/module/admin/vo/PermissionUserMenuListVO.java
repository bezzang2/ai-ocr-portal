package com.agilesoda.module.admin.vo;

import lombok.Data;
import java.util.List;

@Data
public class PermissionUserMenuListVO {
	private String roleId;		// 사용자ID
	private String frstRegEmpno;		// 사용자ID
	private List<String> menuIds;		// 체크된 리스트

}
