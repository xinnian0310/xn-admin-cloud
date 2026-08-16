package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.config.MinioProperties;
import com.smartadmin.config.UploadProperties;
import com.smartadmin.dto.ChunkUploadCheckRequest;
import com.smartadmin.dto.ChunkUploadCheckVO;
import com.smartadmin.dto.ChunkUploadInitRequest;
import com.smartadmin.dto.ChunkUploadPartVO;
import com.smartadmin.dto.ChunkUploadSessionVO;
import com.smartadmin.dto.FileInfoVO;
import com.smartadmin.entity.SysUploadSession;
import com.smartadmin.entity.UploadSessionStatus;
import com.smartadmin.repository.SysUploadSessionRepository;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 大文件分片上传：秒传探测、初始化、分片接收、合并、取消。
 *
 * <p>MinIO 可用时走 S3 原生 multipart（分片由存储侧持有，无临时对象）；不可用时回退本地磁盘临时分片 + 合并。
 *
 * <p>「已上传哪些分片」不落库，一律现查存储侧（MinIO listParts / 本地临时目录）， 因此浏览器刷新、后端重启、并发上传都不会出现清单与实际不一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkUploadService {

    /** 全量摘要：与分片大小无关 */
    public static final String ALGO_FULL = "sha256";

    /** 树状摘要：各分片摘要拼接后再摘要，取值依赖分片大小 */
    public static final String ALGO_TREE = "sha256-tree";

    /**
     * 元信息摘要：由文件名 / 大小 / 修改时间派生，客户端未读文件内容。
     *
     * <p>只用于断点续传的会话匹配，**永不参与秒传**——元信息相同不能证明内容相同。
     */
    public static final String ALGO_META = "meta";

    /** 单片上限，需同时受 spring.servlet.multipart.max-file-size 约束 */
    private static final int MAX_CHUNK_SIZE = 32 * 1024 * 1024;

    private static final Pattern HEX_HASH = Pattern.compile("^[0-9a-f]{32,128}$");
    private static final String PART_SUFFIX = ".part";
    private static final String STORAGE_MINIO = "minio";
    private static final String STORAGE_LOCAL = "local";

    private final RbacService rbacService;
    private final MinioStorageService minioStorageService;
    private final MinioProperties minioProperties;
    private final UploadProperties uploadProperties;
    private final FileManageService fileManageService;
    private final SysUploadSessionRepository sessionRepository;

    /** 秒传 / 续传探测：命中已完成的同指纹文件即可直接返回，无需上传任何字节。 */
    public ChunkUploadCheckVO check(ChunkUploadCheckRequest request) {
        rbacService.checkPermission("file:upload");
        String algo = normalizeAlgo(request.getHashAlgo());
        String hash = normalizeHash(request.getFileHash());
        int chunkSize = request.getChunkSize();
        validateChunking(request.getFileSize(), chunkSize);

        ChunkUploadCheckVO vo = new ChunkUploadCheckVO();
        if (!ALGO_META.equals(algo)) {
            // 全量摘要与分片大小无关，树状摘要则必须同分片大小才可比
            Integer fingerprintChunkSize = ALGO_TREE.equals(algo) ? chunkSize : null;
            for (SysUploadSession done :
                    sessionRepository.findCompletedByFingerprint(
                            algo, hash, request.getFileSize(), fingerprintChunkSize)) {
                FileInfoVO file = fileManageService.findRegisteredFile(done.getObjectKey());
                if (file != null) {
                    vo.setExists(true);
                    vo.setFile(file);
                    return vo;
                }
            }
        }

        SysUploadSession resumable =
                findResumable(algo, hash, request.getFileSize(), chunkSize).orElse(null);
        if (resumable != null) {
            vo.setSession(toSessionVO(resumable));
        }
        return vo;
    }

    /** 初始化上传；同一用户、同指纹、同分片大小的未完成会话会被复用，从而支持刷新页面后续传。 */
    @Transactional
    public ChunkUploadSessionVO init(ChunkUploadInitRequest request) {
        rbacService.checkPermission("file:upload");
        String algo = normalizeAlgo(request.getHashAlgo());
        String hash = normalizeHash(request.getFileHash());
        int chunkSize = request.getChunkSize();
        validateChunking(request.getFileSize(), chunkSize);

        SysUploadSession reusable =
                findResumable(algo, hash, request.getFileSize(), chunkSize).orElse(null);
        if (reusable != null) {
            return toSessionVO(reusable);
        }

        String storage = currentStorage();
        FileManageService.AllocatedObject target =
                fileManageService.allocateObject(request.getFileName());
        SysUploadSession session = new SysUploadSession();
        session.setUploadId(UUID.randomUUID().toString().replace("-", ""));
        session.setFileHash(hash);
        session.setHashAlgo(algo);
        session.setFileName(target.displayName());
        session.setFileSize(request.getFileSize());
        session.setChunkSize(chunkSize);
        session.setTotalChunks(totalChunks(request.getFileSize(), chunkSize));
        session.setContentType(
                StringUtils.hasText(request.getContentType())
                        ? request.getContentType()
                        : "application/octet-stream");
        session.setStorage(storage);
        session.setObjectKey(target.objectKey());
        session.setPrefix(target.prefix());
        session.setStoredName(target.storedName());
        session.setStatus(UploadSessionStatus.UPLOADING);
        session.setUploader(RbacService.currentUsername());

        if (STORAGE_MINIO.equals(storage)) {
            session.setBucket(minioProperties.getBucket());
            session.setStorageUploadId(
                    minioStorageService.createMultipartUpload(
                            target.objectKey(), session.getContentType()));
        } else {
            createLocalDir(localSessionDir(session));
        }
        sessionRepository.save(session);
        return toSessionVO(session, List.of());
    }

    /** 会话状态 + 已上传分片清单（现查存储侧） */
    public ChunkUploadSessionVO status(String uploadId) {
        rbacService.checkPermission("file:upload");
        return toSessionVO(requireSession(uploadId));
    }

    /** 接收单个分片。分片可重复上传（同下标覆盖），因此重试天然幂等。 */
    public ChunkUploadPartVO uploadPart(
            String uploadId, int chunkIndex, String chunkHash, MultipartFile file) {
        rbacService.checkPermission("file:upload");
        SysUploadSession session = requireSession(uploadId);
        if (session.getStatus() != UploadSessionStatus.UPLOADING) {
            throw new BusinessException("上传会话已" + statusText(session.getStatus()) + "，无法继续上传分片");
        }
        if (chunkIndex < 0 || chunkIndex >= session.getTotalChunks()) {
            throw new BusinessException(
                    "分片下标越界：" + chunkIndex + "，应在 0 ~ " + (session.getTotalChunks() - 1));
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("分片内容为空");
        }
        int expected = expectedChunkSize(session, chunkIndex);
        if (file.getSize() != expected) {
            throw new BusinessException(
                    "分片 "
                            + chunkIndex
                            + " 大小不符：期望 "
                            + expected
                            + " 字节，实际 "
                            + file.getSize()
                            + " 字节");
        }

        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("读取分片内容失败：" + e.getMessage());
        }
        if (StringUtils.hasText(chunkHash)) {
            String actual = sha256Hex(data);
            String declared = chunkHash.trim().toLowerCase(Locale.ROOT);
            if (!actual.equals(declared)) {
                throw new BusinessException("分片 " + chunkIndex + " 校验失败，请重试该分片");
            }
        }

        ChunkUploadPartVO vo = new ChunkUploadPartVO();
        vo.setChunkIndex(chunkIndex);
        vo.setSize(data.length);
        vo.setTotalChunks(session.getTotalChunks());
        if (STORAGE_MINIO.equals(session.getStorage())) {
            vo.setEtag(
                    minioStorageService.uploadPart(
                            session.getObjectKey(),
                            session.getStorageUploadId(),
                            chunkIndex + 1,
                            data));
        } else {
            writeLocalPart(session, chunkIndex, data);
        }
        return vo;
    }

    /**
     * 合并分片。
     *
     * <p>幂等：会话已完成、或上一次「合并成功但登记元数据失败」的情况下再次调用， 只补登记不重复合并。
     *
     * <p>不加 {@code @Transactional}：合并（本地回退时是逐片拷贝）耗时可能很长， 不应在此期间占用数据库连接；元数据登记本身已是独立事务，且本方法幂等可重试。
     */
    public FileInfoVO complete(String uploadId) {
        rbacService.checkPermission("file:upload");
        SysUploadSession session = requireSession(uploadId);
        if (session.getStatus() == UploadSessionStatus.ABORTED) {
            throw new BusinessException("上传会话已取消，请重新上传");
        }

        if (!isAlreadyMerged(session)) {
            assertAllPartsPresent(session);
            if (STORAGE_MINIO.equals(session.getStorage())) {
                List<MinioStorageService.PartInfo> parts =
                        minioStorageService.listParts(
                                session.getObjectKey(), session.getStorageUploadId());
                parts.sort(Comparator.comparingInt(MinioStorageService.PartInfo::partNumber));
                minioStorageService.completeMultipartUpload(
                        session.getObjectKey(), session.getStorageUploadId(), parts);
                assertMergedSize(session);
            } else {
                mergeLocalParts(session);
                deleteLocalDir(localSessionDir(session));
            }
        }

        String url =
                STORAGE_MINIO.equals(session.getStorage())
                        ? minioStorageService.publicUrl(session.getObjectKey())
                        : uploadProperties.localPublicUrl(session.getObjectKey());
        FileInfoVO file =
                fileManageService.registerUploadedFile(
                        session.getObjectKey(),
                        session.getPrefix(),
                        session.getFileName(),
                        session.getStoredName(),
                        extensionOf(session.getStoredName()),
                        session.getContentType(),
                        session.getFileSize(),
                        session.getStorage(),
                        session.getBucket(),
                        url);
        session.setStatus(UploadSessionStatus.COMPLETED);
        session.setUrl(url);
        session.setCompletedAt(LocalDateTime.now());
        sessionRepository.save(session);
        return file;
    }

    /** 取消上传并清理已上传分片 */
    @Transactional
    public void cancel(String uploadId) {
        rbacService.checkPermission("file:upload");
        SysUploadSession session = requireSession(uploadId);
        if (session.getStatus() == UploadSessionStatus.COMPLETED) {
            throw new BusinessException("上传已完成，无法取消");
        }
        if (STORAGE_MINIO.equals(session.getStorage())
                && StringUtils.hasText(session.getStorageUploadId())) {
            minioStorageService.abortMultipartUpload(
                    session.getObjectKey(), session.getStorageUploadId());
        } else {
            deleteLocalDir(localSessionDir(session));
        }
        session.setStatus(UploadSessionStatus.ABORTED);
        sessionRepository.save(session);
    }

    // ------------------------------------------------------------------ 会话

    private SysUploadSession requireSession(String uploadId) {
        if (!StringUtils.hasText(uploadId)) {
            throw new BusinessException("uploadId 不能为空");
        }
        return sessionRepository
                .findByUploadId(uploadId.trim())
                .orElseThrow(() -> new BusinessException("上传会话不存在或已过期，请重新上传"));
    }

    /** 复用未完成会话；存储侧已失效（如 multipart 被清理）时标记取消并返回空。 */
    private Optional<SysUploadSession> findResumable(
            String algo, String hash, long fileSize, int chunkSize) {
        String uploader = RbacService.currentUsername();
        for (SysUploadSession session :
                sessionRepository.findResumableByFingerprint(
                        algo, hash, fileSize, chunkSize, uploader)) {
            if (!currentStorage().equals(session.getStorage())) {
                continue;
            }
            try {
                uploadedIndexes(session);
                return Optional.of(session);
            } catch (RuntimeException e) {
                log.info("上传会话 {} 在存储侧已失效，标记取消：{}", session.getUploadId(), e.getMessage());
                session.setStatus(UploadSessionStatus.ABORTED);
                sessionRepository.save(session);
            }
        }
        return Optional.empty();
    }

    private ChunkUploadSessionVO toSessionVO(SysUploadSession session) {
        if (session.getStatus() == UploadSessionStatus.COMPLETED) {
            List<Integer> all = new ArrayList<>(session.getTotalChunks());
            for (int i = 0; i < session.getTotalChunks(); i++) {
                all.add(i);
            }
            return toSessionVO(session, all);
        }
        if (session.getStatus() == UploadSessionStatus.ABORTED) {
            return toSessionVO(session, List.of());
        }
        return toSessionVO(session, uploadedIndexes(session));
    }

    private ChunkUploadSessionVO toSessionVO(SysUploadSession session, List<Integer> uploaded) {
        ChunkUploadSessionVO vo = new ChunkUploadSessionVO();
        vo.setUploadId(session.getUploadId());
        vo.setFileName(session.getFileName());
        vo.setFileSize(session.getFileSize());
        vo.setChunkSize(session.getChunkSize());
        vo.setTotalChunks(session.getTotalChunks());
        vo.setUploadedChunks(uploaded);
        vo.setStatus(session.getStatus().name());
        vo.setStorage(session.getStorage());
        vo.setMinChunkSize(minChunkSize());
        long bytes = 0;
        for (Integer index : uploaded) {
            bytes += expectedChunkSize(session, index);
        }
        vo.setUploadedBytes(bytes);
        return vo;
    }

    // ------------------------------------------------------------------ 分片清单

    /** 已上传分片下标（0 起），来源为存储侧实际状态 */
    private List<Integer> uploadedIndexes(SysUploadSession session) {
        if (STORAGE_MINIO.equals(session.getStorage())) {
            Set<Integer> indexes = new TreeSet<>();
            for (MinioStorageService.PartInfo part :
                    minioStorageService.listParts(
                            session.getObjectKey(), session.getStorageUploadId())) {
                int index = part.partNumber() - 1;
                if (index >= 0 && index < session.getTotalChunks()) {
                    indexes.add(index);
                }
            }
            return new ArrayList<>(indexes);
        }
        return localUploadedIndexes(session);
    }

    private void assertAllPartsPresent(SysUploadSession session) {
        List<Integer> uploaded = uploadedIndexes(session);
        if (uploaded.size() == session.getTotalChunks()) {
            return;
        }
        Set<Integer> present = new TreeSet<>(uploaded);
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < session.getTotalChunks() && missing.size() < 10; i++) {
            if (!present.contains(i)) {
                missing.add(i);
            }
        }
        throw new BusinessException(
                "还有 "
                        + (session.getTotalChunks() - uploaded.size())
                        + " 个分片未上传（如 "
                        + missing
                        + "），无法合并");
    }

    /** 上一次合并可能已成功但后续步骤失败，此时不应重复合并 */
    private boolean isAlreadyMerged(SysUploadSession session) {
        if (STORAGE_MINIO.equals(session.getStorage())) {
            Long size = minioStorageService.objectSizeOrNull(session.getObjectKey());
            return size != null && size.equals(session.getFileSize());
        }
        Path target = localTarget(session);
        try {
            return Files.exists(target) && Files.size(target) == session.getFileSize();
        } catch (IOException e) {
            return false;
        }
    }

    private void assertMergedSize(SysUploadSession session) {
        Long size = minioStorageService.objectSizeOrNull(session.getObjectKey());
        if (size == null || !size.equals(session.getFileSize())) {
            minioStorageService.delete(session.getObjectKey());
            session.setStatus(UploadSessionStatus.ABORTED);
            sessionRepository.save(session);
            throw new BusinessException(
                    "合并后文件大小不符（期望 " + session.getFileSize() + " 字节，实际 " + size + "），已清理，请重新上传");
        }
    }

    // ------------------------------------------------------------------ 本地回退

    private Path localSessionDir(SysUploadSession session) {
        return uploadProperties
                .resolveChunkDir(fileManageService.uploadRoot())
                .resolve(session.getUploadId());
    }

    private Path localTarget(SysUploadSession session) {
        return fileManageService.uploadRoot().resolve(session.getObjectKey()).normalize();
    }

    private void createLocalDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BusinessException("创建分片临时目录失败：" + e.getMessage());
        }
    }

    private void writeLocalPart(SysUploadSession session, int chunkIndex, byte[] data) {
        Path dir = localSessionDir(session);
        createLocalDir(dir);
        Path target = dir.resolve(chunkIndex + PART_SUFFIX);
        Path temp = dir.resolve(chunkIndex + PART_SUFFIX + ".tmp");
        try {
            Files.write(
                    temp,
                    data,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("写入分片 " + chunkIndex + " 失败：" + e.getMessage());
        }
    }

    /** 只认大小正确的 .part 文件，避免崩溃残留的半个分片被当成已完成 */
    private List<Integer> localUploadedIndexes(SysUploadSession session) {
        Path dir = localSessionDir(session);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        Set<Integer> indexes = new TreeSet<>();
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path path : stream.toList()) {
                String name = path.getFileName().toString();
                if (!name.endsWith(PART_SUFFIX)) {
                    continue;
                }
                int index;
                try {
                    index =
                            Integer.parseInt(
                                    name.substring(0, name.length() - PART_SUFFIX.length()));
                } catch (NumberFormatException ignored) {
                    continue;
                }
                if (index < 0 || index >= session.getTotalChunks()) {
                    continue;
                }
                if (Files.size(path) == expectedChunkSize(session, index)) {
                    indexes.add(index);
                }
            }
        } catch (IOException e) {
            throw new BusinessException("读取分片临时目录失败：" + e.getMessage());
        }
        return new ArrayList<>(indexes);
    }

    private void mergeLocalParts(SysUploadSession session) {
        Path target = localTarget(session);
        Path dir = localSessionDir(session);
        Path merging = target.resolveSibling(target.getFileName() + ".merging");
        try {
            Files.createDirectories(target.getParent());
            try (OutputStream out =
                    new BufferedOutputStream(
                            Files.newOutputStream(
                                    merging,
                                    StandardOpenOption.CREATE,
                                    StandardOpenOption.TRUNCATE_EXISTING,
                                    StandardOpenOption.WRITE))) {
                for (int i = 0; i < session.getTotalChunks(); i++) {
                    Files.copy(dir.resolve(i + PART_SUFFIX), out);
                }
            }
            if (Files.size(merging) != session.getFileSize()) {
                long actual = Files.size(merging);
                Files.deleteIfExists(merging);
                throw new BusinessException(
                        "合并后文件大小不符（期望 " + session.getFileSize() + " 字节，实际 " + actual + "），请重新上传");
            }
            Files.move(merging, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("合并分片失败：" + e.getMessage());
        }
    }

    private void deleteLocalDir(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            log.warn("清理分片临时目录失败（可忽略）：{} {}", dir, e.getMessage());
        }
    }

    // ------------------------------------------------------------------ 校验与工具

    private String currentStorage() {
        return minioStorageService.isReady() ? STORAGE_MINIO : STORAGE_LOCAL;
    }

    /** MinIO 原生 multipart 要求除最后一片外每片 ≥ 5MiB；本地回退无此限制。 */
    private int minChunkSize() {
        return minioStorageService.isReady() ? MinioStorageService.MIN_MULTIPART_PART_SIZE : 1;
    }

    private void validateChunking(long fileSize, int chunkSize) {
        if (chunkSize > MAX_CHUNK_SIZE) {
            throw new BusinessException("分片大小不能超过 " + MAX_CHUNK_SIZE / 1024 / 1024 + "MB");
        }
        int total = totalChunks(fileSize, chunkSize);
        if (total > MinioStorageService.MAX_MULTIPART_PARTS) {
            long suggest =
                    (fileSize + MinioStorageService.MAX_MULTIPART_PARTS - 1)
                            / MinioStorageService.MAX_MULTIPART_PARTS;
            throw new BusinessException(
                    "分片数 "
                            + total
                            + " 超过上限 "
                            + MinioStorageService.MAX_MULTIPART_PARTS
                            + "，请把分片大小调整到 "
                            + (suggest / 1024 / 1024 + 1)
                            + "MB 以上");
        }
        if (total > 1 && chunkSize < minChunkSize()) {
            throw new BusinessException(
                    "分片大小不得小于 " + minChunkSize() / 1024 / 1024 + "MB（MinIO 原生分片限制）");
        }
    }

    private static int totalChunks(long fileSize, int chunkSize) {
        return (int) ((fileSize + chunkSize - 1) / chunkSize);
    }

    /** 末片可小于标准分片大小 */
    private static int expectedChunkSize(SysUploadSession session, int chunkIndex) {
        long offset = (long) chunkIndex * session.getChunkSize();
        return (int) Math.min(session.getChunkSize(), session.getFileSize() - offset);
    }

    private static String normalizeAlgo(String algo) {
        if (!StringUtils.hasText(algo)) {
            return ALGO_TREE;
        }
        String value = algo.trim().toLowerCase(Locale.ROOT);
        if (!ALGO_FULL.equals(value) && !ALGO_TREE.equals(value) && !ALGO_META.equals(value)) {
            throw new BusinessException("不支持的指纹算法：" + algo);
        }
        return value;
    }

    private static String normalizeHash(String hash) {
        String value = hash == null ? "" : hash.trim().toLowerCase(Locale.ROOT);
        if (!HEX_HASH.matcher(value).matches()) {
            throw new BusinessException("文件指纹格式不合法");
        }
        return value;
    }

    private static String extensionOf(String storedName) {
        if (!StringUtils.hasText(storedName)) {
            return "";
        }
        int dot = storedName.lastIndexOf('.');
        return dot < 0 ? "" : storedName.substring(dot);
    }

    private static String statusText(UploadSessionStatus status) {
        return switch (status) {
            case COMPLETED -> "完成";
            case ABORTED -> "取消";
            case UPLOADING -> "在上传中";
        };
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
    }
}
