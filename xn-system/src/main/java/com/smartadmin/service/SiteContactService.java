package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.dto.SiteContactVO;
import com.smartadmin.entity.SysSiteContact;
import com.smartadmin.repository.SysSiteContactRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class SiteContactService {

    private static final long SINGLETON_ID = 1L;
    private static final Set<String> ALLOWED_IMAGE_TYPES =
            Set.of("image/png", "image/jpeg", "image/jpg", "image/webp");

    private final SysSiteContactRepository repository;
    private final ObjectMapper objectMapper;
    private final AppCacheService appCacheService;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    /** 公开读取（管理端首页 / 官网），无需鉴权 */
    public SiteContactVO getPublic() {
        return appCacheService.getSiteContact(
                new tools.jackson.core.type.TypeReference<>() {}, this::loadOrDefault);
    }

    public SiteContactVO getForAdmin() {
        return loadOrDefault();
    }

    @Transactional
    public SiteContactVO update(SiteContactVO request) {
        if (request == null) {
            throw new BusinessException("配置不能为空");
        }
        SiteContactVO normalized = normalize(request);
        SysSiteContact entity =
                repository
                        .findById(SINGLETON_ID)
                        .orElseGet(
                                () -> {
                                    SysSiteContact created = new SysSiteContact();
                                    created.setId(SINGLETON_ID);
                                    return created;
                                });
        try {
            entity.setConfigJson(objectMapper.writeValueAsString(normalized));
        } catch (Exception e) {
            throw new BusinessException("配置序列化失败");
        }
        repository.save(entity);
        appCacheService.evictSiteContact();
        return normalized;
    }

    @Transactional
    public String uploadQrcode(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException("仅支持 png / jpg / webp 图片");
        }
        String original = file.getOriginalFilename();
        String ext = ".jpg";
        if (StringUtils.hasText(original) && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.')).toLowerCase();
        } else if (contentType.contains("png")) {
            ext = ".png";
        } else if (contentType.contains("webp")) {
            ext = ".webp";
        }
        try {
            Path dir = Paths.get(uploadDir, "donation").toAbsolutePath().normalize();
            Files.createDirectories(dir);
            String filename = UUID.randomUUID().toString().replace("-", "") + ext;
            Path target = dir.resolve(filename);
            file.transferTo(target);
            return "/uploads/donation/" + filename;
        } catch (IOException e) {
            throw new BusinessException("上传失败");
        }
    }

    private SiteContactVO loadOrDefault() {
        return normalize(
                repository
                        .findById(SINGLETON_ID)
                        .map(this::parse)
                        .orElseGet(SiteContactVO::defaults));
    }

    private SiteContactVO parse(SysSiteContact entity) {
        if (!StringUtils.hasText(entity.getConfigJson())) {
            return SiteContactVO.defaults();
        }
        try {
            SiteContactVO vo = objectMapper.readValue(entity.getConfigJson(), SiteContactVO.class);
            return vo != null ? vo : SiteContactVO.defaults();
        } catch (Exception e) {
            return SiteContactVO.defaults();
        }
    }

    private SiteContactVO normalize(SiteContactVO vo) {
        if (vo.getContacts() == null) {
            vo.setContacts(new ArrayList<>());
        }
        if (vo.getDonation() == null) {
            vo.setDonation(new SiteContactVO.Donation());
        }
        if (vo.getDonation().getQrcodes() == null) {
            vo.getDonation().setQrcodes(new ArrayList<>());
        }
        if (!StringUtils.hasText(vo.getDonation().getTip())) {
            vo.getDonation().setTip("如果这个项目对你有帮助，欢迎请作者喝杯咖啡");
        }
        for (SiteContactVO.ContactItem item : vo.getContacts()) {
            if (item == null) {
                continue;
            }
            if (item.getLabel() != null) {
                item.setLabel(item.getLabel().trim());
            }
            if (item.getValue() != null) {
                item.setValue(item.getValue().trim());
            }
            if (item.getLink() != null) {
                String link = item.getLink().trim();
                item.setLink(link.isEmpty() ? null : link);
            }
            if (!StringUtils.hasText(item.getIcon())) {
                item.setIcon("Link");
            }
        }
        for (SiteContactVO.Qrcode qr : vo.getDonation().getQrcodes()) {
            if (qr == null) {
                continue;
            }
            if (qr.getLabel() != null) {
                qr.setLabel(qr.getLabel().trim());
            }
            if (qr.getSrc() != null) {
                qr.setSrc(qr.getSrc().trim());
            }
        }
        return vo;
    }
}
