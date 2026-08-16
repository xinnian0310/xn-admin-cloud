package com.smartadmin.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Data
@Component
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {

    /** 本地上传目录 */
    private String dir = "uploads";

    /**
     * 分片上传临时目录（仅 MinIO 不可用、回退本地存储时使用）。 为空时取上传根目录的**同级** {@code .chunk-tmp}——放在上传目录内会被文件管理页当成业务文件列出。
     */
    private String chunkDir = "";

    /**
     * 对外可访问的站点根（网关 / Nginx），例如 {@code https://api.example.com}。 为空时本地文件 URL 使用相对路径 {@code
     * /uploads/...}，不再写死 127.0.0.1。 kkFileView 预览需要绝对地址时，必须配置此项。
     */
    private String publicBaseUrl = "";

    public String normalizedPublicBaseUrl() {
        if (!StringUtils.hasText(publicBaseUrl)) {
            return "";
        }
        return publicBaseUrl.trim().replaceAll("/+$", "");
    }

    /** 本地存储对象对外 URL：优先 public-base-url，否则相对路径。 */
    public String localPublicUrl(String relativePath) {
        String rel =
                relativePath == null ? "" : relativePath.replace('\\', '/').replaceAll("^/+", "");
        String path = "/uploads/" + rel;
        String base = normalizedPublicBaseUrl();
        return base.isEmpty() ? path : base + path;
    }

    /** 解析分片临时目录；uploadRoot 为本地上传根目录的绝对路径。 */
    public Path resolveChunkDir(Path uploadRoot) {
        if (StringUtils.hasText(chunkDir)) {
            return Paths.get(chunkDir.trim()).toAbsolutePath().normalize();
        }
        Path parent = uploadRoot.getParent();
        Path base = parent == null ? uploadRoot : parent;
        return base.resolve(".chunk-tmp").normalize();
    }

    /** 供 kkFileView 拉取：相对路径时拼接 public-base-url；未配置则返回 null。 */
    public String absoluteForPreview(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return null;
        }
        String url = fileUrl.trim();
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        String base = normalizedPublicBaseUrl();
        if (base.isEmpty()) {
            return null;
        }
        if (url.startsWith("/")) {
            return base + url;
        }
        return base + "/" + url;
    }
}
