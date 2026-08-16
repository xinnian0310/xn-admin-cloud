package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.config.KkFileViewProperties;
import com.smartadmin.config.MinioProperties;
import com.smartadmin.config.UploadProperties;
import com.smartadmin.dto.AttachmentItem;
import com.smartadmin.dto.FileBrowseVO;
import com.smartadmin.dto.FileInfoVO;
import com.smartadmin.dto.FileTreeNodeVO;
import com.smartadmin.entity.SysFile;
import com.smartadmin.repository.SysFileRepository;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
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
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class FileManageService {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final DateTimeFormatter LOCAL_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** MinIO / 本地上传目录：年/月/日/ */
    private static final DateTimeFormatter DATE_DIR_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** 可安全拼进 URL 的扩展名：点 + 1~16 位字母数字 */
    private static final Pattern EXTENSION_PATTERN = Pattern.compile("\\.[A-Za-z0-9]{1,16}");

    /** kkFileView 常见可预览扩展名（与官方支持列表对齐的常用子集） */
    private static final Set<String> KK_PREVIEW_EXTENSIONS =
            Set.of(
                    // Office / WPS / LibreOffice
                    "doc",
                    "docx",
                    "xls",
                    "xlsx",
                    "xlsm",
                    "ppt",
                    "pptx",
                    "pptm",
                    "csv",
                    "tsv",
                    "dotm",
                    "xlt",
                    "xltm",
                    "dot",
                    "dotx",
                    "xlam",
                    "xla",
                    "pages",
                    "wps",
                    "dps",
                    "et",
                    "ett",
                    "wpt",
                    "odt",
                    "ods",
                    "ots",
                    "odp",
                    "otp",
                    "six",
                    "ott",
                    "fodt",
                    "fods",
                    "vsd",
                    "vsdx",
                    // 文档
                    "pdf",
                    "ofd",
                    "rtf",
                    "epub",
                    "xmind",
                    "bpmn",
                    "eml",
                    "msg",
                    "drawio",
                    // 图片
                    "jpg",
                    "jpeg",
                    "png",
                    "gif",
                    "bmp",
                    "ico",
                    "jfif",
                    "webp",
                    "heic",
                    "heif",
                    "tif",
                    "tiff",
                    "tga",
                    "svg",
                    "wmf",
                    "emf",
                    "psd",
                    "eps",
                    // 文本 / 代码
                    "txt",
                    "xml",
                    "xbrl",
                    "md",
                    "html",
                    "htm",
                    "json",
                    "properties",
                    "log",
                    "java",
                    "php",
                    "py",
                    "js",
                    "ts",
                    "css",
                    "scss",
                    "less",
                    "c",
                    "cpp",
                    "h",
                    "sql",
                    "sh",
                    "bat",
                    "cmd",
                    "yml",
                    "yaml",
                    "ini",
                    "conf",
                    "vue",
                    // 压缩包
                    "zip",
                    "rar",
                    "jar",
                    "tar",
                    "gzip",
                    "gz",
                    "7z",
                    // 音视频
                    // 注：视频容器 ts 与 TypeScript 扩展名冲突，预览扩展名保留代码侧 "ts"
                    "mp3",
                    "wav",
                    "mp4",
                    "flv",
                    "avi",
                    "mov",
                    "rm",
                    "webm",
                    "mkv",
                    "mpeg",
                    "ogg",
                    "mpg",
                    "rmvb",
                    "wmv",
                    "3gp",
                    "swf",
                    // CAD / 3D / 医疗
                    "dwg",
                    "dxf",
                    "dwf",
                    "iges",
                    "igs",
                    "dwt",
                    "dng",
                    "dwfx",
                    "cf2",
                    "plt",
                    "obj",
                    "3ds",
                    "stl",
                    "ply",
                    "gltf",
                    "glb",
                    "off",
                    "3dm",
                    "fbx",
                    "dae",
                    "wrl",
                    "3mf",
                    "ifc",
                    "brep",
                    "step",
                    "fcstd",
                    "bim",
                    "dcm");

    private final RbacService rbacService;
    private final DataScopeService dataScopeService;
    private final MinioStorageService minioStorageService;
    private final MinioProperties minioProperties;
    private final KkFileViewProperties kkFileViewProperties;
    private final SysFileRepository sysFileRepository;
    private final RecycleService recycleService;
    private final UploadProperties uploadProperties;

    public List<FileInfoVO> list(String keyword) {
        rbacService.checkPermission("file:refresh");
        FileBrowseVO browse = browse("", keyword, true);
        return browse.getFiles();
    }

    public FileBrowseVO browse(String prefix, String keyword) {
        return browse(prefix, keyword, false);
    }

    private FileBrowseVO browse(String prefix, String keyword, boolean recursiveFiles) {
        rbacService.checkPermission("file:refresh");
        FileBrowseVO vo = new FileBrowseVO();
        String normalized = MinioStorageService.normalizePrefix(prefix);
        String kw = keyword == null ? "" : keyword.trim();
        vo.setPrefix(normalized);

        if (minioStorageService.isReady()) {
            vo.setStorage("minio");
            MinioStorageService.PrefixListing listing =
                    minioStorageService.listPrefix(
                            normalized, recursiveFiles, kw.isEmpty() ? null : kw);
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
        vo.getFiles()
                .removeIf(
                        f -> {
                            String uploader = f.getUploader();
                            return !StringUtils.hasText(uploader) || !allowed.contains(uploader);
                        });
    }

    public FileTreeNodeVO tree() {
        rbacService.checkPermission("file:refresh");
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
        MinioStorageService.PrefixListing listing =
                minioStorageService.listPrefix(prefix, false, null);
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

    /** 旧附件可能缺 size / uploadedAt，按 objectKey 从 sys_file 补上，避免 JSON 里留下 null。 */
    public void enrichAttachments(List<AttachmentItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        List<String> missing = new ArrayList<>();
        for (AttachmentItem item : items) {
            if (item == null || !StringUtils.hasText(item.getPath())) {
                continue;
            }
            if (item.getSize() == null || !StringUtils.hasText(item.getUploadedAt())) {
                missing.add(item.getPath());
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        Map<String, SysFile> files = new HashMap<>();
        for (SysFile file : sysFileRepository.findByObjectKeyIn(missing)) {
            files.putIfAbsent(file.getObjectKey(), file);
        }
        for (AttachmentItem item : items) {
            if (item == null || !StringUtils.hasText(item.getPath())) {
                continue;
            }
            SysFile file = files.get(item.getPath());
            if (file == null) {
                continue;
            }
            if (item.getSize() == null) {
                item.setSize(file.getSizeBytes());
            }
            if (!StringUtils.hasText(item.getUploadedAt()) && file.getCreatedAt() != null) {
                item.setUploadedAt(file.getCreatedAt().format(LOCAL_DATE_TIME));
            }
        }
    }

    @Transactional
    public FileInfoVO upload(MultipartFile file, String prefix) throws IOException {
        rbacService.checkPermission("file:upload");
        if (missingUpload(file)) {
            throw new BusinessException("请选择文件");
        }
        // 统一按 yyyy/MM/dd/ 落盘；prefix 仅兼容旧入参，不再参与路径
        AllocatedObject target = allocateObject(file.getOriginalFilename());
        String contentType =
                StringUtils.hasText(file.getContentType())
                        ? file.getContentType()
                        : "application/octet-stream";

        if (minioStorageService.isReady()) {
            minioStorageService.upload(file, target.objectKey());
            String url = minioStorageService.publicUrl(target.objectKey());
            SysFile saved =
                    saveMeta(
                            target.objectKey(),
                            target.prefix(),
                            target.displayName(),
                            target.storedName(),
                            target.extension(),
                            contentType,
                            file.getSize(),
                            "minio",
                            minioProperties.getBucket(),
                            url);
            return toDbVO(saved);
        }

        Path targetDir =
                resolveSafePath(target.prefix().substring(0, target.prefix().length() - 1));
        if (!Files.isDirectory(targetDir)) {
            Files.createDirectories(targetDir);
        }
        Path stored = targetDir.resolve(target.storedName());
        file.transferTo(stored.toFile());
        String relative = resolveRoot().relativize(stored).toString().replace('\\', '/');
        String url = uploadProperties.localPublicUrl(relative);
        SysFile saved =
                saveMeta(
                        relative,
                        target.prefix(),
                        target.displayName(),
                        target.storedName(),
                        target.extension(),
                        contentType,
                        file.getSize(),
                        "local",
                        null,
                        url);
        return toDbVO(saved);
    }

    /** 目标对象定位结果：统一 {@code yyyy/MM/dd/} 目录，文件名为 uuid + 扩展名 */
    public record AllocatedObject(
            String prefix,
            String storedName,
            String objectKey,
            String displayName,
            String extension) {}

    /**
     * 为待写入的文件分配落盘位置：{@code yyyy/MM/dd/<uuid><ext>}，原始文件名只进库不进 key。
     *
     * <p>不用原名做 key 有三层考虑：
     *
     * <ol>
     *   <li>同名必然唯一，无需「先查库再写」——分片上传在 init 就要定 key，而元数据行要到 complete 才落库， 查库判重根本盖不住这段窗口，并发传同名文件会互相覆盖
     *   <li>库外对象（手工传进桶、换过库）也不会被覆盖
     *   <li>key 全为 ASCII，中文名 / 空格 / {@code #} 之类不会破坏返回的 URL
     * </ol>
     *
     * <p>列表页展示的是库里的原始文件名，因此用户侧仍然可读。
     */
    public AllocatedObject allocateObject(String originalFilename) {
        String safeName = sanitizeFileName(originalFilename);
        String ext = urlSafeExtension(safeName);
        String storedName = UUID.randomUUID().toString().replace("-", "") + ext;
        String displayName = StringUtils.hasText(safeName) ? safeName : storedName;
        String dir = dateDirPrefix();
        return new AllocatedObject(dir, storedName, dir + storedName, displayName, ext);
    }

    /** 登记已写入存储的对象元数据（分片合并完成后调用） */
    @Transactional
    public FileInfoVO registerUploadedFile(
            String objectKey,
            String prefix,
            String displayName,
            String storedName,
            String extension,
            String contentType,
            long size,
            String storage,
            String bucket,
            String url) {
        return toDbVO(
                saveMeta(
                        objectKey,
                        prefix,
                        displayName,
                        storedName,
                        extension,
                        contentType,
                        size,
                        storage,
                        bucket,
                        url));
    }

    /** 已登记文件的视图；不存在或已进回收站时返回空 */
    public FileInfoVO findRegisteredFile(String objectKey) {
        return sysFileRepository
                .findByObjectKey(objectKey)
                .filter(f -> f.getDeletedAt() == null)
                .map(this::toDbVO)
                .orElse(null);
    }

    /** 本地上传根目录 */
    public Path uploadRoot() {
        return resolveRoot();
    }

    /** 当日上传目录，形如 {@code 2026/08/15/} */
    private static String dateDirPrefix() {
        return LocalDate.now().format(DATE_DIR_FORMATTER) + "/";
    }

    /** 没选文件才拒绝；0 字节空文件（空 txt 等）允许上传 */
    private static boolean missingUpload(MultipartFile file) {
        return file == null
                || (!StringUtils.hasText(file.getOriginalFilename()) && file.getSize() == 0);
    }

    /** 去掉路径、替换非法字符，保留可读原名 */
    private static String sanitizeFileName(String original) {
        if (!StringUtils.hasText(original)) {
            return "";
        }
        String name = Paths.get(original.replace('\\', '/')).getFileName().toString().trim();
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (!StringUtils.hasText(name) || ".".equals(name) || "..".equals(name)) {
            return "";
        }
        return name;
    }

    /**
     * 取扩展名并保证 key 可直接拼进 URL：只认 {@code .} + 字母数字，其余（含中文、空格、超长后缀）一律丢弃。
     *
     * <p>扩展名要保留在 key 里，kkFileView 等下游按 URL 后缀判类型。
     */
    private static String urlSafeExtension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return "";
        }
        String ext = fileName.substring(dot);
        return EXTENSION_PATTERN.matcher(ext).matches() ? ext : "";
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
        List<SysFile> dbFiles =
                recursive
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
            FileBrowseVO vo,
            String prefix,
            String keyword,
            boolean recursive,
            String expectedStorage) {
        List<SysFile> dbFiles =
                recursive
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
        vo.setExtension(f.getExtension());
        vo.setContentType(f.getContentType());
        vo.setSize(f.getSizeBytes() == null ? 0 : f.getSizeBytes());
        vo.setDirectory(false);
        vo.setStorage(f.getStorage());
        vo.setUrl(f.getUrl());
        vo.setPreviewUrl(buildPreviewUrl(f.getUrl(), f.getOriginalName(), f.getExtension()));
        vo.setUploader(f.getUploader());
        vo.setPrefix(f.getPrefix());
        if (f.getCreatedAt() != null) {
            vo.setLastModified(
                    f.getCreatedAt()
                            .format(
                                    java.time.format.DateTimeFormatter.ofPattern(
                                            "yyyy-MM-dd HH:mm:ss")));
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
        Path current =
                normalized.isEmpty()
                        ? root
                        : resolveSafePath(normalized.substring(0, normalized.length() - 1));
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
                    dirVo.setPath(
                            dirVo.getPath().endsWith("/")
                                    ? dirVo.getPath()
                                    : dirVo.getPath() + "/");
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
            vo.setUrl(uploadProperties.localPublicUrl(relative));
            vo.setPreviewUrl(buildPreviewUrl(vo.getUrl(), vo.getName(), vo.getExtension()));
        }
        try {
            vo.setSize(Files.isDirectory(path) ? 0 : Files.size(path));
            vo.setLastModified(
                    FORMATTER.format(
                            Instant.ofEpochMilli(Files.getLastModifiedTime(path).toMillis())));
        } catch (IOException ignored) {
            vo.setSize(0);
        }
        return vo;
    }

    private String buildPreviewUrl(String fileUrl, String fileName, String extension) {
        if (!kkFileViewProperties.isEnabled()
                || !StringUtils.hasText(kkFileViewProperties.getBaseUrl())) {
            return null;
        }
        String absolute = uploadProperties.absoluteForPreview(fileUrl);
        if (!StringUtils.hasText(absolute)) {
            return null;
        }
        String ext = StringUtils.hasText(extension) ? extension : extractExtension(fileName);
        if (!isKkPreviewable(ext)) {
            return null;
        }
        String base = kkFileViewProperties.getBaseUrl().replaceAll("/+$", "");
        String encoded =
                Base64.getEncoder().encodeToString(absolute.getBytes(StandardCharsets.UTF_8));
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
        return Paths.get(uploadProperties.getDir()).toAbsolutePath().normalize();
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
