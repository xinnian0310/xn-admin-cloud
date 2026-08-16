package com.smartadmin.dto;

import com.smartadmin.common.BusinessException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/** 公告 / 站内信附件：只认 attachments 列表。 */
public final class AttachmentSupport {

    public static final int MAX_COUNT = 10;

    private AttachmentSupport() {}

    /** 写入前规范化：按 path 去重，超过上限直接拒绝。 */
    public static List<AttachmentItem> normalize(List<AttachmentItem> attachments) {
        List<AttachmentItem> items = new ArrayList<>();
        if (attachments != null) {
            for (AttachmentItem raw : attachments) {
                AttachmentItem item = clean(raw);
                if (item != null) {
                    items.add(item);
                }
            }
        }
        if (items.size() > MAX_COUNT) {
            throw new BusinessException("附件最多 " + MAX_COUNT + " 个");
        }
        Map<String, AttachmentItem> unique = new LinkedHashMap<>();
        for (AttachmentItem item : items) {
            unique.putIfAbsent(item.getPath(), item);
        }
        return List.copyOf(unique.values());
    }

    /** 读取时：空则返回空列表。 */
    public static List<AttachmentItem> resolve(List<AttachmentItem> stored) {
        if (stored == null || stored.isEmpty()) {
            return List.of();
        }
        return List.copyOf(stored);
    }

    private static AttachmentItem clean(AttachmentItem raw) {
        if (raw == null) {
            return null;
        }
        String name = StringUtils.hasText(raw.getName()) ? raw.getName().trim() : null;
        String path = StringUtils.hasText(raw.getPath()) ? raw.getPath().trim() : null;
        if (name == null && path == null) {
            return null;
        }
        if (name == null || path == null) {
            throw new BusinessException("附件文件名与路径需同时填写或同时清空");
        }
        if (name.length() > 255) {
            throw new BusinessException("附件文件名过长");
        }
        if (path.length() > 500) {
            throw new BusinessException("附件路径过长");
        }
        AttachmentItem item = new AttachmentItem(name, path);
        item.setSize(raw.getSize());
        if (StringUtils.hasText(raw.getUploadedAt())) {
            item.setUploadedAt(raw.getUploadedAt().trim());
        }
        return item;
    }
}
