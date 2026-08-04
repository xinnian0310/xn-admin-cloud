package com.smartadmin.service;

import com.smartadmin.config.MinioProperties;
import com.smartadmin.entity.SysFile;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 回收站彻底删除时清理对象存储 / 本地文件，避免与 FileManageService 循环依赖。
 */
@Component
@RequiredArgsConstructor
public class FilePurgeHelper {

    private final MinioStorageService minioStorageService;
    private final MinioProperties minioProperties;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    public void purgeStorage(SysFile file) throws Exception {
        if (file == null || file.getObjectKey() == null) {
            return;
        }
        String key = file.getObjectKey();
        if ("minio".equalsIgnoreCase(file.getStorage()) && minioStorageService.isReady() && minioProperties.isEnabled()) {
            minioStorageService.delete(key);
            return;
        }
        Path target = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(key).normalize();
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (!target.startsWith(root)) {
            return;
        }
        Files.deleteIfExists(target);
    }
}
