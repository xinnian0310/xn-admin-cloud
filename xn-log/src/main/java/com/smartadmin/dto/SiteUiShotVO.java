package com.smartadmin.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 官网 / 展示用界面截图排序（由路由管理树序生成）。 */
@Getter
@Setter
public class SiteUiShotVO {

    /** 前端项目标识，如 vue */
    private String project = "vue";

    /** 静态图相对目录（官网 public 下，按前端项目分子目录） */
    private String imageBase = "/docs/images/xn-admin-vue3-ts";

    private List<ShotItem> shots = new ArrayList<>();

    @Getter
    @Setter
    public static class ShotItem {
        private int sort;
        private String title;
        private String path;

        /** 文件名，如 dashboard.png */
        private String image;
    }
}
