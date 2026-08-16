package com.smartadmin.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartadmin.common.BusinessException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AttachmentSupportTest {

    @Test
    void normalizeKeepsOrderAndDropsEmpty() {
        List<AttachmentItem> items =
                AttachmentSupport.normalize(
                        List.of(
                                new AttachmentItem("a.pdf", "2026/08/16/a.pdf"),
                                new AttachmentItem("b.txt", "2026/08/16/b.txt")));
        assertEquals(2, items.size());
        assertEquals("a.pdf", items.get(0).getName());
        assertEquals("b.txt", items.get(1).getName());
    }

    @Test
    void resolveEmptyWhenMissing() {
        assertTrue(AttachmentSupport.resolve(null).isEmpty());
        assertTrue(AttachmentSupport.resolve(List.of()).isEmpty());
    }

    @Test
    void rejectsMoreThanMax() {
        List<AttachmentItem> tooMany = new ArrayList<>();
        for (int i = 0; i < AttachmentSupport.MAX_COUNT + 1; i++) {
            tooMany.add(new AttachmentItem("f" + i + ".txt", "p/" + i + ".txt"));
        }
        BusinessException ex =
                assertThrows(BusinessException.class, () -> AttachmentSupport.normalize(tooMany));
        assertTrue(ex.getMessage().contains("最多"));
    }

    @Test
    void dedupesByPath() {
        List<AttachmentItem> items =
                AttachmentSupport.normalize(
                        List.of(
                                new AttachmentItem("a.pdf", "same/path.pdf"),
                                new AttachmentItem("b.pdf", "same/path.pdf")));
        assertEquals(1, items.size());
        assertEquals("a.pdf", items.get(0).getName());
    }
}
