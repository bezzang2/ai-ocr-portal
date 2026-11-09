package com.agilesoda.common.menu;

import java.util.ArrayList;
import java.util.List;

import com.agilesoda.common.ComMenu;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class MenuManager {
    private static MenuManager instance = null;

    private List<ComMenu> list = null;

    public MenuManager(){
        this.list = new ArrayList<>();
    }

    public static MenuManager getInstance(){
        if(instance == null ){
            instance = new MenuManager();
        }

        return instance;
    }

    public void add(List<ComMenu> list){
        this.list.clear();
        this.list.addAll(list);
    }
    
    public ComMenu get(String menuId) {
    	ComMenu comMenu = null;
    	
    	for(ComMenu menu : this.list) {
    		if(menuId.equals(menu.getMenuId())) {
    			comMenu = menu;
    			break;
    		}
    	}
    	
    	return comMenu;
    }
}
