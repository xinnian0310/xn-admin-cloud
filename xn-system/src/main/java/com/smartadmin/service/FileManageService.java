package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.config.KkFileViewProperties;
import com.smartadmin.config.MinioProperties;
import com.smartadmin.dto.FileBrowseVO;
import com.smartadmin.dto.FileInfoVO;
import com.smartadmin.dto.FileTreeNodeVO;
import com.smartadmin.entity.SysFile;
import com.smartadmin.repository.SysFileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class FileManageService {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /** kkFileView 常见可预览扩展名（与官方支持列表对齐的常用子集） */
    private static final Set<String> KK_PREVIEW_EXTENSIONS = Set.of(
            // Office / WPS / LibreOffice
            "doc", "docx", "xls", "xlsx", "xlsm", "ppt", "pptx", "pptm", "csv", "tsv",
            "dotm", "xlt", "xltm", "dot", "dotx", "xlam", "xla", "pages",
            "wps", "dps", "et", "ett", "wpt",
            "odt", "ods", "ots", "odp", "otp", "six", "ott", "fodt", "fods",
            "vsd", "vsdx",
            // 文档
            "pdf", "ofd", "rtf", "epub", "xmind", "bpmn", "eml", "msg", "drawio",
            // 图片
            "jpg", "jpeg", "png", "gif", "bmp", "ico", "jfif", "webp", "heic", "heif",
            "tif", "tiff", "tga", "svg", "wmf", "emf", "psd", "eps",
            // 文本 / 代码
            "txt", "xml", "xbrl", "md", "html", "htm", "json", "properties", "log",
            "java", "php", "py", "js", "ts", "css", "scss", "less", "c", "cpp", "h",
            "sql", "sh", "bat", "cmd", "yml", "yaml", "ini", "conf", "vue",
            // 压缩包
            "zip", "rar", "jar", "tar", "gzip", "gz", "7z",
            // 音视频
            // 注：视频容器 ts 与 TypeScript 扩展名冲突，预览扩展名保留代码侧 "ts"
            "mp3", "wav", "mp4", "flv", "avi", "mov", "rm", "webm", "mkv",
            "mpeg", "ogg", "mpg", "rmvb", "wmv", "3gp", "swf",
            // CAD / 3D / 医疗
            "dwg", "dxf", "dwf", "iges", "igs", "dwt", "dng", "dwfx", "cf2", "plt",
            "obj", "3ds", "stl", "ply", "gltf", "glb", "off", "3dm", "fbx", "dae",
            "wrl", "3mf", "ifc", "brep", "step", "fcstd", "bim", "dcm"
    );

    private final RbacService rbacService;
    private final DataScopeService dataScopeService;
    private final MinioStorageService minioStorageService;
    private final MinioProperties minioProperties;
    private final KkFileViewProperties kkFileViewProperties;
    private final SysFileRepository sysFileRepository;
    private final RecycleService recycleService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${server.port:8080}")
    private int serverPort;

    public List<FileInfoVO> list(String keyword) {
        rbacService.checkPermission("file:view");
        FileBrowseVO browse = browse("", keyword, true);
        return browse.getFiles();
    }

    public FileBrowseVO browse(String prefix, String keyword) {
        return browse(prefix, keyword, false);
    }

    private FileBrowseVO browse(String prefix, String keyword, boolean recursiveFiles) {
        rbacService.checkPermission("file:view");
        FileBrowseVO vo = new FileBrowseVO();
        String normalized = MinioStorageService.normalizePrefix(prefix);
        String kw = keyword == null ? "" : keyword.trim();
        vo.setPrefix(normalized);

        if (minioStorageService.isReady()) {
            vo.setStorage("minio");
            MinioStorageService.PrefixListing listing =
                    minioStorageService.listPrefix(normalized, recursiveFiles, kw.isEmpty() ? null : kw);
            for (MinioStorageService.ObjectInfo dir : listing.dirs()) {
                vo.getDirs().add(toMinioVO(dir));
            }
            mergeFiles(vo, listing.files(), normalized, kw, recursiveFiles);
            applyDataScope(vo);
            vo.getDirs().sort(Comparator.comparing(FileInfoVO::getPath));
            vo.getFiles().sort(Comparator.comparing(FileInfoVO::getPath));
            return vo;
        }

        FileBrowseVO local = browseLocal(prefix, keyword, recursiveFiles);
        mergeDbOnlyFiles(local, normalized, kw, recursiveFiles, "local");
        applyDataScope(local);
        return local;
    }

    private void applyDataScope(FileBrowseVO vo) {
        DataScopeService.UsernameFilter filter = dataScopeService.resolveUsernameFilter();
        if (filter.unrestricted()) {
            return;
        }
        Set<String> allowed = new HashSet<>(filter.usernames());
        vo.getFiles().removeIf(f -> {
            String uploader = f.getUploader();
            return !StringUtils.hasText(uploader) || !allowed.contains(uploader);
        });
    }

    public FileTreeNodeVO tree() {
        rbacService.checkPermission("file:view");
        FileTreeNodeVO root = new FileTreeNodeVO();
        root.setId("");
        root.setPath("");
        if (minioStorageService.isReady()) {
            root.setLabel(minioProperties.getBucket());
            collectMinioDirs(root, "");
            return root;
        }
        root.setLabel("uploads");
        Path rootPath = resolveRoot();
        try {
            Files.createDirectories(rootPath);
        } catch (IOException ex) {
            throw new BusinessException("创建上传目录失败：" + rootPath);
        }
        buildLocalTree(root, rootPath, rootPath);
        return root;
    }

    private void collectMinioDirs(FileTreeNodeVO parent, String prefix) {
        MinioStorageService.PrefixListing listing = minioStorageService.listPrefix(prefix, false, null);
        for (MinioStorageService.ObjectInfo dir : listing.dirs()) {
            FileTreeNodeVO child = new FileTreeNodeVO();
            child.setId(dir.key());
            child.setPath(dir.key());
            child.setLabel(dir.name());
            parent.getChildren().add(child);
            collectMinioDirs(child, dir.key());
        }
        parent.getChildren().sort(Comparator.comparing(FileTreeNodeVO::getLabel));
    }

    @Transactional
    public FileInfoVO upload(MultipartFile file, String prefix) throws IOException {
        rbacService.checkPermission("file:upload");
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择文件");
        }
        String original = file.getOriginalFilename();
        String ext = "";
        if (StringUtils.hasText(original) && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.'));
        }
        String storedName = UUID.randomUUID().toString().replace("-", "") + ext;
        // 严格按当前目录上传；根路径 prefix 为空，不再默认落到 files/
        String dir = MinioStorageService.normalizePrefix(prefix);
        String contentType = StringUtils.hasText(file.getContentType())
                ? file.getContentType()
                : "application/octet-stream";
        String displayName = StringUtils.hasText(original) ? original : storedName;

        if (minioStorageService.isReady()) {
            String objectKey = dir + storedName;
            minioStorageService.upload(file, objectKey);
            String url = minioStorageService.publicUrl(objectKey);
            SysFile saved = saveMeta(
                    objectKey, dir, displayName, storedName, ext, contentType,
                    file.getSize(), "minio", minioProperties.getBucket(), url);
            return toDbVO(saved);
        }

        Path targetDir = dir.isEmpty()
                ? resolveRoot()
                : resolveSafePath(dir.substring(0, dir.length() - 1));
        if (!Files.isDirectory(targetDir)) {
            Files.createDirectories(targetDir);
        }
        Path target = targetDir.resolve(storedName);
        file.transferTo(target.toFile());
        String relative = resolveRoot().relativize(target).toString().replace('\\', '/');
        String url = "http://127.0.0.1:" + serverPort + "/uploads/" + relative;
        SysFile saved = saveMeta(
                relative, dir, displayName, storedName, ext, contentType,
                file.getSize(), "local", null, url);
        return toDbVO(saved);
    }

    @Transactional
    public void mkdir(String path) throws IOException {
        rbacService.checkPermission("file:mkdir");
        String dir = MinioStorageService.normalizePrefix(path);
        if (!StringUtils.hasText(dir)) {
            throw new BusinessException("目录路径不能为空");
        }
        if (minioStorageService.isReady()) {
            minioStorageService.mkdir(dir);
            return;
        }
        Path target = resolveSafePath(dir.substring(0, dir.length() - 1));
        Files.createDirectories(target);
    }

    @Transactional
    public void delete(String relativePath) throws IOException {
        rbacService.checkPermission("file:delete");
        String key = relativePath == null ? "" : relativePath.replace('\\', '/');
        SysFile meta = sysFileRepository.findByObjectKey(key).orElse(null);
        if (meta != null) {
            recycleService.softDeleteFile(meta);
            return;
        }
        // 无元数据的遗留文件：仍直接硬删（无法进回收站）
        if (minioStorageService.isReady() && minioProperties.isEnabled()) {
            minioStorageService.delete(key);
            return;
        }
        Path target = resolveSafePath(key);
        if (!Files.exists(target)) {
            throw new BusinessException("文件不存在");
        }
        if (Files.isDirectory(target)) {
            throw new BusinessException("不支持删除目录");
        }
        Files.delete(target);
    }

    private SysFile saveMeta(
            String objectKey,
            String prefix,
            String originalName,
            String storedName,
            String extension,
            String contentType,
            long size,
            String storage,
            String bucket,
            String url) {
        SysFile entity = sysFileRepository.findByObjectKey(objectKey).orElseGet(SysFile::new);
        entity.setObjectKey(objectKey);
        entity.setPrefix(prefix == null ? "" : prefix);
        entity.setOriginalName(originalName);
        entity.setStoredName(storedName);
        entity.setExtension(extension);
        entity.setContentType(contentType);
        entity.setSizeBytes(size);
        entity.setStorage(storage);
        entity.setBucket(bucket);
        entity.setUrl(url);
        entity.setUploader(RbacService.currentUsername());
        return sysFileRepository.save(entity);
    }

    private void mergeFiles(
            FileBrowseVO vo,
            List<MinioStorageService.ObjectInfo> storageFiles,
            String prefix,
            String keyword,
            boolean recursive) {
        List<SysFile> dbFiles = recursive
                ? sysFileRepository.searchAll(keyword)
                : sysFileRepository.findByPrefix(prefix, keyword);
        Map<String, SysFile> byKey = new HashMap<>();
        for (SysFile f : dbFiles) {
            byKey.put(f.getObjectKey(), f);
        }
        Set<String> recycledKeys = new HashSet<>(sysFileRepository.findDeletedObjectKeys());
        Set<String> seen = new HashSet<>();
        for (MinioStorageService.ObjectInfo obj : storageFiles) {
            if (recycledKeys.contains(obj.key())) {
                continue;
            }
            seen.add(obj.key());
            SysFile meta = byKey.get(obj.key());
            if (meta != null) {
                vo.getFiles().add(toDbVO(meta));
            } else {
                vo.getFiles().add(toMinioVO(obj));
            }
        }
        for (SysFile meta : dbFiles) {
            if (seen.contains(meta.getObjectKey())) {
                continue;
            }
            if (recursive || prefix.equals(meta.getPrefix() == null ? "" : meta.getPrefix())) {
                vo.getFiles().add(toDbVO(meta));
            }
        }
    }

    private void mergeDbOnlyFiles(
            FileBrowseVO vo, String prefix, String keyword, boolean recursive, String expectedStorage) {
        List<SysFile> dbFiles = recursive
                ? sysFileRepository.searchAll(keyword)
                : sysFileRepository.findByPrefix(prefix, keyword);
        Set<String> existing = new HashSet<>();
        for (FileInfoVO f : vo.getFiles()) {
            existing.add(f.getPath());
        }
        for (SysFile meta : dbFiles) {
            if (expectedStorage != null && !expectedStorage.equals(meta.getStorage())) {
                continue;
            }
            if (existing.contains(meta.getObjectKey())) {
                // replace with richer DB meta
                vo.getFiles().removeIf(f -> meta.getObjectKey().equals(f.getPath()));
            }
            vo.getFiles().add(toDbVO(meta));
        }
        vo.getFiles().sort(Comparator.comparing(FileInfoVO::getPath));
    }

    private FileInfoVO toDbVO(SysFile f) {
        FileInfoVO vo = new FileInfoVO();
        vo.setId(f.getId());
        vo.setPath(f.getObjectKey());
        vo.setName(f.getOriginalName());
        vo.setStoredName(f.getStoredName());
        vo.setExtension(f.getExtension());
        vo.setContentType(f.getContentType());
        vo.setSize(f.getSizeBytes() == null ? 0 : f.getSizeBytes());
        vo.setDirectory(false);
        vo.setStorage(f.getStorage());
        vo.setBucket(f.getBucket());
        vo.setUrl(f.getUrl());
        vo.setPreviewUrl(buildPreviewUrl(f.getUrl(), f.getOriginalName(), f.getExtension()));
        vo.setUploader(f.getUploader());
        vo.setPrefix(f.getPrefix());
        if (f.getCreatedAt() != null) {
            vo.setLastModified(f.getCreatedAt().format(
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        return vo;
    }

    private FileInfoVO toMinioVO(MinioStorageService.ObjectInfo obj) {
        FileInfoVO vo = new FileInfoVO();
        vo.setPath(obj.key());
        vo.setName(obj.name());
        vo.setSize(obj.size());
        vo.setDirectory(obj.directory());
        vo.setLastModified(obj.lastModified());
        vo.setStorage("minio");
        if (!obj.directory()) {
            vo.setExtension(extractExtension(obj.name()));
            vo.setUrl(minioStorageService.publicUrl(obj.key()));
            vo.setPreviewUrl(buildPreviewUrl(vo.getUrl(), obj.name(), vo.getExtension()));
        }
        return vo;
    }

    private FileBrowseVO browseLocal(String prefix, String keyword, boolean recursiveFiles) {
        FileBrowseVO vo = new FileBrowseVO();
        vo.setStorage("local");
        String normalized = MinioStorageService.normalizePrefix(prefix);
        vo.setPrefix(normalized);
        Path root = resolveRoot();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new BusinessException("创建上传目录失败：" + root);
        }
        Path current = normalized.isEmpty() ? root : resolveSafePath(normalized.substring(0, normalized.length() - 1));
        if (!Files.isDirectory(current)) {
            return vo;
        }
        if (recursiveFiles) {
            collectFiles(root, current, keyword, vo.getFiles());
            vo.getFiles().sort(Comparator.comparing(FileInfoVO::getPath));
            return vo;
        }
        try (Stream<Path> stream = Files.list(current)) {
            for (Path path : stream.toList()) {
                if (Files.isDirectory(path)) {
                    FileInfoVO dirVo = toLocalVO(path, root);
                    dirVo.setDirectory(true);
                    dirVo.setPath(dirVo.getPath().endsWith("/") ? dirVo.getPath() : dirVo.getPath() + "/");
                    if (!StringUtils.hasText(keyword)
                            || dirVo.getPath().contains(keyword.trim())
                            || dirVo.getName().contains(keyword.trim())) {
                        vo.getDirs().add(dirVo);
                    }
                } else {
                    FileInfoVO fileVo = toLocalVO(path, root);
                    if (".keep".equals(fileVo.getName())) {
                        continue;
                    }
                    if (!StringUtils.hasText(keyword)
                            || fileVo.getPath().contains(keyword.trim())
                            || fileVo.getName().contains(keyword.trim())) {
                        vo.getFiles().add(fileVo);
                    }
                }
            }
        } catch (IOException ex) {
            throw new BusinessException("读取文件列表失败：" + current + "（" + ex.getMessage() + "）");
        }
        vo.getDirs().sort(Comparator.comparing(FileInfoVO::getPath));
        vo.getFiles().sort(Comparator.comparing(FileInfoVO::getPath));
        return vo;
    }

    private void buildLocalTree(FileTreeNodeVO node, Path root, Path current) {
        try (Stream<Path> stream = Files.list(current)) {
            for (Path path : stream.toList()) {
                if (!Files.isDirectory(path)) {
                    continue;
                }
                String relative = root.relativize(path).toString().replace('\\', '/');
                FileTreeNodeVO child = new FileTreeNodeVO();
                child.setId(relative + "/");
                child.setPath(relative + "/");
                child.setLabel(path.getFileName().toString());
                node.getChildren().add(child);
                buildLocalTree(child, root, path);
            }
        } catch (IOException ex) {
            throw new BusinessException("读取目录树失败：" + current);
        }
        node.getChildren().sort(Comparator.comparing(FileTreeNodeVO::getLabel));
    }

    private void collectFiles(Path root, Path current, String keyword, List<FileInfoVO> result) {
        if (!Files.isDirectory(current)) {
            return;
        }
        try (Stream<Path> stream = Files.list(current)) {
            for (Path path : stream.toList()) {
                if (Files.isDirectory(path)) {
                    collectFiles(root, path, keyword, result);
                } else {
                    FileInfoVO vo = toLocalVO(path, root);
                    if (".keep".equals(vo.getName())) {
                        continue;
                    }
                    if (!StringUtils.hasText(keyword)
                            || vo.getPath().contains(keyword.trim())
                            || vo.getName().contains(keyword.trim())) {
                        result.add(vo);
                    }
                }
            }
        } catch (IOException ex) {
            throw new BusinessException("读取文件列表失败：" + current + "（" + ex.getMessage() + "）");
        }
    }

    private FileInfoVO toLocalVO(Path path, Path root) {
        FileInfoVO vo = new FileInfoVO();
        String relative = root.relativize(path).toString().replace('\\', '/');
        vo.setPath(relative);
        vo.setName(path.getFileName().toString());
        vo.setDirectory(Files.isDirectory(path));
        vo.setStorage("local");
        if (!vo.isDirectory()) {
            vo.setExtension(extractExtension(vo.getName()));
            vo.setUrl("http://127.0.0.1:" + serverPort + "/uploads/" + relative);
            vo.setPreviewUrl(buildPreviewUrl(vo.getUrl(), vo.getName(), vo.getExtension()));
        }
        try {
            vo.setSize(Files.isDirectory(path) ? 0 : Files.size(path));
            vo.setLastModified(FORMATTER.format(Instant.ofEpochMilli(Files.getLastModifiedTime(path).toMillis())));
        } catch (IOException ignored) {
            vo.setSize(0);
        }
        return vo;
    }

    private String buildPreviewUrl(String fileUrl, String fileName, String extension) {
        if (!kkFileViewProperties.isEnabled() || !StringUtils.hasText(kkFileViewProperties.getBaseUrl())) {
            return null;
        }
        if (!StringUtils.hasText(fileUrl)) {
            return null;
        }
        String ext = StringUtils.hasText(extension) ? extension : extractExtension(fileName);
        if (!isKkPreviewable(ext)) {
            return null;
        }
        String base = kkFileViewProperties.getBaseUrl().replaceAll("/+$", "");
        String encoded = Base64.getEncoder().encodeToString(fileUrl.getBytes(StandardCharsets.UTF_8));
        return base + "/onlinePreview?url=" + URLEncoder.encode(encoded, StandardCharsets.UTF_8);
    }

    private boolean isKkPreviewable(String extension) {
        if (!StringUtils.hasText(extension)) {
            return false;
        }
        return KK_PREVIEW_EXTENSIONS.contains(extension.trim().toLowerCase());
    }

    private String extractExtension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(idx + 1).toLowerCase();
    }

    private Path resolveRoot() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    private Path resolveSafePath(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            throw new BusinessException("路径不能为空");
        }
        String normalized = relativePath.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.contains("..") || normalized.startsWith("/")) {
            throw new BusinessException("非法路径");
        }
        Path root = resolveRoot();
        Path target = root.resolve(normalized).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException("非法路径");
        }
        return target;
    }
}
