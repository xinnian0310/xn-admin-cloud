package com.smartadmin.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class PageUiConfigVO {

    private String routePath;
    private List<PageUiSearchItemDTO> searchItems = new ArrayList<>();
    /** 工具栏按钮，来自权限内容中的 BUTTON 子权限 */
    private List<PageUiButtonItemDTO> buttons = new ArrayList<>();
    /** 表格操作列按钮，来自权限内容中的 TABLE_BUTTON 子权限 */
    private List<PageUiButtonItemDTO> tableButtons = new ArrayList<>();
}
