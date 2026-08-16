package com.smartadmin.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 对象存储分区读写：名字 / 路径列表。 */
@Data
public class StorageSectionVO {

    private List<Item> items = new ArrayList<>();

    @Data
    public static class Item {
        private String name;
        private String path;

        public Item() {}

        public Item(String name, String path) {
            this.name = name;
            this.path = path;
        }
    }
}
