package com.agilesoda.common.menu;

import com.agilesoda.module.admin.vo.CommonMenuListVO;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Getter
public class MenuManager {
    private static MenuManager instance = null;

    private List<CommonMenuListVO> list = null;

    public MenuManager(){
        this.list = new ArrayList<>();
    }

    public static MenuManager getInstance(){
        if(instance == null ){
            instance = new MenuManager();
        }

        return instance;
    }

    public void add(List<CommonMenuListVO> list){
        this.list.clear();
        this.list = list;
    }
}
