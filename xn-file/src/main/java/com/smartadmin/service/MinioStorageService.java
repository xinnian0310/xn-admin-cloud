package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.SetBucketPolicyArgs;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService {

    private final MinioProperties properties;

    private volatile MinioClient client;
    private volatile boolean ready;

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            ready = false;
            return;
        }
        try {
            client =
                    MinioClient.builder()
                            .endpoint(properties.getEndpoint())
                            .credentials(properties.getAccessKey(), properties.getSecretKey())
                            .build();
            ensureBucket();
            ready = true;
            log.info("MinIO 已连接：{} bucket={}", properties.getEndpoint(), properties.getBucket());
        } catch (Exception e) {
            ready = false;
            log.warn("MinIO 暂不可用（{}），文件将回退本地存储。启动 tool/minio 后重启后端即可。", e.getMessage());
        }
    }

    public boolean isReady() {
        return properties.isEnabled() && ready && client != null;
    }

    public void ensureBucket() throws Exception {
        String bucket = properties.getBucket();
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        // 公开读，便于 kkFileView / 浏览器直接访问对象 URL
        String policy =
                """
                {
                  "Version": "2012-10-17",
                  "Statement": [{
                    "Effect": "Allow",
                    "Principal": {"AWS": ["*"]},
                    "Action": ["s3:GetObject"],
                    "Resource": ["arn:aws:s3:::%s/*"]
                  }]
                }
                """
                        .formatted(bucket);
        try {
            client.setBucketPolicy(
                    SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build());
        } catch (Exception e) {
            log.debug("设置 MinIO 桶策略失败（可忽略）：{}", e.getMessage());
        }
    }

    public record ObjectInfo(
            String key, String name, long size, String lastModified, boolean directory) {
        public ObjectInfo(String key, String name, long size, String lastModified) {
            this(key, name, size, lastModified, false);
        }
    }

    public record PrefixListing(List<ObjectInfo> dirs, List<ObjectInfo> files) {}

    public List<ObjectInfo> list(String keyword) {
        PrefixListing listing = listPrefix("", true, keyword);
        return listing.files();
    }

    /** 按前缀浏览。recursive=false 时使用 delimiter=/，只返回当前层目录与文件。 */
    public PrefixListing listPrefix(String prefix, boolean recursive, String keyword) {
        assertReady();
        String normalized = normalizePrefix(prefix);
        List<ObjectInfo> dirs = new ArrayList<>();
        List<ObjectInfo> files = new ArrayList<>();
        try {
            ListObjectsArgs.Builder builder =
                    ListObjectsArgs.builder()
                            .bucket(properties.getBucket())
                            .prefix(normalized)
                            .recursive(recursive);
            if (!recursive) {
                builder.delimiter("/");
            }
            Iterable<Result<Item>> results = client.listObjects(builder.build());
            for (Result<Item> result : results) {
                Item item = result.get();
                String key = item.objectName();
                if (item.isDir() || key.endsWith("/")) {
                    if (recursive) {
                        continue;
                    }
                    String dirKey = key.endsWith("/") ? key : key + "/";
                    if (dirKey.equals(normalized)) {
                        continue;
                    }
                    String name = dirName(dirKey, normalized);
                    if (!StringUtils.hasText(name)) {
                        continue;
                    }
                    if (StringUtils.hasText(keyword)
                            && !dirKey.contains(keyword.trim())
                            && !name.contains(keyword.trim())) {
                        continue;
                    }
                    dirs.add(new ObjectInfo(dirKey, name, 0, "", true));
                    continue;
                }
                String name = key.contains("/") ? key.substring(key.lastIndexOf('/') + 1) : key;
                if (!StringUtils.hasText(name) || ".keep".equals(name)) {
                    continue;
                }
                if (!recursive) {
                    String rest = key.substring(normalized.length());
                    if (rest.contains("/")) {
                        continue;
                    }
                }
                if (StringUtils.hasText(keyword)
                        && !key.contains(keyword.trim())
                        && !name.contains(keyword.trim())) {
                    continue;
                }
                long size = item.size();
                String modified =
                        item.lastModified() != null
                                ? item.lastModified()
                                        .toLocalDateTime()
                                        .format(
                                                java.time.format.DateTimeFormatter.ofPattern(
                                                        "yyyy-MM-dd HH:mm:ss"))
                                : "";
                files.add(new ObjectInfo(key, name, size, modified, false));
            }
        } catch (Exception e) {
            throw new BusinessException("读取 MinIO 文件列表失败：" + e.getMessage());
        }
        return new PrefixListing(dirs, files);
    }

    public ObjectInfo upload(MultipartFile file, String objectKey) {
        assertReady();
        try (InputStream in = file.getInputStream()) {
            client.putObject(
                    PutObjectArgs.builder().bucket(properties.getBucket()).object(objectKey).stream(
                                    in, file.getSize(), -1)
                            .contentType(
                                    file.getContentType() != null
                                            ? file.getContentType()
                                            : "application/octet-stream")
                            .build());
            return new ObjectInfo(objectKey, file.getOriginalFilename(), file.getSize(), "");
        } catch (Exception e) {
            throw new BusinessException("上传到 MinIO 失败：" + e.getMessage());
        }
    }

    /** 创建“目录”（写入空占位对象 prefix/.keep） */
    public void mkdir(String prefix) {
        assertReady();
        String dir = normalizePrefix(prefix);
        if (!StringUtils.hasText(dir)) {
            throw new BusinessException("目录路径不能为空");
        }
        String marker = dir + ".keep";
        try {
            byte[] empty = new byte[0];
            client.putObject(
                    PutObjectArgs.builder().bucket(properties.getBucket()).object(marker).stream(
                                    new java.io.ByteArrayInputStream(empty), 0, -1)
                            .contentType("application/octet-stream")
                            .build());
        } catch (Exception e) {
            throw new BusinessException("创建 MinIO 目录失败：" + e.getMessage());
        }
    }

    public void delete(String objectKey) {
        assertReady();
        try {
            client.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(properties.getBucket())
                            .object(objectKey)
                            .build());
        } catch (Exception e) {
            throw new BusinessException("删除 MinIO 对象失败：" + e.getMessage());
        }
    }

    public String publicUrl(String objectKey) {
        String endpoint = properties.getEndpoint().replaceAll("/+$", "");
        return endpoint + "/" + properties.getBucket() + "/" + objectKey.replace("\\", "/");
    }

    /** 探测连通性（不抛到上层） */
    public String pingMessage() {
        if (!properties.isEnabled()) {
            return "DISABLED";
        }
        if (!isReady()) {
            return "DOWN:未连接，请确认 MinIO 已启动";
        }
        try {
            client.bucketExists(BucketExistsArgs.builder().bucket(properties.getBucket()).build());
            return "UP";
        } catch (Exception e) {
            return "DOWN:" + e.getMessage();
        }
    }

    public static String normalizePrefix(String prefix) {
        if (!StringUtils.hasText(prefix)) {
            return "";
        }
        String p = prefix.replace('\\', '/').trim();
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (p.contains("..")) {
            throw new BusinessException("非法路径");
        }
        if (!p.isEmpty() && !p.endsWith("/")) {
            p = p + "/";
        }
        return p;
    }

    private static String dirName(String dirKey, String parentPrefix) {
        String relative = dirKey.substring(parentPrefix.length());
        if (relative.endsWith("/")) {
            relative = relative.substring(0, relative.length() - 1);
        }
        int slash = relative.indexOf('/');
        return slash >= 0 ? relative.substring(0, slash) : relative;
    }

    private void assertReady() {
        if (!isReady()) {
            throw new BusinessException(
                    "MinIO 未就绪，请先启动 tool/minio，或将 app.minio.enabled 设为 false 使用本地存储");
        }
    }
}
