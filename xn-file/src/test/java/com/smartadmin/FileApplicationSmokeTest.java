package com.smartadmin;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/** 无中间件依赖的冒烟测试（CI 可离线跑通）。 */
class FileApplicationSmokeTest {

    @Test
    void applicationClassIsLoadable() {
        assertNotNull(FileApplication.class);
    }
}
