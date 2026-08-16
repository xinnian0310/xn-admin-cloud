package com.smartadmin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

/** 落盘命名：key 用 uuid + 扩展名，原名只作展示，同名不再互相覆盖。 */
class FileManageServiceAllocateTest {

    /** allocateObject 不依赖任何协作者，注入 null 即可 */
    private final FileManageService service =
            new FileManageService(null, null, null, null, null, null, null, null);

    private String today() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd")) + "/";
    }

    @Test
    void keyUsesUuidAndKeepsOriginalNameForDisplay() {
        FileManageService.AllocatedObject target = service.allocateObject("deepseek-harness.rar");

        assertEquals(today(), target.prefix());
        assertEquals(".rar", target.extension());
        assertEquals("deepseek-harness.rar", target.displayName());
        assertEquals(target.prefix() + target.storedName(), target.objectKey());
        assertTrue(target.storedName().matches("[0-9a-f]{32}\\.rar"), target.storedName());
    }

    /** 同名两次分配到不同 key：并发上传不会互相覆盖 */
    @Test
    void sameNameNeverCollides() {
        String name = "报表.xlsx";

        String first = service.allocateObject(name).objectKey();
        String second = service.allocateObject(name).objectKey();

        assertNotEquals(first, second);
    }

    /** 中文名、空格、# 之类只留在展示名里，不进 key，避免破坏返回的 URL */
    @Test
    void keyStaysUrlSafeForAwkwardNames() {
        FileManageService.AllocatedObject target = service.allocateObject("季度 报表#final.XLSX");

        assertEquals("季度 报表#final.XLSX", target.displayName());
        assertTrue(target.storedName().matches("[0-9a-f]{32}\\.XLSX"), target.storedName());
    }

    /** 无扩展名、以点结尾、以及不可当后缀的怪异扩展名都退化为无后缀 */
    @Test
    void dropsUnusableExtensions() {
        assertEquals("", service.allocateObject("README").extension());
        assertEquals("", service.allocateObject("trailing.").extension());
        assertEquals("", service.allocateObject("archive.备份").extension());
        assertEquals("", service.allocateObject(".gitignore").extension());
    }

    /** 只剩路径分隔符等无效字符时，展示名回落为 storedName，避免库里原名为空 */
    @Test
    void fallsBackToStoredNameWhenOriginalUnusable() {
        FileManageService.AllocatedObject target = service.allocateObject("../");

        assertEquals(target.storedName(), target.displayName());
    }

    /** 多级扩展名只取最后一段 */
    @Test
    void keepsLastSegmentOfMultiPartExtension() {
        assertEquals(".gz", service.allocateObject("dump.tar.gz").extension());
    }
}
