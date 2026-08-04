package com.smartadmin.service;

import com.smartadmin.common.BusinessException;
import com.smartadmin.dto.MyNoticeVO;
import com.smartadmin.dto.NoticeReaderVO;
import com.smartadmin.dto.NoticeRequest;
import com.smartadmin.dto.NoticeVO;
import com.smartadmin.dto.PageResult;
import com.smartadmin.entity.NoticeStatus;
import com.smartadmin.entity.SysNotice;
import com.smartadmin.entity.SysNoticeReceiver;
import com.smartadmin.entity.User;
import com.smartadmin.repository.SysNoticeReceiverRepository;
import com.smartadmin.repository.SysNoticeRepository;
import com.smartadmin.repository.UserRepository;
import com.smartadmin.websocket.NoticeSessionHub;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final SysNoticeRepository noticeRepository;
    private final SysNoticeReceiverRepository receiverRepository;
    private final UserRepository userRepository;
    private final RbacService rbacService;
    private final NoticeSessionHub sessionHub;
    private final DataScopeService dataScopeService;

    public PageResult<NoticeVO> list(int page, int size, String keyword, String status) {
        rbacService.checkPermission("notice:view");
        NoticeStatus statusEnum = null;
        if (StringUtils.hasText(status)) {
            try {
                statusEnum = NoticeStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new BusinessException("无效的公告状态");
            }
        }
        DataScopeService.OwnerFilter ownerFilter = dataScopeService.resolveOwnerFilter();
        Page<SysNotice> result = noticeRepository.search(
                StringUtils.hasText(keyword) ? keyword.trim() : "",
                statusEnum,
                ownerFilter.ownerIds(),
                ownerFilter.unrestricted(),
                PageRequest.of(page, size)
        );
        Map<Long, String> publisherNames = loadPublisherNames(result.getContent());
        List<NoticeVO> records = result.getContent().stream()
                .map(n -> toAdminVO(n, publisherNames.get(n.getPublisherId())))
                .toList();
        return new PageResult<>(records, result.getTotalElements(), page, size);
    }

    public NoticeVO getById(Long id) {
        rbacService.checkPermission("notice:view");
        SysNotice notice = findNotice(id);
        dataScopeService.assertOwnerAccessible(notice.getPublisherId());
        String publisherName = null;
        if (notice.getPublisherId() != null) {
            publisherName = userRepository.findById(notice.getPublisherId())
                    .map(u -> StringUtils.hasText(u.getNickname()) ? u.getNickname() : u.getUsername())
                    .orElse(null);
        }
        return toAdminVO(notice, publisherName);
    }

    @Transactional
    public NoticeVO create(NoticeRequest request) {
        rbacService.checkPermission("notice:create");
        SysNotice notice = new SysNotice();
        notice.setTitle(request.getTitle().trim());
        notice.setContent(request.getContent());
        notice.setStatus(NoticeStatus.DRAFT);
        notice.setPublisherId(rbacService.currentUser().getId());
        return toAdminVO(noticeRepository.save(notice), currentPublisherName());
    }

    @Transactional
    public NoticeVO update(Long id, NoticeRequest request) {
        rbacService.checkPermission("notice:update");
        SysNotice notice = findNotice(id);
        dataScopeService.assertOwnerAccessible(notice.getPublisherId());
        if (notice.getStatus() != NoticeStatus.DRAFT) {
            throw new BusinessException("仅草稿可编辑");
        }
        notice.setTitle(request.getTitle().trim());
        notice.setContent(request.getContent());
        return toAdminVO(noticeRepository.save(notice), currentPublisherName());
    }

    @Transactional
    public void delete(Long id) {
        rbacService.checkPermission("notice:delete");
        deleteInternal(id);
    }

    @Transactional
    public int batchDelete(List<Long> ids) {
        rbacService.checkPermission("notice:delete");
        int count = 0;
        for (Long id : ids) {
            deleteInternal(id);
            count++;
        }
        return count;
    }

    private void deleteInternal(Long id) {
        SysNotice notice = findNotice(id);
        dataScopeService.assertOwnerAccessible(notice.getPublisherId());
        if (notice.getStatus() != NoticeStatus.DRAFT) {
            throw new BusinessException("仅草稿可删除：" + notice.getTitle());
        }
        receiverRepository.deleteByNoticeId(id);
        noticeRepository.delete(notice);
    }

    @Transactional
    public NoticeVO publish(Long id) {
        rbacService.checkPermission("notice:publish");
        return publishInternal(id);
    }

    @Transactional
    public int batchPublish(List<Long> ids) {
        rbacService.checkPermission("notice:publish");
        int count = 0;
        for (Long id : ids) {
            publishInternal(id);
            count++;
        }
        return count;
    }

    private NoticeVO publishInternal(Long id) {
        SysNotice notice = findNotice(id);
        dataScopeService.assertOwnerAccessible(notice.getPublisherId());
        if (notice.getStatus() != NoticeStatus.DRAFT && notice.getStatus() != NoticeStatus.REVOKED) {
            throw new BusinessException("当前状态不可下发：" + notice.getTitle());
        }
        // 重新下发前清理旧接收记录
        receiverRepository.deleteByNoticeId(id);

        List<User> targets = dataScopeService.listAccessibleActiveUsers();
        if (targets.isEmpty()) {
            throw new BusinessException("当前数据权限范围内无可用接收用户");
        }
        LocalDateTime now = LocalDateTime.now();
        notice.setStatus(NoticeStatus.PUBLISHED);
        notice.setPublisherId(rbacService.currentUser().getId());
        notice.setPublishedAt(now);
        notice.setRevokedAt(null);
        noticeRepository.save(notice);

        List<SysNoticeReceiver> receivers = targets.stream().map(user -> {
            SysNoticeReceiver receiver = new SysNoticeReceiver();
            receiver.setNoticeId(notice.getId());
            receiver.setUserId(user.getId());
            return receiver;
        }).toList();
        if (!receivers.isEmpty()) {
            receiverRepository.saveAll(receivers);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "notice:publish");
        payload.put("id", notice.getId());
        payload.put("title", notice.getTitle());
        payload.put("publishedAt", notice.getPublishedAt() != null ? notice.getPublishedAt().toString() : null);
        for (User user : targets) {
            sessionHub.sendToUser(user.getId(), payload);
        }

        return toAdminVO(notice, currentPublisherName());
    }

    @Transactional
    public NoticeVO revoke(Long id) {
        rbacService.checkPermission("notice:revoke");
        return revokeInternal(id);
    }

    @Transactional
    public int batchRevoke(List<Long> ids) {
        rbacService.checkPermission("notice:revoke");
        int count = 0;
        for (Long id : ids) {
            revokeInternal(id);
            count++;
        }
        return count;
    }

    private NoticeVO revokeInternal(Long id) {
        SysNotice notice = findNotice(id);
        dataScopeService.assertOwnerAccessible(notice.getPublisherId());
        if (notice.getStatus() != NoticeStatus.PUBLISHED) {
            throw new BusinessException("仅已下发公告可撤回：" + notice.getTitle());
        }
        notice.setStatus(NoticeStatus.REVOKED);
        notice.setRevokedAt(LocalDateTime.now());
        noticeRepository.save(notice);

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "notice:revoke");
        payload.put("id", notice.getId());
        sessionHub.broadcast(payload);

        return toAdminVO(notice, currentPublisherName());
    }

    public List<NoticeReaderVO> readers(Long id) {
        rbacService.checkPermission("notice:view");
        SysNotice notice = findNotice(id);
        dataScopeService.assertOwnerAccessible(notice.getPublisherId());
        List<SysNoticeReceiver> readers = receiverRepository.findReaders(id);
        Map<Long, User> users = userRepository.findAllById(
                readers.stream().map(SysNoticeReceiver::getUserId).toList()
        ).stream().collect(Collectors.toMap(User::getId, Function.identity()));

        return readers.stream().map(r -> {
            NoticeReaderVO vo = new NoticeReaderVO();
            vo.setUserId(r.getUserId());
            vo.setReadAt(r.getReadAt());
            User user = users.get(r.getUserId());
            if (user != null) {
                vo.setUsername(user.getUsername());
                vo.setNickname(user.getNickname());
            }
            return vo;
        }).toList();
    }

    public List<MyNoticeVO> myList() {
        User user = rbacService.currentUser();
        List<SysNoticeReceiver> receivers = receiverRepository.findPublishedByUserId(user.getId());
        if (receivers.isEmpty()) {
            return List.of();
        }
        Map<Long, SysNotice> notices = noticeRepository.findAllById(
                receivers.stream().map(SysNoticeReceiver::getNoticeId).toList()
        ).stream().collect(Collectors.toMap(SysNotice::getId, Function.identity()));

        Map<Long, String> publisherNames = loadPublisherNames(
                notices.values().stream().toList()
        );

        return receivers.stream()
                .map(r -> {
                    SysNotice notice = notices.get(r.getNoticeId());
                    if (notice == null || notice.getStatus() != NoticeStatus.PUBLISHED) {
                        return null;
                    }
                    String publisherName = publisherNames.get(notice.getPublisherId());
                    return MyNoticeVO.from(
                            notice,
                            r.getReadAt() != null,
                            r.getReadAt(),
                            r.getCreatedAt(),
                            publisherName
                    );
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public void markRead(Long id) {
        User user = rbacService.currentUser();
        SysNotice notice = findNotice(id);
        if (notice.getStatus() != NoticeStatus.PUBLISHED) {
            throw new BusinessException("公告不可读或已撤回");
        }
        SysNoticeReceiver receiver = receiverRepository.findByNoticeIdAndUserId(id, user.getId())
                .orElseThrow(() -> new BusinessException("未收到该公告"));
        if (receiver.getReadAt() == null) {
            receiver.setReadAt(LocalDateTime.now());
            receiverRepository.save(receiver);
        }
    }

    private SysNotice findNotice(Long id) {
        return noticeRepository.findById(id)
                .orElseThrow(() -> new BusinessException("公告不存在"));
    }

    private NoticeVO toAdminVO(SysNotice notice, String publisherName) {
        NoticeVO vo = NoticeVO.from(notice);
        vo.setPublisherName(publisherName);
        if (notice.getStatus() == NoticeStatus.PUBLISHED || notice.getStatus() == NoticeStatus.REVOKED) {
            vo.setTotalCount(receiverRepository.countByNoticeId(notice.getId()));
            vo.setReadCount(receiverRepository.countByNoticeIdAndReadAtIsNotNull(notice.getId()));
        }
        return vo;
    }

    private Map<Long, String> loadPublisherNames(List<SysNotice> notices) {
        List<Long> ids = notices.stream()
                .map(SysNotice::getPublisherId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(
                        User::getId,
                        u -> StringUtils.hasText(u.getNickname()) ? u.getNickname() : u.getUsername()
                ));
    }

    private String currentPublisherName() {
        User user = rbacService.currentUser();
        return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }
}
