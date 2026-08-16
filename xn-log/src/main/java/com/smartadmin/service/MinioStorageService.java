package com.smartadmin.service;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.smartadmin.common.BusinessException;
import com.smartadmin.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.ListObjectsArgs;
import io.minio.ListPartsResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioAsyncClient;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.SetBucketPolicyArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import io.minio.messages.Part;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService {

    /** S3 原生 multipart 除最后一片外的最小分片大小（5 MiB），MinIO 服务端强制校验 */
    public static final int MIN_MULTIPART_PART_SIZE = 5 * 1024 * 1024;

    /** S3 原生 multipart 单个对象的最大分片数 */
    public static final int MAX_MULTIPART_PARTS = 10000;

    private final MinioProperties properties;

    private volatile MinioClient client;

    /** 原生 multipart 接口只在异步客户端上公开，故与同步客户端并存 */
    private volatile MinioAsyncClient asyncClient;

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
            asyncClient =
                    MinioAsyncClient.builder()
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

    /** 已上传分片信息。分片字节数不取存储侧返回值，由调用方按 chunkSize 推算，避免依赖可选字段。 */
    public record PartInfo(int partNumber, String etag) {}

    /** 创建原生 multipart 上传，返回存储侧 uploadId */
    public String createMultipartUpload(String objectKey, String contentType) {
        assertReady();
        Multimap<String, String> headers = HashMultimap.create();
        headers.put(
                "Content-Type",
                StringUtils.hasText(contentType) ? contentType : "application/octet-stream");
        return await(
                        () ->
                                asyncClient.createMultipartUploadAsync(
                                        properties.getBucket(), null, objectKey, headers, null),
                        "创建分片上传")
                .result()
                .uploadId();
    }

    /** 上传单个分片，partNumber 从 1 开始，返回分片 ETag */
    public String uploadPart(String objectKey, String uploadId, int partNumber, byte[] data) {
        assertReady();
        return await(
                        () ->
                                asyncClient.uploadPartAsync(
                                        properties.getBucket(),
                                        null,
                                        objectKey,
                                        new ByteArrayInputStream(data),
                                        data.length,
                                        uploadId,
                                        partNumber,
                                        null,
                                        null),
                        "上传分片 " + partNumber)
                .etag();
    }

    /** 查询存储侧已收到的分片（自动翻页） */
    public List<PartInfo> listParts(String objectKey, String uploadId) {
        assertReady();
        List<PartInfo> parts = new ArrayList<>();
        int marker = 0;
        while (true) {
            final int partNumberMarker = marker;
            ListPartsResponse response =
                    await(
                            () ->
                                    asyncClient.listPartsAsync(
                                            properties.getBucket(),
                                            null,
                                            objectKey,
                                            MAX_MULTIPART_PARTS,
                                            partNumberMarker,
                                            uploadId,
                                            null,
                                            null),
                            "查询已上传分片");
            for (Part part : response.result().partList()) {
                parts.add(new PartInfo(part.partNumber(), part.etag()));
            }
            if (!response.result().isTruncated()) {
                return parts;
            }
            marker = response.result().nextPartNumberMarker();
        }
    }

    /** 合并分片。parts 必须按 partNumber 升序且连续。 */
    public void completeMultipartUpload(String objectKey, String uploadId, List<PartInfo> parts) {
        assertReady();
        Part[] array = new Part[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            PartInfo part = parts.get(i);
            array[i] = new Part(part.partNumber(), part.etag());
        }
        await(
                () ->
                        asyncClient.completeMultipartUploadAsync(
                                properties.getBucket(),
                                null,
                                objectKey,
                                uploadId,
                                array,
                                null,
                                null),
                "合并分片");
    }

    /** 放弃 multipart 上传并清理已上传分片；失败只记日志，便于取消操作幂等。 */
    public void abortMultipartUpload(String objectKey, String uploadId) {
        if (!isReady()) {
            return;
        }
        try {
            await(
                    () ->
                            asyncClient.abortMultipartUploadAsync(
                                    properties.getBucket(), null, objectKey, uploadId, null, null),
                    "取消分片上传");
        } catch (RuntimeException e) {
            log.warn("取消 MinIO 分片上传失败（可忽略）：{} {}", objectKey, e.getMessage());
        }
    }

    /** 对象字节数；对象不存在返回 null（用于判断合并是否已经完成过） */
    public Long objectSizeOrNull(String objectKey) {
        assertReady();
        try {
            return client.statObject(
                            StatObjectArgs.builder()
                                    .bucket(properties.getBucket())
                                    .object(objectKey)
                                    .build())
                    .size();
        } catch (ErrorResponseException e) {
            String code = e.errorResponse() == null ? "" : e.errorResponse().code();
            if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code)) {
                return null;
            }
            throw new BusinessException("读取 MinIO 对象信息失败：" + e.getMessage());
        } catch (Exception e) {
            throw new BusinessException("读取 MinIO 对象信息失败：" + e.getMessage());
        }
    }

    /** 统一处理异步调用的等待与异常转换 */
    private <T> T await(MinioCall<T> call, String action) {
        try {
            return call.get().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("MinIO " + action + "被中断");
        } catch (ExecutionException | CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new BusinessException("MinIO " + action + "失败：" + cause.getMessage());
        } catch (Exception e) {
            throw new BusinessException("MinIO " + action + "失败：" + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface MinioCall<T> {
        CompletableFuture<T> get() throws Exception;
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
        String endpoint = properties.resolvedPublicEndpoint();
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
