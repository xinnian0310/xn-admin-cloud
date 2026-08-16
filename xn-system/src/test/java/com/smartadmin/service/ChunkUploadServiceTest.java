package com.smartadmin.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartadmin.common.BusinessException;
import com.smartadmin.config.MinioProperties;
import com.smartadmin.config.UploadProperties;
import com.smartadmin.dto.ChunkUploadCheckRequest;
import com.smartadmin.dto.ChunkUploadCheckVO;
import com.smartadmin.dto.ChunkUploadInitRequest;
import com.smartadmin.dto.ChunkUploadSessionVO;
import com.smartadmin.dto.FileInfoVO;
import com.smartadmin.entity.SysUploadSession;
import com.smartadmin.entity.UploadSessionStatus;
import com.smartadmin.repository.SysUploadSessionRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/**
 * 分片上传服务：走本地回退存储（MinIO 不可用）跑完整流程，验证切片、续传清单、分片校验、合并与取消。
 *
 * <p>本地回退与 MinIO 共用同一套流程编排，差异仅在「分片存哪里」，因此这里能覆盖绝大部分逻辑。
 */
@ExtendWith(MockitoExtension.class)
class ChunkUploadServiceTest {

    /** 本地回退无 5MiB 下限，用小分片让用例保持轻量 */
    private static final int CHUNK_SIZE = 1024;

    private static final String HASH = "a".repeat(64);

    @TempDir Path tempDir;

    @Mock private RbacService rbacService;
    @Mock private MinioStorageService minioStorageService;
    @Mock private MinioProperties minioProperties;
    @Mock private FileManageService fileManageService;
    @Mock private SysUploadSessionRepository sessionRepository;

    private UploadProperties uploadProperties;
    private ChunkUploadService service;
    private Map<String, SysUploadSession> saved;
    private Path uploadRoot;

    @BeforeEach
    void setUp() throws IOException {
        uploadRoot = Files.createDirectories(tempDir.resolve("uploads"));
        uploadProperties = new UploadProperties();
        uploadProperties.setChunkDir(tempDir.resolve("chunk-tmp").toString());

        saved = new LinkedHashMap<>();
        lenient().when(minioStorageService.isReady()).thenReturn(false);
        lenient().when(fileManageService.uploadRoot()).thenReturn(uploadRoot);
        lenient()
                .when(fileManageService.allocateObject(anyString()))
                .thenAnswer(
                        invocation -> {
                            String name = invocation.getArgument(0);
                            return new FileManageService.AllocatedObject(
                                    "2026/08/15/", name, "2026/08/15/" + name, name, ".bin");
                        });
        lenient()
                .when(sessionRepository.save(any(SysUploadSession.class)))
                .thenAnswer(
                        invocation -> {
                            SysUploadSession session = invocation.getArgument(0);
                            saved.put(session.getUploadId(), session);
                            return session;
                        });
        lenient()
                .when(sessionRepository.findByUploadId(anyString()))
                .thenAnswer(
                        invocation -> Optional.ofNullable(saved.get(invocation.getArgument(0))));
        lenient()
                .when(
                        sessionRepository.findResumableByFingerprint(
                                anyString(), anyString(), anyLong(), any(), any()))
                .thenReturn(List.of());
        lenient()
                .when(
                        sessionRepository.findCompletedByFingerprint(
                                anyString(), anyString(), anyLong(), any()))
                .thenReturn(List.of());

        service =
                new ChunkUploadService(
                        rbacService,
                        minioStorageService,
                        minioProperties,
                        uploadProperties,
                        fileManageService,
                        sessionRepository);
    }

    // ------------------------------------------------------------------ 主流程

    @Test
    void uploadsAndMergesAllChunksIntoTheOriginalBytes() throws IOException {
        byte[] content = payload(CHUNK_SIZE * 3 + 137);
        ChunkUploadSessionVO session = init(content.length);

        assertEquals(4, session.getTotalChunks(), "末片允许小于标准分片大小");
        assertEquals("local", session.getStorage());
        assertTrue(session.getUploadedChunks().isEmpty());

        for (int index = 0; index < session.getTotalChunks(); index++) {
            uploadChunk(session, content, index);
        }

        FileInfoVO file = complete(session);
        assertNotNull(file);
        assertArrayEquals(content, Files.readAllBytes(mergedFile()), "合并结果必须与原文件逐字节一致");
        assertEquals(UploadSessionStatus.COMPLETED, saved.get(session.getUploadId()).getStatus());
    }

    @Test
    void statusReportsUploadedChunksSoTheClientCanResume() {
        byte[] content = payload(CHUNK_SIZE * 4);
        ChunkUploadSessionVO session = init(content.length);

        uploadChunk(session, content, 0);
        uploadChunk(session, content, 2);

        ChunkUploadSessionVO status = service.status(session.getUploadId());
        assertEquals(List.of(0, 2), status.getUploadedChunks());
        assertEquals(2L * CHUNK_SIZE, status.getUploadedBytes());
        assertEquals("UPLOADING", status.getStatus());
    }

    @Test
    void reuploadingTheSameChunkIsIdempotent() throws IOException {
        byte[] content = payload(CHUNK_SIZE * 2);
        ChunkUploadSessionVO session = init(content.length);

        uploadChunk(session, content, 0);
        uploadChunk(session, content, 0);
        uploadChunk(session, content, 1);

        assertEquals(List.of(0, 1), service.status(session.getUploadId()).getUploadedChunks());
        complete(session);
        assertArrayEquals(content, Files.readAllBytes(mergedFile()));
    }

    @Test
    void completeIsIdempotentAfterAMergeAlreadySucceeded() throws IOException {
        byte[] content = payload(CHUNK_SIZE * 2);
        ChunkUploadSessionVO session = init(content.length);
        uploadChunk(session, content, 0);
        uploadChunk(session, content, 1);

        complete(session);
        // 临时分片已清理，再次合并只能靠「已合并」判定跳过，否则会因缺片失败
        complete(session);
        assertArrayEquals(content, Files.readAllBytes(mergedFile()));
    }

    // ------------------------------------------------------------------ 校验

    @Test
    void rejectsChunkWhoseHashDoesNotMatch() {
        byte[] content = payload(CHUNK_SIZE * 2);
        ChunkUploadSessionVO session = init(content.length);

        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.uploadPart(
                                        session.getUploadId(),
                                        0,
                                        "b".repeat(64),
                                        chunkFile(content, 0)));
        assertTrue(ex.getMessage().contains("校验失败"));
        assertTrue(service.status(session.getUploadId()).getUploadedChunks().isEmpty());
    }

    @Test
    void rejectsChunkWhoseSizeDoesNotMatch() {
        ChunkUploadSessionVO session = init(CHUNK_SIZE * 2);
        MockMultipartFile truncated = new MockMultipartFile("file", new byte[CHUNK_SIZE - 1]);

        BusinessException ex =
                assertThrows(
                        BusinessException.class,
                        () -> service.uploadPart(session.getUploadId(), 0, null, truncated));
        assertTrue(ex.getMessage().contains("大小不符"));
    }

    @Test
    void rejectsChunkIndexOutOfRange() {
        byte[] content = payload(CHUNK_SIZE * 2);
        ChunkUploadSessionVO session = init(content.length);

        assertTrue(
                assertThrows(
                                BusinessException.class,
                                () ->
                                        service.uploadPart(
                                                session.getUploadId(),
                                                2,
                                                null,
                                                chunkFile(content, 0)))
                        .getMessage()
                        .contains("越界"));
    }

    @Test
    void refusesToMergeWhileChunksAreMissing() {
        byte[] content = payload(CHUNK_SIZE * 3);
        ChunkUploadSessionVO session = init(content.length);
        uploadChunk(session, content, 0);

        BusinessException ex =
                assertThrows(
                        BusinessException.class, () -> service.complete(session.getUploadId()));
        assertTrue(ex.getMessage().contains("个分片未上传"));
        assertFalse(Files.exists(mergedFile()));
    }

    @Test
    void rejectsUnknownSessionAndUnknownAlgorithm() {
        assertTrue(
                assertThrows(BusinessException.class, () -> service.status("not-exist"))
                        .getMessage()
                        .contains("会话不存在"));

        ChunkUploadInitRequest request = initRequest(CHUNK_SIZE);
        request.setHashAlgo("md5");
        assertTrue(
                assertThrows(BusinessException.class, () -> service.init(request))
                        .getMessage()
                        .contains("不支持的指纹算法"));

        ChunkUploadInitRequest badHash = initRequest(CHUNK_SIZE);
        badHash.setFileHash("zzz");
        assertTrue(
                assertThrows(BusinessException.class, () -> service.init(badHash))
                        .getMessage()
                        .contains("指纹格式"));
    }

    // ------------------------------------------------------------------ 取消

    @Test
    void cancelDropsUploadedChunksAndBlocksFurtherUploads() {
        byte[] content = payload(CHUNK_SIZE * 2);
        ChunkUploadSessionVO session = init(content.length);
        uploadChunk(session, content, 0);

        service.cancel(session.getUploadId());

        assertEquals(UploadSessionStatus.ABORTED, saved.get(session.getUploadId()).getStatus());
        assertFalse(Files.exists(tempDir.resolve("chunk-tmp").resolve(session.getUploadId())));
        assertTrue(
                assertThrows(
                                BusinessException.class,
                                () ->
                                        service.uploadPart(
                                                session.getUploadId(),
                                                1,
                                                null,
                                                chunkFile(content, 1)))
                        .getMessage()
                        .contains("已取消"));
        assertTrue(
                assertThrows(BusinessException.class, () -> service.complete(session.getUploadId()))
                        .getMessage()
                        .contains("已取消"));
    }

    // ------------------------------------------------------------------ 秒传探测

    @Test
    void checkReportsAnExistingFileAsInstantlyUploadable() {
        SysUploadSession done = new SysUploadSession();
        done.setObjectKey("2026/08/15/old.bin");
        when(sessionRepository.findCompletedByFingerprint("sha256-tree", HASH, 2048L, CHUNK_SIZE))
                .thenReturn(List.of(done));
        FileInfoVO existing = new FileInfoVO();
        when(fileManageService.findRegisteredFile("2026/08/15/old.bin")).thenReturn(existing);

        ChunkUploadCheckVO vo = service.check(checkRequest("sha256-tree"));

        assertTrue(vo.isExists());
        assertEquals(existing, vo.getFile());
    }

    @Test
    void metaFingerprintNeverTriggersAnInstantUpload() {
        // 元信息相同不能证明内容相同，因此 meta 不得查秒传候选
        ChunkUploadCheckVO vo = service.check(checkRequest("meta"));

        assertFalse(vo.isExists());
        assertNull(vo.getFile());
        verify(sessionRepository, never())
                .findCompletedByFingerprint(anyString(), anyString(), anyLong(), any());
    }

    // ------------------------------------------------------------------ 工具

    private ChunkUploadInitRequest initRequest(long fileSize) {
        ChunkUploadInitRequest request = new ChunkUploadInitRequest();
        request.setFileHash(HASH);
        request.setHashAlgo("sha256-tree");
        request.setFileName("demo.bin");
        request.setFileSize(fileSize);
        request.setChunkSize(CHUNK_SIZE);
        request.setContentType("application/octet-stream");
        return request;
    }

    private ChunkUploadCheckRequest checkRequest(String algo) {
        ChunkUploadCheckRequest request = new ChunkUploadCheckRequest();
        request.setFileHash(HASH);
        request.setHashAlgo(algo);
        request.setFileName("demo.bin");
        request.setFileSize(2048L);
        request.setChunkSize(CHUNK_SIZE);
        return request;
    }

    private ChunkUploadSessionVO init(long fileSize) {
        return service.init(initRequest(fileSize));
    }

    private void uploadChunk(ChunkUploadSessionVO session, byte[] content, int index) {
        byte[] slice = slice(content, index);
        service.uploadPart(
                session.getUploadId(), index, sha256Hex(slice), chunkFile(content, index));
    }

    private FileInfoVO complete(ChunkUploadSessionVO session) {
        when(fileManageService.registerUploadedFile(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyLong(),
                        anyString(),
                        any(),
                        anyString()))
                .thenReturn(new FileInfoVO());
        return service.complete(session.getUploadId());
    }

    private Path mergedFile() {
        return uploadRoot.resolve("2026/08/15/demo.bin");
    }

    private static MockMultipartFile chunkFile(byte[] content, int index) {
        return new MockMultipartFile("file", slice(content, index));
    }

    private static byte[] slice(byte[] content, int index) {
        int start = index * CHUNK_SIZE;
        int end = Math.min(start + CHUNK_SIZE, content.length);
        byte[] slice = new byte[end - start];
        System.arraycopy(content, start, slice, 0, slice.length);
        return slice;
    }

    private static byte[] payload(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) (i * 31 + 7);
        }
        return data;
    }

    private static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
