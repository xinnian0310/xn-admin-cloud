package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.config.UploadProperties;
import com.smartadmin.dto.AttachmentUploadVO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 业务附件上传：落盘为 yyyy/MM/dd/{uuid}{ext}，只返回展示文件名与对象路径； 不写 sys_file，访问由前端用远程连接配置 storage.minio +
 * filePath 拼接。
 */
@Service
@RequiredArgsConstructor
public class AttachmentService {

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final MinioStorageService minioStorageService;
    private final UploadProperties uploadProperties;
    private final RbacService rbacService;

    public AttachmentUploadVO upload(MultipartFile file) throws IOException {
        assertCanUpload();
        if (file == null
                || (!StringUtils.hasText(file.getOriginalFilename()) && file.getSize() == 0)) {
            throw new BusinessException("请选择文件");
        }
        String original = file.getOriginalFilename();
        String displayName =
                StringUtils.hasText(original)
                        ? Paths.get(original.replace('\\', '/')).getFileName().toString()
                        : "file";
        String ext = "";
        int dot = displayName.lastIndexOf('.');
        if (dot > 0 && dot < displayName.length() - 1) {
            ext = displayName.substring(dot);
        }
        String dir = LocalDate.now().format(DATE_DIR) + "/";
        String stored = UUID.randomUUID().toString().replace("-", "") + ext;
        String objectKey = dir + stored;

        if (minioStorageService.isReady()) {
            minioStorageService.upload(file, objectKey);
        } else {
            Path root = Paths.get(uploadProperties.getDir()).toAbsolutePath().normalize();
            Path targetDir = root.resolve(dir);
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(stored);
            file.transferTo(target.toFile());
        }
        return new AttachmentUploadVO(displayName, objectKey);
    }

    private void assertCanUpload() {
        if (rbacService.hasPermission("notice:create")
                || rbacService.hasPermission("notice:update")
                || rbacService.hasPermission("message:create")
                || rbacService.hasPermission("message:update")) {
            return;
        }
        throw new BusinessException("无附件上传权限");
    }
}
