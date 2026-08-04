package com.smartadmin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartadmin.common.BusinessException;
import com.smartadmin.dto.PageResult;
import com.smartadmin.dto.RecycleBinVO;
import com.smartadmin.entity.SysFile;
import com.smartadmin.entity.SysRecycleBin;
import com.smartadmin.entity.User;
import com.smartadmin.repository.SysFileRepository;
import com.smartadmin.repository.SysRecycleBinRepository;
import com.smartadmin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecycleService {

    public static final String BIZ_USER = "USER";
    public static final String BIZ_FILE = "FILE";

    private final SysRecycleBinRepository recycleBinRepository;
    private final UserRepository userRepository;
    private final SysFileRepository sysFileRepository;
    private final RbacService rbacService;
    private final FilePurgeHelper filePurgeHelper;
    private final AppCacheService appCacheService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PageResult<RecycleBinVO> list(int page, int size, String keyword, String bizType) {
        rbacService.checkPermission("menu:system:recycle");
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<SysRecycleBin> result = recycleBinRepository.search(
                StringUtils.hasText(keyword) ? keyword.trim() : "",
                StringUtils.hasText(bizType) ? bizType.trim() : null,
                pageable);
        List<RecycleBinVO> records = result.getContent().stream().map(RecycleBinVO::from).toList();
        return new PageResult<>(records, result.getTotalElements(), page, size);
    }

    /** 用户软删除：写入回收站并打 deletedAt */
    @Transactional
    public void softDeleteUser(User user) {
        if (user.getDeletedAt() != null) {
            return;
        }
        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);
        upsertBin(BIZ_USER, user.getId(),
                StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername(),
                "用户名: " + user.getUsername(),
                snapshotOf(Map.of(
                        "id", user.getId(),
                        "username", user.getUsername(),
                        "nickname", user.getNickname(),
                        "email", user.getEmail(),
                        "phone", user.getPhone()
                )));
        appCacheService.evictPermissionCodes(user.getId());
    }

    /** 文件软删除：不删对象存储，仅隐藏元数据 */
    @Transactional
    public void softDeleteFile(SysFile file) {
        if (file.getDeletedAt() != null) {
            return;
        }
        file.setDeletedAt(LocalDateTime.now());
        sysFileRepository.save(file);
        upsertBin(BIZ_FILE, file.getId(),
                file.getOriginalName(),
                file.getObjectKey(),
                snapshotOf(Map.of(
                        "id", file.getId(),
                        "objectKey", file.getObjectKey(),
                        "originalName", file.getOriginalName(),
                        "storage", file.getStorage(),
                        "sizeBytes", file.getSizeBytes()
                )));
    }

    @Transactional
    public void restore(Long id) {
        rbacService.checkPermission("recycle:restore");
        SysRecycleBin bin = findBin(id);
        if (BIZ_USER.equals(bin.getBizType())) {
            User user = userRepository.findById(bin.getBizId())
                    .orElseThrow(() -> new BusinessException("原用户记录不存在，无法恢复"));
            user.setDeletedAt(null);
            userRepository.save(user);
        } else if (BIZ_FILE.equals(bin.getBizType())) {
            SysFile file = sysFileRepository.findById(bin.getBizId())
                    .orElseThrow(() -> new BusinessException("原文件记录不存在，无法恢复"));
            file.setDeletedAt(null);
            sysFileRepository.save(file);
        } else {
            throw new BusinessException("不支持的回收类型: " + bin.getBizType());
        }
        recycleBinRepository.delete(bin);
    }

    @Transactional
    public void purge(Long id) {
        rbacService.checkPermission("recycle:purge");
        purgeInternal(findBin(id));
    }

    @Transactional
    public int batchPurge(List<Long> ids) {
        rbacService.checkPermission("recycle:purge");
        int count = 0;
        for (Long id : ids) {
            recycleBinRepository.findById(id).ifPresent(bin -> {
                purgeInternal(bin);
            });
            count++;
        }
        return count;
    }

    @Transactional
    public void clean() {
        rbacService.checkPermission("recycle:clean");
        for (SysRecycleBin bin : recycleBinRepository.findAll()) {
            purgeInternal(bin);
        }
    }

    private void purgeInternal(SysRecycleBin bin) {
        try {
            if (BIZ_USER.equals(bin.getBizType())) {
                userRepository.findById(bin.getBizId()).ifPresent(user -> {
                    if (user.getDeletedAt() != null) {
                        userRepository.delete(user);
                        appCacheService.evictPermissionCodes(user.getId());
                    }
                });
            } else if (BIZ_FILE.equals(bin.getBizType())) {
                sysFileRepository.findById(bin.getBizId()).ifPresent(file -> {
                    if (file.getDeletedAt() != null) {
                        try {
                            filePurgeHelper.purgeStorage(file);
                        } catch (Exception ex) {
                            log.warn("彻底删除文件存储失败 {}: {}", file.getObjectKey(), ex.getMessage());
                        }
                        sysFileRepository.delete(file);
                    }
                });
            }
        } finally {
            recycleBinRepository.delete(bin);
        }
    }

    private void upsertBin(String bizType, Long bizId, String title, String summary, String snapshot) {
        SysRecycleBin bin = recycleBinRepository.findByBizTypeAndBizId(bizType, bizId).orElseGet(SysRecycleBin::new);
        bin.setBizType(bizType);
        bin.setBizId(bizId);
        bin.setTitle(title);
        bin.setSummary(summary);
        bin.setSnapshot(snapshot);
        bin.setDeletedBy(RbacService.currentUsername());
        recycleBinRepository.save(bin);
    }

    private SysRecycleBin findBin(Long id) {
        return recycleBinRepository.findById(id)
                .orElseThrow(() -> new BusinessException("回收站记录不存在"));
    }

    private String snapshotOf(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            return null;
        }
    }
}
