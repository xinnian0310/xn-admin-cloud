package com.smartadmin.dto;

import lombok.Getter;
import lombok.Setter;

/** 用户个人布局与字号偏好。 仅覆盖全局系统配置中「布局与 UI → 布局与字号」对应字段；未设置的字段保持全局值。 */
@Getter
@Setter
public class UserUiConfigVO {

    private LayoutUi layout = new LayoutUi();
    private FontSizeUi fontSize = new FontSizeUi();
    private TagsViewUi tagsView = new TagsViewUi();
    private DialogUi dialog = new DialogUi();

    @Getter
    @Setter
    public static class LayoutUi {
        /** side | top | mix | columns */
        private String mode;
    }

    @Getter
    @Setter
    public static class FontSizeUi {
        private String sidebar;
        private String header;
        private String tagsView;
        private String main;
    }

    @Getter
    @Setter
    public static class TagsViewUi {
        private String height;
    }

    @Getter
    @Setter
    public static class DialogUi {
        private String maxHeight;
    }
}
