package com.agilesoda.module.admin.vo;

import lombok.Data;
import java.util.List;

@Data
public class CodeManagementSaveVO {
    private CodeManagementResVO group;
    private List<CodeManagementDetailResVO> detailList;


}

